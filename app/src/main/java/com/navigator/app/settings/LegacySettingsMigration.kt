package com.navigator.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.navigator.app.logging.AppLogger

/**
 * One-time move of the old EncryptedSharedPreferences contents into the plain settings store.
 *
 * Everything that still lived in the encrypted file was low-sensitivity (onboarding flag, display
 * name, mirror-app list, ...): the bike's address, pairing flag and session keys were already in the
 * plain store because EncryptedSharedPreferences silently resets on some devices, which is worse
 * for a pairing than at-rest encryption inside an app-private, backup-excluded directory. The
 * library is deprecated, and its Keystore access made every cold start slower, so it is only kept
 * as a read-only source for this migration (remove it, and this class, once every install has run
 * it). The legacy files are left in place untouched.
 *
 * Plain values always win over legacy ones, and the done-flag is only set after the copied
 * values read back correctly, so a failed or interrupted run is retried on the next launch (a
 * number of launches; only then is the legacy data considered unrecoverable).
 */
internal object LegacySettingsMigration {

    const val KEY_DONE = "legacy_migration_done"
    const val KEY_COUNT = "legacy_migration_count"
    const val KEY_ATTEMPTS = "legacy_migration_attempts"
    const val MAX_ATTEMPTS = 10

    @Volatile private var checked = false

    fun migrateIfNeeded(context: Context, target: SharedPreferences) {
        if (checked) return
        synchronized(this) {
            if (checked) return
            try {
                if (!target.getBoolean(KEY_DONE, false)) run(context.applicationContext, target)
            } catch (e: Throwable) {
                AppLogger.log("Settings", "!! legacy settings migration crashed: ${e.javaClass.simpleName}")
            }
            checked = true
        }
    }

    /** What the legacy stores held, and whether reading them fully worked. */
    class LegacyRead(val entries: Map<String, Any?>, val ok: Boolean)

    private fun run(app: Context, target: SharedPreferences) {
        runMigration(target) { readLegacyFromDisk(app) }
    }

    private fun readLegacyFromDisk(app: Context): LegacyRead {
        val legacy = LinkedHashMap<String, Any?>()
        // Fallback store first (used when the encrypted one failed), encrypted store on top.
        runCatching { legacy.putAll(app.getSharedPreferences("opendash_settings_fallback", Context.MODE_PRIVATE).all) }
        // No legacy file = fresh install: don't create a Keystore key just to read nothing.
        val encryptedFile = java.io.File(app.dataDir, "shared_prefs/opendash_settings.xml")
        if (!encryptedFile.exists()) return LegacyRead(legacy, ok = true)
        return try {
            val masterKey = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val enc = EncryptedSharedPreferences.create(
                app, "opendash_settings", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            legacy.putAll(enc.all)
            LegacyRead(legacy, ok = true)
        } catch (e: Throwable) {
            AppLogger.log("Settings", "legacy encrypted store unreadable (${e.javaClass.simpleName})")
            LegacyRead(legacy, ok = false)
        }
    }

    /** The migration proper, with the legacy source injected (unit-testable). */
    internal fun runMigration(
        target: SharedPreferences,
        log: (String) -> Unit = { AppLogger.log("Settings", it) },
        readLegacy: () -> LegacyRead,
    ) {
        val attempts = target.getInt(KEY_ATTEMPTS, 0) + 1
        val read = try {
            readLegacy()
        } catch (e: Throwable) {
            LegacyRead(emptyMap(), ok = false)
        }

        val toWrite = planCopy(read.entries, target.all.keys)
        val editor = target.edit().putInt(KEY_ATTEMPTS, attempts)
        applyTo(editor, toWrite)
        val committed = editor.commit()

        val verified = committed && toWrite.all { (k, v) -> target.all[k] == v }
        // Done when everything copied and verified. If the encrypted store keeps failing it is
        // retried on later launches (a locked/flaky Keystore recovers); only after many
        // attempts is it treated as permanently unreadable (the pairing never depended on it).
        val done = verified && (read.ok || attempts >= MAX_ATTEMPTS)
        if (done) target.edit().putBoolean(KEY_DONE, true).putInt(KEY_COUNT, toWrite.size).commit()
        log(
            "legacy settings migration: copied=${toWrite.size} verified=$verified readOk=${read.ok} attempts=$attempts done=$done",
        )
    }

    /** Entries from [legacy] that are missing from the target (plain wins) and of a storable type. */
    fun planCopy(legacy: Map<String, Any?>, existingKeys: Set<String>): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for ((k, v) in legacy) {
            if (k in existingKeys || v == null) continue
            when (v) {
                is Boolean, is Int, is Long, is Float, is String -> out[k] = v
                is Set<*> -> if (v.all { it is String }) {
                    @Suppress("UNCHECKED_CAST")
                    out[k] = HashSet(v as Set<String>)
                }
            }
        }
        return out
    }

    fun applyTo(editor: SharedPreferences.Editor, entries: Map<String, Any>) {
        for ((k, v) in entries) {
            when (v) {
                is Boolean -> editor.putBoolean(k, v)
                is Int -> editor.putInt(k, v)
                is Long -> editor.putLong(k, v)
                is Float -> editor.putFloat(k, v)
                is String -> editor.putString(k, v)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(k, v as Set<String>)
                }
            }
        }
    }

    /** Short human-readable status for Settings > Diagnostics. */
    fun summary(target: SharedPreferences): String = when {
        target.getBoolean(KEY_DONE, false) -> "migrated (${target.getInt(KEY_COUNT, 0)} keys)"
        else -> "pending (attempt ${target.getInt(KEY_ATTEMPTS, 0)})"
    }
}
