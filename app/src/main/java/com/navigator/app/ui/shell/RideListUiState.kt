package com.navigator.app.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.navigator.app.ui.screens.RideSortField

/**
 * The Rides list's selection, search and sort, held above the screen so they survive opening a
 * ride and coming back (the list leaves composition meanwhile). Reset when the app restarts.
 */
@Stable
class RideListUiState {
    var selectionMode by mutableStateOf(false)
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
    var query by mutableStateOf("")
    var sortField by mutableStateOf(RideSortField.DATE)
    var sortDesc by mutableStateOf(true)
}
