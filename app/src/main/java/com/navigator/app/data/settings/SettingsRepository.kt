package com.navigator.app.data.settings

import android.content.SharedPreferences
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable settings. Reads and writes still go through [AppSettings] (so key names, clamping and
 * the backup whitelist stay as they are); this adds a [snapshot] that re-emits whenever the
 * preferences file changes, including writes made by services, restore and other [AppSettings]
 * instances. UI state should come from here, never from a fresh `AppSettings(context)`.
 */
class SettingsRepository(private val settings: AppSettings) {

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
