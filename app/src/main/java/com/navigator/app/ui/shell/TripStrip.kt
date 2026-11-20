package com.navigator.app.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.navigator.app.app.appContainer
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.ui.components.ActivityStrip
import com.navigator.app.ui.components.ActivityStripState
import com.navigator.app.ui.feature.navigate.ActivityCard
import com.navigator.app.ui.feature.navigate.detailText
import com.navigator.app.ui.feature.navigate.titleText
import com.navigator.app.ui.theme.KtmSpacing

/**
 * MO6: a trip the rider minimised stays visible above the tab bar on every other tab; tapping it goes back to the
 * riding screen. On Navigate itself the same strip sits in the Where-to sheet.
 */
@Composable
fun TripStrip(onNavigateTab: Boolean, onOpen: () -> Unit) {
    if (onNavigateTab) return
    val trip by LocalContext.current.appContainer.trip.state.collectAsState()
    val nav by GoogleNavSdkProvider.state.collectAsState()
    val card = ActivityCard.build(trip, nav, System.currentTimeMillis()) ?: return
    ActivityStrip(
        state = ActivityStripState.Trip(title = card.titleText(), detail = card.detailText()),
        onOpen = onOpen, onResume = onOpen, modifier = Modifier.padding(KtmSpacing.sm),
    )
}
