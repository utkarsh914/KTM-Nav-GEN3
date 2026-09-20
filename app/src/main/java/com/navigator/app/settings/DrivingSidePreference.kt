package com.navigator.app.settings

import com.navigator.app.nav.model.DrivingSide
import java.util.Locale

/**
 * The rider's driving-side setting. Distinct from the runtime
 * [com.navigator.app.nav.model.DrivingSide]: this is the *user preference*,
 * which may be [AUTO] (infer from the device region). The in-app Google Nav SDK
 * reports driving side per-step from route geometry and does not need this - it
 * only backs the **notification-mirroring** path (Google Maps / other nav apps
 * mirrored via their notifications), which carries no driving-side context of
 * its own, so an unqualified "merge"/"exit"/roundabout would otherwise default
 * to right-hand traffic and be wrong for left-hand-traffic countries (India).
 */
enum class DrivingSidePreference(val id: String) {
    AUTO("auto"),
    LEFT("left"),
    RIGHT("right");

    companion object {
        fun fromId(id: String?): DrivingSidePreference =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Resolves a [DrivingSidePreference] to a concrete [DrivingSide], inferring from
 * the device region when the preference is [DrivingSidePreference.AUTO]. Pure and
 * JVM-testable (region is passed in / read from [Locale]).
 */
object DrivingSideResolver {

    /**
     * ISO-3166 alpha-2 country codes that drive on the **left** (left-hand
     * traffic). Curated from the standard LHT list; India (IN) is included. Any
     * region not listed here is treated as right-hand traffic.
     */
    val LEFT_HAND_TRAFFIC_REGIONS: Set<String> = setOf(
        "AI", "AG", "AU", "BS", "BD", "BB", "BM", "BT", "BW", "BN", "KY", "CX",
        "CC", "CK", "CY", "DM", "TL", "FK", "FJ", "GD", "GG", "GY", "HK", "IN",
        "ID", "IE", "IM", "JM", "JP", "JE", "KE", "KI", "LS", "MO", "MW", "MY",
        "MV", "MT", "MU", "MS", "MZ", "NA", "NR", "NP", "NZ", "NU", "NF", "PK",
        "PG", "PN", "SH", "KN", "LC", "VC", "WS", "SC", "SG", "SB", "ZA", "LK",
        "SR", "SZ", "TZ", "TH", "TK", "TO", "TT", "TC", "TV", "UG", "GB", "VG",
        "VI", "ZM", "ZW",
    )

    /**
     * Resolve the preference to a concrete driving side. [regionOverride] (an
     * ISO-3166 alpha-2 code) is used when provided; otherwise the default
     * [Locale] country is read. When the region can't be determined for [AUTO],
     * falls back to [DrivingSide.UNKNOWN] (which downstream treats as left-hand /
     * clockwise - the plan default).
     */
    fun resolve(
        preference: DrivingSidePreference,
        regionOverride: String? = null,
    ): DrivingSide = when (preference) {
        DrivingSidePreference.LEFT -> DrivingSide.LEFT
        DrivingSidePreference.RIGHT -> DrivingSide.RIGHT
        DrivingSidePreference.AUTO -> {
            val region = (regionOverride ?: Locale.getDefault().country)
                .trim()
                .uppercase(Locale.ROOT)
            when {
                region.isEmpty() -> DrivingSide.UNKNOWN
                region in LEFT_HAND_TRAFFIC_REGIONS -> DrivingSide.LEFT
                else -> DrivingSide.RIGHT
            }
        }
    }
}
