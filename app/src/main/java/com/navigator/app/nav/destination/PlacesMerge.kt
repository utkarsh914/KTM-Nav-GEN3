package com.navigator.app.nav.destination

/** Pure merging of saved places for backup restore: what is already on the phone always wins. */
internal object PlacesMerge {

    private fun key(p: SavedPlace) = PlacesJson.coordKey(p.lat, p.lng)

    /**
     * Existing favorites stay; incoming saved places are added when their coordinates are new;
     * incoming Home / Work only fill a slot that is still empty.
     */
    fun favorites(existing: List<FavoritePlace>, incoming: List<FavoritePlace>): List<FavoritePlace> {
        val out = ArrayList(existing)
        val haveKeys = existing.filter { it.slot == FavoriteSlot.OTHER }.map { key(it.place) }.toHashSet()
        for (f in incoming) {
            when (f.slot) {
                FavoriteSlot.OTHER -> if (haveKeys.add(key(f.place))) out += f
                FavoriteSlot.HOME, FavoriteSlot.WORK -> if (out.none { it.slot == f.slot }) out += f
            }
        }
        return out
    }

    /** Existing recents first (newest first), then incoming ones not already present, capped. */
    fun recents(existing: List<SavedPlace>, incoming: List<SavedPlace>, max: Int): List<SavedPlace> {
        val seen = HashSet<String>()
        val out = ArrayList<SavedPlace>()
        for (p in existing + incoming) if (seen.add(key(p))) out += p
        return out.take(max)
    }
}
