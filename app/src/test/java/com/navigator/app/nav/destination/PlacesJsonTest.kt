package com.navigator.app.nav.destination

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesJsonTest {

    @Test fun oneBadEntryDoesNotDropTheRest() {
        val raw = """[{"lat":12.9,"lng":77.5,"label":"A"},{"lat":"x"},{"label":"no coords"},
            {"lat":999,"lng":0,"label":"out of range"},"junk",{"lat":1.0,"lng":2.0,"label":"B","address":"Rd"}]"""
        val parsed = PlacesJson.parsePlaces(raw)
        assertFalse(parsed.corrupt)
        assertEquals(listOf("A", "B"), parsed.items.map { it.label })
        assertEquals("Rd", parsed.items[1].address)
    }

    @Test fun favoritesKeepSlotsAndTolerateUnknownSlot() {
        val raw = """[{"lat":1,"lng":2,"label":"Home","slot":"HOME"},{"lat":3,"lng":4,"label":"X","slot":"BOGUS"},{"bad":1}]"""
        val items = PlacesJson.parseFavorites(raw).items
        assertEquals(listOf(FavoriteSlot.HOME, FavoriteSlot.OTHER), items.map { it.slot })
    }

    @Test fun unreadableJsonIsReportedCorruptNotSilentlyEmpty() {
        val parsed = PlacesJson.parseFavorites("{not json")
        assertTrue(parsed.corrupt)
        assertTrue(parsed.items.isEmpty())
        assertFalse(PlacesJson.parseFavorites(null).corrupt)
        assertFalse(PlacesJson.parseFavorites("").corrupt)
    }

    @Test fun roundTrip() {
        val favs = listOf(
            FavoritePlace(SavedPlace(12.9716, 77.5946, "Home", "Rd"), FavoriteSlot.HOME),
            FavoritePlace(SavedPlace(1.5, 2.5, "Cafe"), FavoriteSlot.OTHER),
        )
        assertEquals(favs, PlacesJson.parseFavorites(PlacesJson.writeFavorites(favs)).items)
        val places = favs.map { it.place }
        assertEquals(places, PlacesJson.parsePlaces(PlacesJson.writePlaces(places)).items)
    }

    @Test fun coordKeyIgnoresDefaultLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY) // comma decimal separator
            assertEquals("12.9716,77.5946", PlacesJson.coordKey(12.97164, 77.59456))
        } finally {
            Locale.setDefault(old)
        }
    }
}
