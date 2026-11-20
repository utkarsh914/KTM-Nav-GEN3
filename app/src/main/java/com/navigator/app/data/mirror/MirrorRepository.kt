package com.navigator.app.data.mirror

import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.notifications.NotificationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * The mirror engine's state as one flow: setup (from [PermissionsRepository] and the listener), whether Google Maps'
 * guidance is coming through, how fresh it is and whether sending to the dash is paused. The clock ticks only while
 * someone collects, so the freshness label moves without any screen-side timer.
 */
class MirrorRepository(
    private val permissions: PermissionsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = 1_000L,
    private val listenerConnected: Flow<Boolean> = NotificationRepository.listenerConnected,
    private val paused: Flow<Boolean> = NotificationNavProvider.paused,
    private val guidance: Flow<NotificationRepository.NavGuidance?> = NotificationRepository.navGuidance,
    private val navText: Flow<String?> = NotificationRepository.currentNavText,
    private val navPackage: Flow<String?> = NotificationRepository.currentNavPackage,
    private val lastUpdateMs: Flow<Long> = NotificationRepository.lastUpdateMs,
    private val setPausedImpl: (Boolean) -> Unit = NotificationNavProvider::setPaused,
) {
    private val clock: Flow<Long> = flow { while (true) { emit(now()); delay(tickMs) } }

    val status: Flow<MirrorStatus> = combine(
        combine(permissions.state, listenerConnected, paused) { p, l, pa -> Triple(p, l, pa) },
        combine(guidance, navText, navPackage, lastUpdateMs) { g, t, pkg, u -> arrayOf(g, t, pkg, u) },
        clock,
    ) { (perms, listener, paused), nav, nowMs ->
        @Suppress("UNCHECKED_CAST")
        MirrorStatusMapper.map(
            MirrorInputs(
                accessGranted = perms[PermissionId.NOTIFICATION_ACCESS].granted,
                listenerConnected = listener,
                batteryUnrestricted = perms[PermissionId.BATTERY].granted,
                paused = paused,
                guidance = nav[0] as NotificationRepository.NavGuidance?,
                navText = nav[1] as String?,
                navPackage = nav[2] as String?,
                lastUpdateMs = nav[3] as Long,
                nowMs = nowMs,
            ),
        )
    }

    /** Re-read what Android grants: Android has no callback, so the screen calls this when it resumes. */
    fun refresh() = permissions.refresh()

    fun setSending(on: Boolean) = setPausedImpl(!on)
}
