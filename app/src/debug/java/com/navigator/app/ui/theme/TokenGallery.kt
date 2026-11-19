package com.navigator.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Debug-only gallery of the design tokens (§3.1-§3.4). Open the previews in Android Studio. */
@Composable
private fun Swatch(name: String, color: Color, on: Color = Color.Unspecified) {
    Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().size(44.dp).background(color, KtmShapes.Control)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, KtmShapes.Control),
            contentAlignment = Alignment.Center,
        ) { if (on != Color.Unspecified) Text("Aa", color = on, style = MaterialTheme.typography.labelLarge) }
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = KtmSpacing.xl), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = KtmTheme.accent.text)
        content()
    }
}

@Composable
private fun TokenGallery() {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(KtmSpacing.edge)) {
            Text("Tokens", style = MaterialTheme.typography.headlineLarge, color = c.fg)
            Section("Neutrals") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Swatch("bg", c.bg); Swatch("surf", c.surf); Swatch("surf2", c.surf2); Swatch("surf3", c.surf3)
                    Swatch("line", c.line); Swatch("line2", c.line2)
                    Swatch("fg", c.fg); Swatch("fg2", c.fg2); Swatch("fg3", c.fg3)
                }
            }
            Section("Status") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Swatch("ok", c.ok, c.onStatus); Swatch("okContainer", c.okContainer, c.fg)
                    Swatch("warn", c.warn, c.onStatus); Swatch("warnContainer", c.warnContainer, c.fg)
                    Swatch("bad", c.bad, c.onStatus); Swatch("badContainer", c.badContainer, c.fg)
                    Swatch("info", c.info, c.onStatus); Swatch("infoContainer", c.infoContainer, c.fg)
                }
            }
            Section("Accent") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Swatch("fill", a.fill, a.onFill); Swatch("text", a.text); Swatch("container", a.container, a.onContainer)
                    Swatch("inverse", a.inverseAccent)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentPalettes.forEach { Swatch(it.displayName, it.tones.fill, it.tones.onFill) }
                }
            }
            Section("Riding") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RidingDay, RidingNight).forEach { r ->
                        Column(Modifier.background(r.surface, KtmShapes.Card).padding(8.dp)) {
                            Box(Modifier.size(width = 120.dp, height = 40.dp).background(r.guidance, KtmShapes.Control), contentAlignment = Alignment.Center) {
                                Text("250 m", color = r.onGuidance, style = MaterialTheme.typography.headlineSmall)
                            }
                            Text("42 km/h", color = r.onSurface, style = MaterialTheme.typography.displaySmall)
                            Text("Overspeed", color = r.onOverspeed, modifier = Modifier.background(r.overspeed, KtmShapes.Badge).padding(4.dp))
                        }
                    }
                }
            }
            Section("Type") {
                val t = MaterialTheme.typography
                val x = KtmTheme.type
                listOf<Pair<String, TextStyle>>(
                    "displayLarge 250 m" to t.displayLarge, "displayMedium 1.2 km" to t.displayMedium,
                    "displaySmall 42 · 6.3" to t.displaySmall, "headlineLarge Rides" to t.headlineLarge,
                    "headlineMedium 17 min" to t.headlineMedium, "headlineSmall Toit Brewpub" to t.headlineSmall,
                    "titleLarge Morning ride" to t.titleLarge, "titleMedium Turn right onto 100 Feet Road" to t.titleMedium,
                    "titleSmall Saved · 4" to t.titleSmall, "bodyLarge Third Wave Coffee" to t.bodyLarge,
                    "bodyMedium Couldn't get a route." to t.bodyMedium, "bodySmall 12th Main Rd, HAL" to t.bodySmall,
                    "labelLarge Start navigation" to t.labelLarge, "labelMedium Fuel · Moving" to t.labelMedium,
                    "labelSmall Thu 19 Nov · km/h" to t.labelSmall, "button.compact Undo" to x.buttonCompact,
                    "riding.label Stop added" to x.ridingLabel, "riding.support 5.9 km · 6:29 pm" to x.ridingSupport,
                    "mono 7C:9E:BD:4F:7F:21" to x.mono,
                ).forEach { (label, style) -> Text(label, style = style, color = c.fg) }
            }
            Section("Radii") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(KtmShapes.Badge, KtmShapes.Control, KtmShapes.Card, KtmShapes.Sheet, KtmShapes.Pill).forEach {
                        Box(Modifier.size(48.dp).background(c.surf3, it))
                    }
                }
            }
            Section("Icons") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KtmIcons.all.forEach { Icon(it, it.name, tint = c.fg, modifier = Modifier.size(24.dp)) }
                }
            }
        }
    }
}

@Preview(name = "Tokens dark", widthDp = 400, heightDp = 2400, showBackground = true)
@Composable
private fun TokenGalleryDark() = KtmTheme(darkTheme = true) { TokenGallery() }

@Preview(name = "Tokens light", widthDp = 400, heightDp = 2400, showBackground = true)
@Composable
private fun TokenGalleryLight() = KtmTheme(darkTheme = false) { TokenGallery() }
