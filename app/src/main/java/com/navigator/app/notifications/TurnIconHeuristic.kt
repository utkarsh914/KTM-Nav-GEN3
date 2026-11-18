package com.navigator.app.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import com.navigator.app.ble.BccuProtocol
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.DrivingSide

/**
 * Resolves a navigation notification into a dash [BccuProtocol.TurnIcon].
 *
 * Three layers, in order of confidence:
 *
 * 1. **Text maneuver parsing** (new, primary). Most nav apps put the maneuver
 *    in words somewhere in the notification ("Sharp left", "Make a U-turn",
 *    "3rd exit", "Keep right", "Slight left onto…"). Parsing that maps
 *    directly onto the full icon table - including sharp/slight severity,
 *    U-turns, roundabout exits and keep-lane maneuvers - which the old
 *    left/right-ink heuristic could never express. This is what fixes both
 *    "hard turns show as a slight arrow" (a sharp left now maps to HEAVY_LEFT,
 *    not LIGHT_LEFT) and "other symbols never show" (u-turns/roundabouts/keep).
 * 2. **Exact bitmap-hash cache** ([IconHashCache]) for icons a maneuver word
 *    wasn't found for - reliable once a hash has been confirmed.
 * 3. **Graduated ink heuristic** as a last resort: left/right ink dominance
 *    now grades into LIGHT / QUITE / HEAVY (and straight) by how lopsided the
 *    arrow is, instead of always emitting a single LIGHT_* value.
 */
object TurnIconHeuristic {

    private const val SAMPLE_SIZE = 48

    // Severity bands, calibrated from real Google Maps arrows on hardware
    // (2026-07-06 logs): a straight arrow measured a full-height left/right ink
    // ratio of ~1.00, while a real 90-degree "Turn left" measured only ~1.14 -
    // the tall vertical shaft dilutes the ratio, so the OLD 1.30 cutoff for a
    // "normal" turn mis-called every Maps turn as a slight one. These cutoffs
    // put ~1.14 into QUITE (a proper turn) instead of LIGHT.
    private const val STRAIGHT_MAX = 1.05   // below this -> going straight
    private const val LIGHT_MAX = 1.12      // slight turn
    private const val QUITE_MAX = 1.40      // normal ~90-degree turn; above -> sharp
    // The arrowhead lives in the top of the icon; measuring only the top rows
    // separates sharp turns (head swung far to one side) from gentle ones far
    // better than the shaft-diluted full-height ratio, so it's used to escalate.
    private const val TOP_REGION_FRACTION = 0.45
    private const val TOP_QUITE_RATIO = 1.8
    private const val TOP_HEAVY_RATIO = 3.0
    // A genuinely-straight arrow has a near-1.0 arrowhead (top-region) ratio
    // (measured 1.005 on real Maps). A fork/keep whose full-image ratio still
    // looks straight (shaft dominates) but whose arrowhead clearly leans - e.g.
    // the real "towards A / B" fork measured full=1.024 but top=1.592 - must NOT
    // be called GO_STRAIGHT. Above this top-ratio we treat it as at least a
    // slight turn in the leaning direction. Comfortably above straight's ~1.00
    // and below a real 90-degree turn's ~2.05, so neither is reclassified.
    private const val STRAIGHT_TOP_MAX = 1.35

    fun guessDirection(
        context: Context,
        sbn: StatusBarNotification,
        drivingSide: DrivingSide = DrivingSide.UNKNOWN,
    ): BccuProtocol.TurnIcon? {
        val text = collectText(sbn)

        // Roundabouts: the text only carries an ORDINAL exit count, but the dash
        // glyph is chosen by exit ANGLE. The Maps icon shows the real angle, so
        // for a roundabout the icon (user calibration, then the classifier) wins
        // over the text; the ordinal is only an approximate fallback.
        if (mentionsRoundabout(text)) {
            loadIconBitmap(context, sbn)?.let { bmp ->
                val fromIcon = IconHashCache.lookup(bmp, context)
                    ?: ManeuverClassifier.classify(context, bmp, drivingSide)
                if (fromIcon != null && isRoundaboutResult(fromIcon)) {
                    AppLogger.log("TurnIcon", "Roundabout from icon -> ${fromIcon.name}")
                    return fromIcon
                }
            }
        }

        parseManeuverFromText(text, drivingSide)?.let {
            AppLogger.log("TurnIcon", "Maneuver from text -> ${it.name}")
            return it
        }

        val bitmap = loadIconBitmap(context, sbn) ?: return null

        // 1. User's manual calibration (exact per-bitmap override) wins.
        IconHashCache.lookup(bitmap, context)?.let { return it }

        // 2. On-device TFLite classifier (ported from the KTM Gen-3 companion
        //    app). Purpose-trained on nav maneuver icons, so it recognises the
        //    exact maneuver - including every roundabout exit - which the pixel
        //    heuristic below cannot. This is the real fix for Google Maps icons.
        ManeuverClassifier.classify(context, bitmap, drivingSide)?.let {
            AppLogger.log("TurnIcon", "Maneuver from model -> ${it.name}")
            return it
        }

        // 3. Last-resort ink heuristic (only if the model isn't confident / absent).
        val (leftInk, rightInk) = measureLeftRightInk(bitmap, 0.0)
        val (topLeft, topRight) = measureLeftRightInk(bitmap, TOP_REGION_FRACTION)
        if (leftInk + rightInk <= 0) return null

        // Direction from the arrowhead (top region) - more reliable than the
        // full image, whose bottom shaft is centred for every maneuver.
        val leftDominant = if (topLeft + topRight > 0) topLeft >= topRight else leftInk >= rightInk
        val fullRatio = ratio(leftInk, rightInk)
        val topRatio = ratio(topLeft, topRight)
        AppLogger.log("TurnIcon", "Icon heuristic: full L=$leftInk R=$rightInk (r=${"%.3f".format(fullRatio)}), top L=$topLeft R=$topRight (r=${"%.3f".format(topRatio)})")

        val severity = when {
            // Straight only when BOTH the full image and the arrowhead are
            // near-symmetric - a leaning arrowhead (STRAIGHT_TOP_MAX) means a
            // fork/keep/slight turn, never straight, even if the shaft-diluted
            // full ratio looks straight.
            fullRatio < STRAIGHT_MAX && topRatio < STRAIGHT_TOP_MAX -> 0 // straight
            fullRatio >= QUITE_MAX || topRatio >= TOP_HEAVY_RATIO -> 3  // heavy/sharp
            fullRatio >= LIGHT_MAX || topRatio >= TOP_QUITE_RATIO -> 2  // quite/normal
            else -> 1 // light/slight
        }
        return when (severity) {
            0 -> BccuProtocol.TurnIcon.GO_STRAIGHT
            1 -> if (leftDominant) BccuProtocol.TurnIcon.LIGHT_LEFT else BccuProtocol.TurnIcon.LIGHT_RIGHT
            2 -> if (leftDominant) BccuProtocol.TurnIcon.QUITE_LEFT else BccuProtocol.TurnIcon.QUITE_RIGHT
            else -> if (leftDominant) BccuProtocol.TurnIcon.HEAVY_LEFT else BccuProtocol.TurnIcon.HEAVY_RIGHT
        }
    }

    private fun ratio(a: Long, b: Long): Double {
        val hi = maxOf(a, b); val lo = minOf(a, b)
        return hi.toDouble() / lo.coerceAtLeast(1)
    }

    /** Gather every text field a nav app might carry the maneuver phrase in. */
    private fun collectText(sbn: StatusBarNotification): String {
        val e = sbn.notification.extras
        val keys = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUMMARY_TEXT,
        )
        val sb = StringBuilder()
        for (k in keys) {
            e.getCharSequence(k)?.let { sb.append(' ').append(it) }
        }
        sbn.notification.tickerText?.let { sb.append(' ').append(it) }
        return sb.toString().lowercase()
    }

    /**
     * Map an instruction string to an icon. Order matters - the most specific
     * phrases (sharp/slight/u-turn/roundabout/keep) are checked before the
     * generic "turn left/right", so a "sharp left" isn't swallowed by "left".
     * Returns null when no maneuver word is present (falls through to bitmap).
     */
    internal fun parseManeuverFromText(
        t: String,
        drivingSide: DrivingSide = DrivingSide.UNKNOWN,
    ): BccuProtocol.TurnIcon? {
        if (t.isBlank()) return null
        // Whole-word matching throughout: plain substrings misfired on place names
        // ("Rampura" -> ramp/exit, "Leftwich Rd" -> left, "Hardware Rd" -> sharp).
        fun has(vararg words: String) = words.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(t) }
        // The instruction's direction is the FIRST left/right word; a later one
        // usually belongs to a road name ("Turn right onto Left Bank Rd").
        val side = SIDE_RE.find(t)?.value
        val left = side == "left"
        val right = side == "right"
        // Left-hand traffic circulates clockwise (RH glyph bank) and its
        // ramps/exits are on the left; shared with the SDK path so they agree.
        val clockwise = KtmManeuverMapping.clockwiseForDrivingSide(drivingSide)
        val rampLeft = KtmManeuverMapping.rampOnLeftFor(drivingSide)

        // Roundabouts: "roundabout" / "rotary" + an exit ordinal. The dash
        // sections are fixed exit-ANGLE glyphs (hardware-verified, see
        // KtmManeuverMapping.roundaboutSection), so the ordinal is converted to
        // an approximate angle - NOT used as the section index. No ordinal ->
        // UNDEFINED (no arrow) rather than inventing a direction.
        if (mentionsRoundabout(t)) {
            val exit = roundaboutExit(t) ?: return BccuProtocol.TurnIcon.UNDEFINED
            return KtmManeuverMapping.roundaboutIcon(
                KtmManeuverMapping.ordinalExitAngle(exit, clockwise),
                clockwise,
            )
        }

        // U-turn. Explicit side wins; otherwise a U-turn crosses oncoming
        // traffic: right in left-hand traffic (India), left in right-hand.
        if (UTURN_RE.containsMatchIn(t)) {
            val uRight = if (left || right) right else rampLeft
            return if (uRight) BccuProtocol.TurnIcon.UTURN_RIGHT else BccuProtocol.TurnIcon.UTURN_LEFT
        }

        // Sharp / hard.
        if (has("sharp", "sharply", "hard") && (left || right)) {
            return if (left) BccuProtocol.TurnIcon.HEAVY_LEFT else BccuProtocol.TurnIcon.HEAVY_RIGHT
        }
        // Slight / soft / bear.
        if (has("slight", "slightly", "soft", "bear") && (left || right)) {
            return if (left) BccuProtocol.TurnIcon.LIGHT_LEFT else BccuProtocol.TurnIcon.LIGHT_RIGHT
        }
        // "Turn left to stay on NH 48" is a real turn - checked before keep/stay,
        // which would otherwise read it as a lane keep.
        TURN_SIDE_RE.find(t)?.let {
            return if (it.groupValues[1] == "left") BccuProtocol.TurnIcon.QUITE_LEFT else BccuProtocol.TurnIcon.QUITE_RIGHT
        }
        // Keep / stay in lane.
        if (has("keep", "stay") && (left || right)) {
            return if (left) BccuProtocol.TurnIcon.KEEP_LEFT else BccuProtocol.TurnIcon.KEEP_RIGHT
        }
        // Merge onto a highway. "Merge left" moves left, i.e. off a ramp on the
        // RIGHT; with no side, the ramp is on the rider's driving side.
        // Only a side directly after "merge" counts; a left/right inside a road
        // name ("Merge onto Left Bank Expy") must not flip the glyph.
        if (has("merge")) {
            val mergeSide = MERGE_SIDE_RE.find(t)?.groupValues?.get(1)
            val rampOnLeft = if (mergeSide != null) mergeSide == "right" else rampLeft
            return KtmManeuverMapping.enterHighwayIcon(rampOnLeft = rampOnLeft)
        }
        // Take a motorway exit / off-ramp. Same rule: explicit side wins, else the
        // driving-side lane. BUT an explicit turn onto an on-ramp ("Turn right
        // onto the ramp to NH 48") is a surface-road turn, not a highway exit -
        // let it fall through to the plain-turn branch so the dash shows the real
        // turn, matching the SDK path's ON_RAMP_* -> turn-arrow mapping.
        val turnOntoRamp = (left || right) &&
            (has("turn") || t.contains("onto the ramp") || has("on-ramp") || t.contains("on ramp"))
        if (!turnOntoRamp && has("exit", "ramp", "off-ramp")) {
            val onLeft = if (left || right) left else rampLeft
            return if (onLeft) BccuProtocol.TurnIcon.LEAVE_HIGHWAY_LEFT_LANE else BccuProtocol.TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE
        }
        // Arrival / departure.
        if (has("destination", "arrive", "arrived")) {
            return BccuProtocol.TurnIcon.END
        }
        // Plain turn: this is a normal ~90 degree turn, so QUITE_* (not LIGHT_*).
        if (has("turn") && left) return BccuProtocol.TurnIcon.QUITE_LEFT
        if (has("turn") && right) return BccuProtocol.TurnIcon.QUITE_RIGHT
        // Straight / continue / head.
        if (has("straight", "continue", "head", "proceed")) {
            return BccuProtocol.TurnIcon.GO_STRAIGHT
        }
        // A bare directional word with no other maneuver context - treat as a turn.
        if (left) return BccuProtocol.TurnIcon.QUITE_LEFT
        if (right) return BccuProtocol.TurnIcon.QUITE_RIGHT
        return null
    }

    private val SIDE_RE = Regex("""\b(left|right)\b""")
    private val MERGE_SIDE_RE = Regex("""\bmerge\s+(left|right)\b""")
    private val TURN_SIDE_RE = Regex("""\bturn\s+(left|right)\b""")
    private val UTURN_RE = Regex("""\bu-?\s?turn\b|\bmake a u\b""")

    /**
     * Whole-word "roundabout" or "rotary" (the US term). "Rotary" that is part of
     * a proper name ("Rotary Club Rd") doesn't count on its own.
     */
    internal fun mentionsRoundabout(t: String): Boolean {
        if (ROUNDABOUT_WORD_RE.containsMatchIn(t)) return true
        return ROTARY_RE.findAll(t).any { m ->
            ROTARY_NAME_RE.find(t, m.range.first)?.range?.first != m.range.first
        }
    }

    private val ROUNDABOUT_WORD_RE = Regex("""\broundabouts?\b""")
    private val ROTARY_RE = Regex("""\brotary\b""")
    // "rotary" used as part of a proper name (Rotary Club/Road/Nagar...) is not a maneuver.
    private val ROTARY_NAME_RE = Regex("""rotary\s+(club|rd|road|nagar|circle|layout|park|hall|school|colony|street|st)\b""")

    /** Icon results acceptable for a roundabout instruction: a section glyph or a (roundabout) U-turn. */
    private fun isRoundaboutResult(icon: BccuProtocol.TurnIcon): Boolean =
        KtmManeuverMapping.isRoundaboutSection(icon) ||
            icon == BccuProtocol.TurnIcon.UTURN_LEFT || icon == BccuProtocol.TurnIcon.UTURN_RIGHT

    /** Pull a roundabout exit number from phrases like "3rd exit" or "exit 2". */
    private fun roundaboutExit(t: String): Int? {
        Regex("""(\d+)(?:st|nd|rd|th)?\s+exit""").find(t)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""exit\s+(\d+)""").find(t)?.let { return it.groupValues[1].toIntOrNull() }
        listOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5).forEach { (w, n) ->
            if (t.contains("$w exit")) return n
        }
        return null
    }

    /** Public wrapper: render a nav notification's large maneuver icon to the sampled bitmap (for debug capture). */
    fun loadManeuverBitmap(context: Context, sbn: StatusBarNotification): Bitmap? = loadIconBitmap(context, sbn)

    private fun loadIconBitmap(context: Context, sbn: StatusBarNotification): Bitmap? {
        val icon = sbn.notification.getLargeIcon() ?: return null
        val drawable = try {
            icon.loadDrawable(context)
        } catch (e: Exception) {
            AppLogger.log("TurnIcon", "!! Failed to load notification icon: $e")
            null
        } ?: return null
        return drawableToBitmap(drawable)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            AppLogger.log("TurnIcon", "!! Failed to render icon to bitmap: $e")
            null
        }
    }

    /**
     * Sum left-half vs right-half ink alpha. [fromYFraction] restricts the scan
     * to the **top** [fromYFraction] of the image height, where the arrowhead
     * sits (0.0 = whole image; 0.45 = top 45% only).
     */
    private fun measureLeftRightInk(bitmap: Bitmap, fromYFraction: Double): Pair<Long, Long> {
        var leftInk = 0L
        var rightInk = 0L
        val halfWidth = bitmap.width / 2
        val startY = 0
        val endY = if (fromYFraction <= 0.0) bitmap.height
        else (bitmap.height * fromYFraction).toInt().coerceIn(1, bitmap.height)
        for (y in startY until endY) {
            for (x in 0 until bitmap.width) {
                val alpha = (bitmap.getPixel(x, y) ushr 24) and 0xFF
                if (x < halfWidth) leftInk += alpha else rightInk += alpha
            }
        }
        return leftInk to rightInk
    }
}
