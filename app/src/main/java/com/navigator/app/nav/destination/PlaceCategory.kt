package com.navigator.app.nav.destination

/** Quick search categories shown as chips in the search panel. [query] is what Text Search is asked for. */
enum class PlaceCategory(val label: String, val query: String) {
    FUEL("Fuel", "petrol pump"),
    FOOD("Food", "restaurant"),
    COFFEE("Coffee", "cafe"),
    PARKING("Parking", "parking"),
    ATM("ATM", "atm"),
    HOSPITAL("Hospital", "hospital"),
    PHARMACY("Pharmacy", "pharmacy"),
    EV("EV charging", "ev charging station"),
}

/** One category search hit. [distanceMeters] is straight-line from the search origin (null without one). */
data class CategoryResult(
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val distanceMeters: Int?,
)
