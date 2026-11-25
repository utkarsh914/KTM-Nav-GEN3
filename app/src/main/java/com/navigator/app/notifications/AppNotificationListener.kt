package com.navigator.app.notifications

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.navigator.app.BuildConfig
import com.navigator.app.audio.BeepSourcePolicy
import com.navigator.app.audio.TurnBeeper
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.ble.BccuProtocol
import com.navigator.app.location.SpeedMonitor
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.settings.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Captures notifications from user-selected apps (WhatsApp/SMS/etc, see
 * AppSettings.notificationSourceApps) and mirrors them to the dash's
 * NOTIFICATION characteristic (the small bottom banner), plus separately
 * detects "which app is currently navigating" and mirrors that to the
 * dash's center guidance view (TURN_DISTANCE + TURN_ROAD - a different
 * display region entirely, confirmed from the official app's NavableBleImpl,
 * see BCCU_BLE_PROTOCOL.md) rather than the bottom banner.
 *
 * Scope note: turn-direction (left/right/straight) is derived via
 * TurnIconHeuristic - an explicitly unreliable image heuristic, not a
 * confirmed protocol field like everything else here. Included by request
 * despite that, since there's no text-based signal for turn direction in a
 * generic maps app's notification.
 */
class AppNotificationListener : NotificationListenerService() {

    /**
     * Confirmed via a full hardware calibration pass (BCCU_BLE_PROTOCOL.md
     * section 7.2, 2026-07-05, "Symbol testing" screen): of all 8
     * NotificationIcon codes, only NOTIFICATION_REROUTING and
     * NOTIFICATION_WAYPOINT actually render on this Gen-3 dash's bottom
     * banner. Using WAYPOINT as the settled default for all app-generated
     * notifications.
     */
    private val dashIcon = BccuProtocol.NotificationIcon.NOTIFICATION_WAYPOINT

    // Built once; the getters read prefs live, so runtime setting changes are still reflected.
    private val settings by lazy { AppSettings(this) }

    // Rendering the maneuver icon, hashing, TFLite and disk writes are far too heavy for
    // the main thread (this callback is delivered there). One worker keeps strict ordering,
    // and removals go through the SAME queue so a removal can never overtake the post it follows.
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "notif-worker").apply { isDaemon = true }
    }

    private fun runOnWorker(what: String, block: () -> Unit) {
        try {
            worker.execute {
                try { block() } catch (e: Exception) {
                    AppLogger.log("Notif", "!! $what failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        } catch (e: RejectedExecutionException) {
            // Service already destroyed.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runOnWorker("onNotificationPosted") { handlePosted(sbn) }
    }

    override fun onListenerDisconnected() {
        NotificationRepository.setListenerConnected(false)
        NotificationRepository.setMapsGuidancePresent(false) // unbound: the resync on reconnect says what is showing
        super.onListenerDisconnected()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationRepository.setListenerConnected(true)
        // (Re)bound: pick up a navigation notification that is already showing, instead of
        // waiting for its next update (and track its key for the removal logic).
        runOnWorker("onListenerConnected sync") {
            // Maps may have ended while the listener was unbound: only what is showing now counts.
            NotificationRepository.setMapsGuidancePresent(false)
            activeNotifications?.forEach { sbn ->
                if (isNavigationNotification(sbn, settings)) handlePosted(sbn)
            }
        }
    }

    override fun onDestroy() {
        NotificationRepository.setListenerConnected(false)
        worker.shutdown()
        super.onDestroy()
    }

    // Last text mirrored per notification key, so repeated updates of the same message
    // (progress, re-posts) aren't sent to the dash again.
    private val lastMessageText = ConcurrentHashMap<String, String>()

    private fun handlePosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        // Debug only: logs the package + category of EVERY app's notification, which
        // would otherwise be mirrored to the public Downloads log in release.
        if (BuildConfig.DEBUG) {
            AppLogger.log("Notif", "Posted: pkg=$packageName category=${sbn.notification.category} flags=0x${Integer.toHexString(sbn.notification.flags)} title=${redact(title)}")
        }
        if (title.isBlank() && text.isBlank()) return

        val isNav = isNavigationNotification(sbn, settings)
        if (isNav) navKeys.add(sbn.key)
        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        if (isNav) {
            // Keep the two navigation engines mutually exclusive: when the in-app
            // Google Nav SDK is the selected engine, it owns the dash - don't also
            // mirror another nav app's turn notifications to it.
            if (GoogleNavSdkController.isActive(this, settings) ||
                GoogleNavSdkProvider.isTripInProgress
            ) return
            // Before anything that can skip the update (pause, beeps): the trip exists whatever the dash is doing.
            NotificationRepository.setMapsGuidancePresent(true)
            val navText = if (title.isNotBlank()) "$title $text" else text
            // The title is only the dash's distance field when it looks like a distance.
            val distanceTitle = NavSubtextParser.dashDistanceLabel(title)
            AppLogger.log("Notif", "Nav update from $packageName: distance=${redact(title)} road=${redact(text)}")
            // Dump every extras key so we can see, from real logs, exactly which
            // field carries ETA/duration/remaining-distance - no guessing which
            // Android notification extra Google Maps uses for that subtitle line.
            // DEBUG-only: extra *values* carry the destination road/address
            // (location PII) and AppLogger mirrors every line to the public
            // Downloads copy, so raw values must never be emitted in a release.
            if (BuildConfig.DEBUG) {
                for (key in extras.keySet()) {
                    // Bundle.get(String) is deprecated (no typed replacement exists for a
                    // dump of arbitrary-type extras); fine for this debug-only diagnostic.
                    @Suppress("DEPRECATION")
                    val value = extras.get(key)
                    AppLogger.log("Notif", "  extra[$key] = $value")
                }
            }
            NotificationRepository.updateNavText(packageName, navText)
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
            val (etaText, remainingDistanceText) = parseEtaAndRemainingDistance(subText)
            captureManeuverIconIfEnabled(sbn, title, text)
            val guessedIcon = TurnIconHeuristic.guessDirection(this, sbn, settings.resolvedDrivingSide())
            // Feed the navigation pipeline instead of calling BLE directly:
            // provider -> encoder -> coordinator applies the dedup/throttle/
            // state-machine before it reaches the dash. title/text line up with
            // the official app's field split: title="110 m" (TURN_DISTANCE),
            // text="towards 1st Cross Rd" (TURN_ROAD).
            NotificationNavProvider.pushGuidance(
                distanceText = distanceTitle,
                roadText = text.ifBlank { null },
                etaText = etaText,
                remainingText = remainingDistanceText,
                icon = guessedIcon,
            )
            // Stereo approach beeps: left ear = left turn, right ear = right turn,
            // faster pattern as the distance (the notification title) shrinks.
            // Ducked to a background hum while the bike is provably waiting
            // (GPS says stationary).
            // One source at a time: while the app's own guidance runs, its adapter drives the beeper.
            if (settings.turnBeepEnabled && !NotificationNavProvider.paused.value &&
                BeepSourcePolicy.listenerMayBeep(
                    GoogleNavSdkProvider.state.value.sessionState.isActiveNav(),
                )
            ) {
                TurnBeeper.swapChannels = settings.swapBeepChannels
                TurnBeeper.volumePercent = settings.beepVolumePercent
                TurnBeeper.gpsSpeedKmh = SpeedMonitor.speedKmh.value
                TurnBeeper.onGuidance(distanceTitle, guessedIcon)
            }
            NotificationRepository.updateNavGuidance(
                NotificationRepository.NavGuidance(
                    distance = title.ifBlank { null },
                    road = text.ifBlank { null },
                    eta = etaText,
                    remaining = remainingDistanceText,
                    maneuver = ManeuverText.forIcon(guessedIcon),
                    turnIcon = guessedIcon,
                )
            )
            return
        }

        if (packageName !in settings.notificationSourceApps) return
        // Never mirror our own notifications, nor ongoing / foreground-service ones (media
        // players, downloads, call timers): those are status, not messages.
        if (packageName == this.packageName) return
        if (sbn.notification.flags and
            (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0
        ) return

        // Only INDIVIDUAL conversations reach the dash: skip group chats
        // (WhatsApp groups, group MMS) and the per-app summary notifications
        // that just say "5 new messages".
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            AppLogger.log("Notif", "Skipping group-summary notification from $packageName")
            return
        }
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) {
            AppLogger.log("Notif", "Skipping GROUP conversation from $packageName (title ${redact(title)})")
            return
        }
        // WhatsApp fallback for older notification styles: group messages title
        // as "Group name: Sender" or carry "@ Group" in the conversation title.
        if (packageName == "com.whatsapp" &&
            (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null &&
                extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, true))
        ) {
            AppLogger.log("Notif", "Skipping WhatsApp group (conversation title present, title ${redact(title)})")
            return
        }

        val combined = if (title.isNotBlank()) "$title: $text" else text
        // Same text AND same timestamp = the same message re-posted (a genuinely new message,
        // even with identical text, carries a new `when`).
        val signature = "$combined|${sbn.notification.`when`}"
        if (lastMessageText.put(sbn.key, signature) == signature) return
        if (lastMessageText.size > 200) lastMessageText.clear()
        AppLogger.log("Notif", "Captured from $packageName ($appLabel): ${redact(combined)}")
        // Quick-mute: skip pushing non-navigation notifications to the dash while
        // mirroring is off.
        if (settings.mirrorEnabled) {
            BccuConnectionService.sendNotificationIfRunning(combined, dashIcon)
        }
    }

    /**
     * Best-effort extraction of ETA and remaining distance from a nav app's
     * notification subtitle line, e.g. "22 min - 9.7 km - 2:12 am ETA" (the
     * "Maps - ... - Now" wrapper around it is added by Android's system UI,
     * not part of the app's own extras). This is standard Android notification
     * API usage (EXTRA_SUB_TEXT), not reverse-engineered BLE protocol - but the
     * exact subtitle format is still only as reliable as what real logs show,
     * so this returns null for either field rather than guess if the segment
     * doesn't look like a time or a distance.
     *
     * ETA is reformatted to plain zero-padded "HH:MM" with no am/pm suffix -
     * the official app's own confirmed example (Eta.java: "12:45") has no
     * am/pm text, and a real-hardware test sending "2:12 am" wrote successfully
     * over BLE but never rendered, while the original working screenshot for
     * this same dash slot showed a bare "00:00" - strongly suggesting the
     * firmware expects a strict numeric time, not extra letters.
     */
    private fun parseEtaAndRemainingDistance(subText: String): Pair<String?, String?> =
        NavSubtextParser.parseEtaAndRemaining(subText)

    /**
     * When the navigation app's own notification goes away (rider exited Maps,
     * arrived, or stopped guidance), tell the dash to clear its center guidance
     * view. Without this the last turn stayed frozen on the dash. The service
     * debounces the actual clear, so Maps' routine remove+repost during a route
     * doesn't cause a flicker.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        runOnWorker("onNotificationRemoved") { handleRemoved(sbn) }
    }

    private fun handleRemoved(sbn: StatusBarNotification) {
        val trackedNav = navKeys.remove(sbn.key)
        if (trackedNav || isNavigationNotification(sbn, settings)) {
            AppLogger.log("Notif", "Nav notification removed from ${sbn.packageName} - scheduling guidance clear")
            // TurnBeeper.reset() deliberately NOT called here: Maps routinely
            // removes+reposts its notification mid-route, and resetting the beep
            // dedup on every raw removal made the same turn beep again on each
            // repost. The provider debounces the removal and only emits a real
            // end (-> encoder clear) when navigation has genuinely stopped.
            // The phone's mirror home clears together with the dash (when the debounced end
            // fires), not on every transient remove+repost; if no end is pending (paused /
            // not navigating) there is nothing to wait for, so clear now.
            NotificationRepository.setMapsGuidancePresent(false)
            if (!NotificationNavProvider.pushRemoved()) NotificationRepository.clearNav()
        }
    }

    /**
     * Debug maneuver-icon harvester, OFF unless a marker file
     * `files/CAPTURE_MANEUVERS` exists (create it with adb during an emulator
     * session; it never exists for real users). Saves each distinct maneuver
     * largeIcon to `files/maps_capture/<label>__<hash>.png`, where <label> is the
     * nav app's own instruction text - giving a ground-truth-labeled bitmap set
     * to validate the text parser and the geometric fallback against real Maps.
     */
    private fun captureManeuverIconIfEnabled(sbn: StatusBarNotification, title: String, text: String) {
        try {
            val dir = filesDir
            val marker = File(dir, "CAPTURE_MANEUVERS")
            if (!marker.exists()) return
            val bitmap = TurnIconHeuristic.loadManeuverBitmap(this, sbn) ?: return
            val hash = bitmapPixelHash(bitmap)
            val label = (text.ifBlank { title }).lowercase()
                .replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "unlabeled" }
            val outDir = File(dir, "maps_capture").apply { mkdirs() }
            val file = File(outDir, "${label}__${hash}.png")
            if (file.exists()) return
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            AppLogger.log("Capture", "Saved maneuver icon: text=${redact(text)} hash=$hash -> ${file.name}")
        } catch (e: Exception) {
            AppLogger.log("Capture", "!! capture failed: $e")
        }
    }

    /**
     * Redact notification content before logging. [AppLogger] mirrors every line
     * to a public `Download/OpenDash` copy, so raw titles/bodies/destinations
     * there would leak private messages and locations to shared storage
     * (readable by other apps with storage access, over USB/MTP and cloud
     * backup, and surviving app uninstall). Log only the length, never content.
     */
    private fun redact(s: String): String = if (s.isEmpty()) "<empty>" else "<${s.length} chars>"

    // Keys of notifications accepted as navigation guidance, so a removal is only
    // treated as "guidance ended" for those (not for Maps' traffic/sharing alerts).
    private val navKeys = ConcurrentHashMap.newKeySet<String>()

    // Any notification from a known nav app counts (several apps don't reliably set
    // CATEGORY_NAVIGATION - requiring it dropped real updates), except for Google
    // Maps, whose non-guidance notifications are filtered out; see NavNotificationFilter.
    private fun isNavigationNotification(sbn: StatusBarNotification, settings: AppSettings): Boolean =
        NavNotificationFilter.isNavigation(
            packageName = sbn.packageName,
            flags = sbn.notification.flags,
            category = sbn.notification.category,
            navAppOverride = settings.navAppOverride,
            knownNavApps = AppSettings.KNOWN_NAV_APPS,
        )
}
