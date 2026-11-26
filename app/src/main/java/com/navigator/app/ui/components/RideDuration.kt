package com.navigator.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.navigator.app.ride.rideDurationText

/** "38 min" / "1 h 05 min" for a finished ride (see [rideDurationText]); one wording on the card, replay, trim and merge. */
@Composable
fun rideDuration(seconds: Int): String = rideDurationText(LocalResources.current, seconds)
