package com.navigator.app.nav.providers

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import com.google.android.libraries.navigation.NotificationContentProviderBase
import com.navigator.app.R
import com.navigator.app.logging.AppLogger
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * The guidance foreground-service notification, replacing the Nav SDK's generic "Navigating" one:
 * current instruction, distance to the turn, remaining distance and ETA, and a whole-route
 * progress bar. On Android 16 QPR2+ it is requested as a promoted ongoing notification (a Live
 * Update: status-bar chip with the distance to the next turn, lock-screen card); everywhere else
 * it is a normal ongoing notification with the same content. In-app engine only - in mirror mode
 * Google Maps owns its own notification.
 */
class NavLiveUpdate(
    private val app: Application,
    private val resumeIntent: Intent,
) : NotificationContentProviderBase(app) {

    @Volatile private var totalM = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings by lazy { AppSettings(app) }

    init {
        ensureChannel()
        scope.launch {
            // ~1 Hz is plenty for a notification, and keeps the system from rate-limiting updates.
            GoogleNavSdkProvider.state.sample(1_000).collect { state ->
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
        scope.cancel()
    }

    private val tapIntent: PendingIntent by lazy {
        PendingIntent.getActivity(app, 0, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ensureChannel() {
        runCatching {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Navigation guidance", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Turn and ETA while you navigate"
                    setShowBadge(false)
                },
            )
        }
    }

    // The SDK calls this inside Service.startForeground: it must never throw.
    override fun getNotification(): Notification = try {
        build()
    } catch (e: Throwable) {
        AppLogger.log("Nav", "!! live notification build failed (${e.javaClass.simpleName}); using a minimal one")
        Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Navigating")
            .setOngoing(true)
            .build()
    }

    private fun build(): Notification {
        ensureChannel() // idempotent; covers a channel the rider deleted
        val state = GoogleNavSdkProvider.state.value
        val content = NavLiveContentBuilder.build(state, System.currentTimeMillis())
        val tap = tapIntent
        val b = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        NavLiveContentBuilder.progress(totalM, state.remainingDistanceMeters)?.let { (done, total) ->
            runCatching {
                b.setStyle(
                    Notification.ProgressStyle()
                        .setStyledByProgress(false)
                        .addProgressSegment(Notification.ProgressStyle.Segment(total))
                        .setProgress(done),
                )
            }
        }
        if (liveUpdatesEnabled()) {
            // Both calls are newer than API 36.0: a NoSuchMethodError on older builds just
            // leaves the regular ongoing notification.
            content.chip?.let { chip -> runCatching { b.setShortCriticalText(chip) } }
            runCatching { b.setRequestPromotedOngoing(true) }
        }
        return b.build()
    }

    private fun liveUpdatesEnabled(): Boolean = runCatching { settings.liveUpdatesEnabled }.getOrDefault(true)

    companion object {
        private const val CHANNEL_ID = "nav_guidance"
        /** Same id the SDK foreground service is started with (GoogleNavSdkController). */
        const val NOTIFICATION_ID = 1010
    }
}
