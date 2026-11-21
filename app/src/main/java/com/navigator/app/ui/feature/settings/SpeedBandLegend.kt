package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ui.map.bandColor
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** The slow-to-fast speed colours a ride track will use, exactly as the map draws them. */
@Composable
fun SpeedBandLegend(base: Int, step: Int, count: Int, modifier: Modifier = Modifier) {
    val bands = RideMetrics.SpeedBands(base, step, count)
    val top = base + step * (count - 1)
    val c = KtmTheme.colors
    val description = stringResource(R.string.rec_legend_cd, top)
    Column(modifier.fillMaxWidth().padding(horizontal = KtmSpacing.lg).semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(KtmLayout.legendBar).clip(KtmShapes.Badge)) {
            for (i in 0 until bands.bandCount) {
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(bandColor(i, bands.bandCount))))
            }
        }
        // Every band's edge: 0, where each colour starts, and the last colour's start with "+" (ST5).
        Row(Modifier.fillMaxWidth().padding(top = KtmSpacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.rec_legend_start), style = MaterialTheme.typography.labelSmall, color = c.fg2)
            for (i in 0 until bands.bandCount - 1) {
                Text((base + step * i).toString(), style = MaterialTheme.typography.labelSmall, color = c.fg2)
            }
            Text(stringResource(R.string.rec_legend_end, top), style = MaterialTheme.typography.labelSmall, color = c.fg2)
        }
    }
}
