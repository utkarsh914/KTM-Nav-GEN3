package com.navigator.app.ui.feature.rides

import androidx.annotation.StringRes
import com.navigator.app.R
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class RideSortField { Date, Distance, Duration, TopSpeed }

data class RideSort(val field: RideSortField = RideSortField.Date, val desc: Boolean = true) {
    /** The direction in the field's own words: "Newest first", "Shortest first", ... */
    @get:StringRes val label: Int
        get() = when (this.field) {
            RideSortField.Date -> if (desc) R.string.rides_sort_newest else R.string.rides_sort_oldest
            RideSortField.Distance -> if (desc) R.string.rides_sort_longest else R.string.rides_sort_shortest
            RideSortField.Duration -> if (desc) R.string.rides_sort_longest_time else R.string.rides_sort_shortest_time
            RideSortField.TopSpeed -> if (desc) R.string.rides_sort_fastest else R.string.rides_sort_slowest
        }

    companion object {
        /** The eight choices of the sort sheet, in display order. */
        val options: List<RideSort> = RideSortField.entries.flatMap { listOf(RideSort(it, true), RideSort(it, false)) }
    }
}

/** A row of the list: a day header or a ride, with the matched text to highlight while searching. */
sealed interface RideListItem {
    val key: String

    data class Header(val date: LocalDate) : RideListItem {
        override val key get() = "hdr-$date"
    }

    /** [showDate]: the list has no day headers (searching, or sorted by something else), so the card says which day. */
    data class Ride(val ride: RecordedRide, val titleMatch: IntRange?, val notesMatch: IntRange?, val showDate: Boolean = false) : RideListItem {
        override val key get() = ride.id
    }
}

enum class DayKind { Today, Yesterday, ThisYear, Older }

object RidesListBuilder {
    fun build(
        rides: List<RecordedRide>,
        query: String,
        sort: RideSort,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<RideListItem> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val shown = rides.filter { r -> words.all { w -> w in haystack(r) } }
        val by: Comparator<RecordedRide> = when (sort.field) {
            RideSortField.Date -> compareBy { it.startMs }
            RideSortField.Distance -> compareBy { it.distanceMeters }
            RideSortField.Duration -> compareBy { it.durationSeconds }
            RideSortField.TopSpeed -> compareBy { it.maxSpeedKmh }
        }
        val ordered = shown.sortedWith((if (sort.desc) by.reversed() else by).thenByDescending { it.startMs })
        val headers = words.isEmpty() && sort.field == RideSortField.Date
        fun item(r: RecordedRide) = RideListItem.Ride(
            r, words.firstNotNullOfOrNull { matchRange(r.destinationLabel, it) }, words.firstNotNullOfOrNull { matchRange(r.notes, it) },
            showDate = !headers,
        )
        // Day headers only when the order is by date and nothing is being searched.
        if (!headers) return ordered.map(::item)
        val out = ArrayList<RideListItem>(ordered.size + 8)
        var last: LocalDate? = null
        for (r in ordered) {
            val day = Instant.ofEpochMilli(r.startMs).atZone(zone).toLocalDate()
            if (day != last) { out += RideListItem.Header(day); last = day }
            out += item(r)
        }
        return out
    }

    private fun haystack(r: RecordedRide) = "${r.destinationLabel.orEmpty()}\n${r.notes.orEmpty()}".lowercase()

    private fun matchRange(text: String?, word: String): IntRange? {
        val i = text?.indexOf(word, ignoreCase = true) ?: return null
        return if (i < 0) null else i until (i + word.length)
    }

    fun dayKind(date: LocalDate, today: LocalDate): DayKind = when {
        date == today -> DayKind.Today
        date == today.minusDays(1) -> DayKind.Yesterday
        date.year == today.year -> DayKind.ThisYear
        else -> DayKind.Older
    }
}

/** What the title and the source line say about how a ride started. */
object RideSourceText {
    @StringRes fun title(source: String?): Int = when (source) {
        RideSource.NAV -> R.string.ride_title_navigated
        RideSource.GPX -> R.string.ride_title_imported
        RideSource.MIRROR -> R.string.ride_title_mirrored
        RideSource.MERGED -> R.string.ride_title_merged
        else -> R.string.ride_title_recorded
    }

    @StringRes fun how(source: String?): Int = when (source) {
        RideSource.NAV -> R.string.ride_how_navigated
        RideSource.GPX -> R.string.ride_how_imported
        RideSource.MIRROR -> R.string.ride_how_mirrored
        RideSource.MERGED -> R.string.ride_how_merged
        else -> R.string.ride_how_recorded
    }
}

/** What the selection allows. Pure so the rules are tested without a screen. */
object RideSelection {
    fun visible(selected: Set<String>, items: List<RideListItem>): Set<String> {
        val ids = items.filterIsInstance<RideListItem.Ride>().map { it.ride.id }.toSet()
        return selected intersect ids
    }

    /** Star everything unless everything is already starred, then unstar. */
    fun starTarget(rides: List<RecordedRide>): Boolean = rides.any { !it.saved }
}

/** When a list goes back to the top: only if its [key] differs from the last one seen; the first sight (or no key yet) never does. */
internal class TopOnChange<T : Any> {
    private var last: T? = null

    fun shouldReset(key: T?): Boolean {
        if (key == null) return false
        val reset = last != null && last != key
        last = key
        return reset
    }
}
