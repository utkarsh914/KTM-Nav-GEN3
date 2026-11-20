package com.navigator.app.nav.providers

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import com.google.android.libraries.navigation.NotificationContentProviderBase
import com.navigator.app.R
import com.navigator.app.app.AppIntents
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.location.SpeedMonitor
import com.navigator.app.logging.AppLogger
import com.navigator.app.notify.LargeIcons
import com.navigator.app.notify.LiveAction
import com.navigator.app.notify.LiveInput
import com.navigator.app.notify.LiveUpdateBuilder
import com.navigator.app.notify.NotificationActions
import com.navigator.app.notify.NotificationChannels
import com.navigator.app.notify.PromotedOngoing
import com.navigator.app.ride.RideSession
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * The guidance foreground-service notification, replacing the Nav SDK's generic "Navigating" one (SY1 to SY3): the
 * dash glyph as the large icon, the turn, destination · ETA · remaining, a "● Rec · Dash connected" line, a progress
 * bar with a point per stop and a flag at the end, and the Open / Mute beeps / End trip buttons. Overspeed is a state
 * of this same notification, and the recording that runs during the trip is a line in it. On Android 16 QPR2+ it is
 * requested as a promoted ongoing notification (a Live Update); everywhere else it is a normal ongoing one with the
 * same content. In-app engine only: in mirror mode Google Maps owns its own notification.
 */
class NavLiveUpdate(
    private val app: Application,
    private val resumeIntent: Intent,
) : NotificationContentProviderBase(app) {

    @Volatile private var totalM = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings by lazy { AppSettings(app) }

    init {
        current = this
        NotificationChannels.ensure(app, NotificationChannels.GUIDANCE)
        scope.launch {
            // ~1 Hz is plenty for a notification, and keeps the system from rate-limiting updates. Overspeed and the
            // recording are folded in here so they never become notifications of their own while a trip runs.
            combine(
                GoogleNavSdkProvider.state, RideSession.state, SpeedMonitor.overspeedKmh, BccuConnectionService.connectionState,
            ) { state, _, _, _ -> state }.sample(1_000).collect { state ->
                totalM = NavLiveContentBuilder.trackTotal(totalM, state)
                // updateNotification() is a plain notify(): only refresh while guidance is live AND
                // the SDK's foreground service still owns the notification, or a late tick would
                // resurrect a stale, undismissable "Navigating" card after the trip ended.
                if (NavLiveContentBuilder.shouldRefresh(state) && foregroundNotificationShown()) {
                    runCatching { updateNotification() }
                }
            }
        }
    }

    private fun foregroundNotificationShown(): Boolean = runCatching {
        app.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.id == NOTIFICATION_ID && (it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        }
    }.getOrDefault(false)

    /** Stop refreshing (provider abandoned, e.g. after falling back to the SDK default). */
    fun close() {
        if (current === this) current = null
        scope.cancel()
    }

    private val tapIntent: PendingIntent by lazy {
        PendingIntent.getActivity(app, 0, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // The SDK calls this inside Service.startForeground: it must never throw.
    override fun getNotification(): Notification = try {
        build()
    } catch (e: Throwable) {
        AppLogger.log("Nav", "!! live notification build failed (${e.javaClass.simpleName}); using a minimal one")
        Notification.Builder(app, NotificationChannels.GUIDANCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.getString(R.string.live_navigating))
            .setOngoing(true)
            .build()
    }

    private fun build(): Notification {
        NotificationChannels.ensure(app, NotificationChannels.GUIDANCE) // idempotent; covers a channel the rider deleted
        val state = GoogleNavSdkProvider.state.value
        val marks = GoogleNavSdkProvider.routeMarks.value
        val paired = settings.bondedDeviceAddress != null
        val content = LiveUpdateBuilder.build(
            LiveInput(
                nav = state, nowMs = System.currentTimeMillis(),
                destination = GoogleNavSdkProvider.currentDestination.value?.label,
                recording = RideSession.state.value,
                dashConnected = if (paired) BccuConnectionService.connectionState.value == BccuConnectionService.ConnectionState.AUTHENTICATED else null,
                overspeedKmh = SpeedMonitor.overspeedKmh.value,
                beepsOn = runCatching { settings.turnBeepEnabled }.getOrDefault(true),
                stopMarks = marks.stops, routeTotalM = marks.total,
            ),
            app.resources,
        )
        val b = Notification.Builder(app, NotificationChannels.GUIDANCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        content.subText?.let { b.setSubText(it) }
        val large = if (content.overspeed) SpeedMonitor.overspeedKmh.value?.let(LargeIcons::speed)
        else content.turn?.let { LargeIcons.turn(app, it) }
        large?.let { b.setLargeIcon(it) }
        content.actions.forEach { action -> actionFor(action)?.let { b.addAction(it) } }

        // The marks are measured once, at the start: after a reroute that adds distance they no longer fit.
        val marksFit = marks.total > 0 && (state.remainingDistanceMeters ?: 0) <= marks.total
        val total = if (marksFit) marks.total else totalM
        NavLiveContentBuilder.progress(total, state.remainingDistanceMeters)?.let { (done, all) ->
            runCatching {
                val style = Notification.ProgressStyle()
                    .setStyledByProgress(false)
                    .addProgressSegment(Notification.ProgressStyle.Segment(all))
                    .setProgress(done)
                    .setProgressEndIcon(Icon.createWithResource(app, R.drawable.ic_notif_flag))
                (if (marksFit) marks.stops else emptyList()).filter { it in 1 until all }.forEach { style.addProgressPoint(Notification.ProgressStyle.Point(it)) }
                b.setStyle(style)
            }
        }
        if (liveUpdatesEnabled()) PromotedOngoing.apply(b, content.chip)
        return b.build()
    }

    private fun actionFor(action: LiveAction): Notification.Action? {
        val (label, intent) = when (action) {
            LiveAction.OPEN -> R.string.live_action_open to tapIntent
            LiveAction.MUTE_BEEPS -> R.string.live_action_mute to NotificationActions.toggleBeeps(app)
            LiveAction.UNMUTE_BEEPS -> R.string.live_action_unmute to NotificationActions.toggleBeeps(app)
            // Ending needs a confirm, so the app opens on it.
            LiveAction.END_TRIP -> R.string.live_action_end to NotificationActions.open(app, CODE_END, AppIntents.REQUEST_END_TRIP)
        }
        return Notification.Action.Builder(Icon.createWithResource(app, R.drawable.ic_notification), app.getString(label), intent).build()
    }

    private fun liveUpdatesEnabled(): Boolean = runCatching { settings.liveUpdatesEnabled }.getOrDefault(true)

    companion object {
        /** Same id the SDK foreground service is started with (GoogleNavSdkController). */
        const val NOTIFICATION_ID = 1010
        private const val CODE_END = 72

        @Volatile private var current: NavLiveUpdate? = null

        /** A button or setting changed what the notification says: redraw it now rather than at the next tick. */
        fun refreshNow() {
            val live = current ?: return
            val state = GoogleNavSdkProvider.state.value
            if (NavLiveContentBuilder.shouldRefresh(state) && live.foregroundNotificationShown()) runCatching { live.updateNotification() }
        }
    }
}
