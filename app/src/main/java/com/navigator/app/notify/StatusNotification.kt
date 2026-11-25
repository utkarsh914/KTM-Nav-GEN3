package com.navigator.app.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import com.navigator.app.R
import com.navigator.app.app.AppContainer
import com.navigator.app.app.AppIntents
import com.navigator.app.app.appContainer
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.location.SpeedMonitor
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.NavLiveContentBuilder
import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.ride.RideRecordingService
import com.navigator.app.ride.RideSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * The app's one ongoing notification (D34). The bike link, the ride recording and the Navigation SDK's service all
 * start foreground with [build] on the same id, so Android keeps one notification until the last of them leaves; a
 * single refresher ([startRefresher]) keeps it current. What it says is decided by [AppStatusBuilder].
 */
object StatusNotification {
    /** The SDK's own foreground id, kept so its service shares this notification. */
    const val ID = 1010

    private val lock = Any()
    private var lastPosted: AppStatusContent? = null
    private var trackedTotalM = 0

    /** The notification for a service's `startForeground`: never throws. */
    fun build(context: Context): Notification = try {
        val app = context.applicationContext
        val content = content(app)
        render(app, content).also { synchronized(lock) { lastPosted = content } }
    } catch (e: Throwable) {
        synchronized(lock) { lastPosted = null }
        AppLogger.log("Notify", "!! status notification build failed (${e.javaClass.simpleName}); using a minimal one")
        NotificationChannels.ensure(context, NotificationChannels.STATUS)
        Notification.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.status_ready_title))
            .setOngoing(true)
            .build()
    }

    /** Re-post when the content changed and a foreground service owns the notification. Main thread. */
    fun refresh(context: Context) {
        runCatching {
            val app = context.applicationContext
            val nm = app.getSystemService(NotificationManager::class.java)
            if (!foregroundShown(nm)) return
            val content = content(app)
            if (!shouldPost(synchronized(lock) { lastPosted }, content)) return
            synchronized(lock) { lastPosted = content }
            nm.notify(ID, render(app, content))
        }.onFailure { AppLogger.log("Notify", "!! status refresh failed: ${it.javaClass.simpleName}") }
    }

    /** A re-post only for a change: an identical notification is churn, and the system rate-limits updates. */
    internal fun shouldPost(last: AppStatusContent?, now: AppStatusContent): Boolean = last != now

    private fun foregroundShown(nm: NotificationManager): Boolean = nm.activeNotifications.any {
        it.id == ID && (it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
    }

    private fun content(app: Context): AppStatusContent {
        val container = app.appContainer
        val settings = container.settings.legacy
        val nav = GoogleNavSdkProvider.state.value
        val phase = container.trip.phase.value
        val marks = GoogleNavSdkProvider.routeMarks.value
        val paired = settings.bondedDeviceAddress != null
        val mirrorNav = NotificationNavProvider.state.value
        val mirrorActive = container.settings.engineInUse() == NavEngine.MIRROR && mirrorNav.sessionState.isActiveNav()
        val total = synchronized(lock) { NavLiveContentBuilder.trackTotal(trackedTotalM, nav).also { trackedTotalM = it } }
        return AppStatusBuilder.build(
            AppStatusInput(
                phase = phase,
                trip = LiveInput(
                    nav = nav, nowMs = System.currentTimeMillis(),
                    destination = (container.trip.state.value.dest ?: GoogleNavSdkProvider.currentDestination.value)?.label,
                    recording = RideSession.state.value,
                    dashConnected = if (paired) BccuConnectionService.connectionState.value == BccuConnectionService.ConnectionState.AUTHENTICATED else null,
                    overspeedKmh = SpeedMonitor.overspeedKmh.value,
                    overspeedLimitKmh = runCatching { settings.overspeedLimitKmh }.getOrNull(),
                    beepsOn = runCatching { settings.turnBeepEnabled }.getOrDefault(true),
                    stopMarks = marks.stops, routeTotalM = marks.total,
                ),
                connection = BccuConnectionService.notice.value,
                bikeName = settings.bondedDeviceName,
                mirrorActive = mirrorActive,
                mirrorToTurn = mirrorNav.preformattedDistance?.takeIf { mirrorNav.sessionState == NavSessionState.ENROUTE },
                trackedTotalM = total,
                liveUpdates = runCatching { settings.liveUpdatesEnabled }.getOrDefault(true),
                quitNeedsConfirm = container.quit.needsConfirm(),
            ),
            app.resources,
        )
    }

    private fun render(app: Context, c: AppStatusContent): Notification {
        NotificationChannels.ensure(app, NotificationChannels.STATUS)
        val b = Notification.Builder(app, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.title)
            .setContentIntent(NotificationActions.open(app, NotificationRequestCodes.OPEN_APP))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC) // low-sensitivity text; hiding it would also hide the buttons on the lock screen
        c.text?.let { b.setContentText(it) }
        c.subText?.let { b.setSubText(it) }
        if (c.kind == StatusKind.TRIP) b.setCategory(Notification.CATEGORY_NAVIGATION)
        val large = if (c.overspeedKmh != null) LargeIcons.speed(c.overspeedKmh) else c.turn?.let { LargeIcons.turn(app, it) }
        large?.let { b.setLargeIcon(it) }
        c.actions.forEach { action(app, action = it, quitNeedsConfirm = c.quitNeedsConfirm)?.let(b::addAction) }
        if (c.chronometerBaseMs != null) b.setUsesChronometer(true).setShowWhen(true).setWhen(c.chronometerBaseMs)
        else b.setShowWhen(false)
        c.progress?.let { (done, all) ->
            runCatching {
                val style = Notification.ProgressStyle()
                    .setStyledByProgress(false)
                    .addProgressSegment(Notification.ProgressStyle.Segment(all))
                    .setProgress(done)
                    .setProgressEndIcon(Icon.createWithResource(app, R.drawable.ic_notif_flag))
                c.stopPoints.forEach { style.addProgressPoint(Notification.ProgressStyle.Point(it)) }
                b.setStyle(style)
            }
        }
        if (c.promoted) PromotedOngoing.apply(b, c.chip)
        return b.build()
    }

    private fun action(app: Context, action: StatusAction, quitNeedsConfirm: Boolean): Notification.Action? {
        val (label, intent) = when (action) {
            StatusAction.OPEN -> R.string.live_action_open to NotificationActions.open(app, NotificationRequestCodes.OPEN_APP)
            StatusAction.MUTE_BEEPS -> R.string.live_action_mute to NotificationActions.toggleBeeps(app)
            StatusAction.UNMUTE_BEEPS -> R.string.live_action_unmute to NotificationActions.toggleBeeps(app)
            // Ending needs a confirm, so the app opens on it.
            StatusAction.END_TRIP -> R.string.live_action_end to NotificationActions.open(app, NotificationRequestCodes.LIVE_END_TRIP, AppIntents.REQUEST_END_TRIP)
            StatusAction.PAUSE -> R.string.rec_action_pause to RideRecordingService.pauseIntent(app)
            StatusAction.RESUME -> R.string.rec_action_resume to RideRecordingService.resumeIntent(app)
            StatusAction.STOP_RECORDING -> R.string.rec_action_stop to RideRecordingService.stopIntent(app)
            StatusAction.TURN_ON -> R.string.conn_action_turn_on to NotificationActions.turnOnBluetooth(app)
            StatusAction.REPAIR -> R.string.conn_action_repair to NotificationActions.open(app, NotificationRequestCodes.CONNECTION_REPAIR, AppIntents.REQUEST_REPAIR)
            // Android 12+ forbids starting an activity from a receiver a notification launched: with nothing to confirm the
            // button quits in place, otherwise it opens the app on the confirm.
            StatusAction.QUIT -> R.string.conn_action_quit to
                if (quitNeedsConfirm) NotificationActions.open(app, NotificationRequestCodes.STATUS_QUIT_CONFIRM, AppIntents.REQUEST_QUIT)
                else NotificationActions.quitNow(app)
        }
        return Notification.Action.Builder(Icon.createWithResource(app, R.drawable.ic_notification), app.getString(label), intent).build()
    }

    /** One refresher for everything the notification shows; the ticks it samples away are the per-second churn. */
    @OptIn(FlowPreview::class)
    fun startRefresher(app: Context, container: AppContainer, scope: CoroutineScope) {
        scope.launch(Dispatchers.Main.immediate) {
            combine(
                container.trip.phase, GoogleNavSdkProvider.state, RideSession.state, SpeedMonitor.overspeedKmh,
                BccuConnectionService.notice, BccuConnectionService.connectionState, NotificationNavProvider.state,
                container.settings.snapshot, GoogleNavSdkProvider.routeMarks, container.trip.state, GoogleNavSdkProvider.currentDestination,
            ) { }.sample(1_000).collect { refresh(app) }
        }
    }
}
