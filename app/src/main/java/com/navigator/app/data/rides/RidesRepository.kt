package com.navigator.app.data.rides

import com.navigator.app.ride.MergeResult
import com.navigator.app.ride.MergeToken
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

object RideSearch {
    /** Rides whose name or note contains every word of [query] (case-insensitive); all of them for a blank query. */
    fun filter(rides: List<RecordedRide>, query: String): List<RecordedRide> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return rides
        return rides.filter { r ->
            val hay = "${r.destinationLabel.orEmpty()}\n${r.notes.orEmpty()}".lowercase()
            words.all { it in hay }
        }
    }
}

/** What the Rides screens read and do: a live list, search, and the delete/merge operations with their undo. */
class RidesRepository(
    private val store: RideStore,
    val thumbnails: RideThumbnailCache = RideThumbnailCache(store),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Newest first, re-read whenever any ride changes. */
    val rides: Flow<List<RecordedRide>> = store.changes.map { withContext(io) { store.list() } }

    fun search(query: String): Flow<List<RecordedRide>> = rides.map { RideSearch.filter(it, query) }

    suspend fun softDelete(ids: Collection<String>): List<String> = withContext(io) {
        store.purgeDeleted(System.currentTimeMillis() - RideStore.UNDO_WINDOW_MS)
        store.softDelete(ids)
    }

    /** The undo is over: remove them (and their tracks) for good. */
    suspend fun purge(ids: Collection<String>) = withContext(io) { store.purge(ids) }
    suspend fun get(id: String): RecordedRide? = withContext(io) { store.get(id) }
    suspend fun points(id: String) = withContext(io) { store.points(id) }
    suspend fun updateMeta(id: String, label: String?, notes: String?) = withContext(io) { store.updateMeta(id, label, notes) }
    suspend fun restore(ids: Collection<String>): List<String> = withContext(io) { store.restore(ids) }
    suspend fun setSaved(id: String, saved: Boolean) = withContext(io) { store.setSaved(id, saved) }
    suspend fun merge(ids: Collection<String>): MergeResult = withContext(io) { store.mergeRides(ids) }
    suspend fun undoMerge(token: MergeToken): Boolean = withContext(io) { store.undoMerge(token) }
    suspend fun thumbnail(ride: RecordedRide): List<ThumbPoint> = withContext(io) { thumbnails.get(ride.id, ride.pointCount) }
}
