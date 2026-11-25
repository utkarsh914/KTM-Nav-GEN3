package com.navigator.app.data.settings

import android.content.SharedPreferences
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Observable settings. Reads and writes still go through [AppSettings] (so key names, clamping and
 * the backup whitelist stay as they are); this adds a [snapshot] that re-emits whenever the
 * preferences file changes, including writes made by services, restore and other [AppSettings]
 * instances. UI state should come from here, never from a fresh `AppSettings(context)`.
 */
class SettingsRepository(
    private val settings: AppSettings,
    /** Whether the in-app engine can run (a key and Play services). Asked once, on first use. */
    engineAvailable: () -> Boolean = { true },
) {
    private val available by lazy(engineAvailable)

    /** The engine that actually drives the app: the stored choice, but only when it can run. The one place this is decided. */
    fun engineInUse(snap: SettingsSnapshot = snapshot.value): NavEngine = NavEngine.inUse(snap.engine, available)

    val engineInUseFlow: Flow<NavEngine> get() = snapshot.map { engineInUse(it) }.distinctUntilChanged()


    /**
     * The raw store, for what is not in [snapshot]: per-icon calibration, the paired-before flag and other
     * service-side keys. Writes through it still reach [snapshot]. UI state comes from [snapshot].
     */
    val legacy: AppSettings get() = settings

    private val _snapshot = MutableStateFlow(SettingsSnapshot.read(settings))
    val snapshot: StateFlow<SettingsSnapshot> = _snapshot.asStateFlow()

    // The file also holds pairing keys, so this fires on every such write too; reads are cheap and
    // equal snapshots don't re-emit.
    // SharedPreferences holds listeners weakly, so this must stay referenced for the repository's life.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    init {
        settings.preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Apply a change through the typed [AppSettings] setters; [snapshot] follows. Some setters
     * (bike address and name) write with `commit()`, so call this off the main thread for those.
     */
    fun update(change: AppSettings.() -> Unit) {
        settings.change()
        refresh()
    }

    /** Re-read the store, e.g. after a restore replaced the preferences wholesale. */
    @Synchronized
    fun refresh() {
        // Read and publish together: two racing refreshes must not leave the older read last.
        _snapshot.value = SettingsSnapshot.read(settings)
    }
}
