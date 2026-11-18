package com.navigator.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.nav.DashPreview

/** Debug-only overlay: tap the chip to expand what the dash would currently show. */
@Composable
fun DashPreviewPanel() {
    val s by DashPreview.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(start = 8.dp, bottom = 96.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xCC000000))
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val mono = FontFamily.Monospace
            if (!expanded) {
                Text("DASH ${s.icon?.name ?: "-"}", color = Color.Green, fontSize = 10.sp, fontFamily = mono)
            } else {
                val lines = listOf(
                    "DASH PREVIEW (debug, #${s.updates})",
                    "nav: on=${s.guidanceOn} gps=${s.gpsIconOn}",
                    "icon: ${s.icon?.name ?: "-"}",
                    "dist: ${s.distance ?: "-"}",
                    "road: ${s.road ?: "-"}",
                    "eta: ${s.eta ?: "-"}  rem: ${s.remaining ?: "-"}",
                    "offline banner: ${s.offlineBanner}",
                    "last: ${s.lastEvent}",
                )
                lines.forEach { Text(it, color = Color.Green, fontSize = 11.sp, fontFamily = mono) }
            }
        }
    }
}
