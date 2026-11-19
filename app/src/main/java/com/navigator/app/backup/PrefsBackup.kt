package com.navigator.app.backup

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed JSON form of the app preferences for a backup. The bike pairing never goes in: it only
 * works with THIS phone and dash, and a restored copy would claim a pairing the new phone can't
 * use (and skip the dash's "add device" prompt). The onboarding flag is left out too, so a new
 * phone still walks through the permission setup.
 */
object PrefsBackup {

    private enum class T(val code: String) { B("b"), I("i"), S("s"), SS("ss") }
    private class Rule(val type: T, val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE, val maxLen: Int = 80)

    /**
     * Only these keys, with exactly these types and ranges, are ever backed up or restored. The
     * app's getters throw ClassCastException on a value of the wrong type, so a blind restore of a
     * crafted / future-version file could crash the app at every launch.
     */
    private val RULES: Map<String, Rule> = mapOf(
        "avg_speed_mode" to Rule(T.S), "dash_preview_overlay" to Rule(T.B), "user_name" to Rule(T.S, maxLen = 60),
        "theme_mode" to Rule(T.S), "font_mode" to Rule(T.S), "nav_theme_mode" to Rule(T.S),
        "accent_color" to Rule(T.S, maxLen = 40), "notification_source_apps" to Rule(T.SS),
        "nav_app_override" to Rule(T.S, maxLen = 160),
        "mirror_enabled" to Rule(T.B), "marquee_enabled" to Rule(T.B), "turn_beep_enabled" to Rule(T.B),
        "beep_volume_percent" to Rule(T.I, 5, 100), "swap_beep_channels" to Rule(T.B),
        "overspeed_enabled" to Rule(T.B), "overspeed_limit_kmh" to Rule(T.I, 30, 200),
        "ride_recording_enabled" to Rule(T.B), "ride_min_distance_m" to Rule(T.I, 50, 50_000),
        "ride_min_duration_s" to Rule(T.I, 10, 3_600), "ride_history_limit" to Rule(T.I, 10, 1_000),
        "nav_provider" to Rule(T.S), "latin_dash_text" to Rule(T.B), "full_sdk_nav_ui" to Rule(T.B), "live_updates_enabled" to Rule(T.B),
        "speed_band_base_kmh" to Rule(T.I, 10, 120), "speed_band_step_kmh" to Rule(T.I, 1, 100),
        "speed_band_count" to Rule(T.I, 1, 20), "location_marker_style" to Rule(T.S),
        "driving_side" to Rule(T.S), "vehicle_mode" to Rule(T.S),
    )
    // Per-icon calibration / test results: string families, capped in count.
    private val FAMILIES = listOf("turncal_" to Rule(T.S, maxLen = 60), "icon_test_" to Rule(T.S, maxLen = 200))
    private const val MAX_FAMILY_KEYS = 500
    private const val MAX_SET = 200

    /** Never leave the phone: pairing (only valid with THIS phone + dash) and the onboarding flag (a new phone must still set up permissions). */
    fun isExcluded(key: String): Boolean =
        key.startsWith("bonded_") || key.startsWith("paired_before_") || key.startsWith("session_keys_") ||
            key == "onboarding_complete"

    private fun ruleFor(key: String): Rule? =
        if (isExcluded(key)) null else RULES[key] ?: FAMILIES.firstOrNull { key.startsWith(it.first) }?.second

    fun encode(all: Map<String, *>): String {
        val o = JSONObject()
        val familyCount = HashMap<String, Int>()
        for ((k, v) in all) {
            val rule = ruleFor(k) ?: continue
            if (v == null) continue
            FAMILIES.firstOrNull { k.startsWith(it.first) }?.let { fam ->
                if (familyCount.merge(fam.first, 1, Int::plus)!! > MAX_FAMILY_KEYS) continue
            }
            val entry = JSONObject()
            when {
                rule.type == T.B && v is Boolean -> entry.put("t", "b").put("v", v)
                rule.type == T.I && v is Int -> entry.put("t", "i").put("v", v)
                rule.type == T.S && v is String -> entry.put("t", "s").put("v", v)
                rule.type == T.SS && v is Set<*> -> entry.put("t", "ss").put("v", JSONArray(v.filterIsInstance<String>()))
                else -> continue
            }
            o.put(k, entry)
        }
        return o.toString()
    }

    /** Validated entries from [json]: known keys only, exact types, clamped numbers, capped strings. */
    fun decode(json: String?): Map<String, Any> {
        if (json.isNullOrBlank()) return emptyMap()
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, Any>()
        val familyCount = HashMap<String, Int>()
        for (k in o.keys()) {
            val rule = ruleFor(k) ?: continue
            val e = o.optJSONObject(k) ?: continue
            if (e.optString("t") != rule.type.code) continue // wrong type for this key: never write it
            FAMILIES.firstOrNull { k.startsWith(it.first) }?.let { fam ->
                if (familyCount.merge(fam.first, 1, Int::plus)!! > MAX_FAMILY_KEYS) continue
            }
            val value: Any? = runCatching {
                when (rule.type) {
                    T.B -> e.getBoolean("v")
                    T.I -> e.getInt("v").coerceIn(rule.min, rule.max)
                    T.S -> e.getString("v").take(rule.maxLen)
                    T.SS -> e.getJSONArray("v").let { a ->
                        (0 until minOf(a.length(), MAX_SET)).map { a.getString(it).take(160) }.toSet()
                    }
                }
            }.getOrNull()
            if (value != null) out[k] = value
        }
        return out
    }

    fun applyTo(editor: SharedPreferences.Editor, entries: Map<String, Any>) {
        for ((k, v) in entries) {
            when (v) {
                is Boolean -> editor.putBoolean(k, v)
                is Int -> editor.putInt(k, v)
                is String -> editor.putString(k, v)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(k, v as Set<String>)
                }
            }
        }
    }
}
