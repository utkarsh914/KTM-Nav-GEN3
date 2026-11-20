package com.navigator.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.AccentScheme
import com.navigator.app.ui.theme.KtmColors
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** Visual style of a [KtmButton]. One primary action per screen; destructive actions use a danger style, never the accent. */
enum class ButtonStyle { Primary, Tonal, Neutral, Outline, Text, Danger, DangerTonal }

/** Heights are touch targets: 48 at rest, 56 primary at rest, 64 riding, 72 for stacked icon-over-label tiles, 84 for the trip sheet's. */
enum class ButtonSize(val height: Dp) {
    Small(KtmLayout.targetRest),
    Rest(KtmLayout.targetPrimary),
    Riding(KtmLayout.targetRiding),
    Tile(72.dp),
    /** The trip sheet's big stacked tiles (Add stop, Overview, Minimise). */
    TileLarge(84.dp);

    val stacked: Boolean get() = this == Tile || this == TileLarge
}

/** Container, content and outline colours of a button in one state, resolved from the theme tokens. */
data class ButtonPalette(val container: Color, val content: Color, val border: Color?)

fun buttonPalette(style: ButtonStyle, enabled: Boolean, c: KtmColors, a: AccentScheme): ButtonPalette {
    if (!enabled) {
        return when (style) {
            ButtonStyle.Outline -> ButtonPalette(Color.Transparent, c.fg3, c.line)
            ButtonStyle.Text -> ButtonPalette(Color.Transparent, c.fg3, null)
            else -> ButtonPalette(c.surf3, c.fg3, null)
        }
    }
    return when (style) {
        ButtonStyle.Primary -> ButtonPalette(a.fill, a.onFill, null)
        ButtonStyle.Tonal -> ButtonPalette(a.container, a.onContainer, null)
        ButtonStyle.Neutral -> ButtonPalette(c.surf3, c.fg, null)
        ButtonStyle.Outline -> ButtonPalette(Color.Transparent, c.fg, c.line2)
        ButtonStyle.Text -> ButtonPalette(Color.Transparent, a.text, null)
        ButtonStyle.Danger -> ButtonPalette(c.bad, c.onStatus, null)
        ButtonStyle.DangerTonal -> ButtonPalette(c.badContainer, c.badText, null)
    }
}

/**
 * The one button (replaces KtmPrimaryButton / KtmOutlineButton / ad-hoc boxes). [loading] shows a
 * spinner in place of the icon and ignores taps: spinners live only inside buttons.
 * Haptics: Primary confirms, danger styles reject, the rest tick.
 */
@Composable
fun KtmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    size: ButtonSize = ButtonSize.Rest,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val palette = buttonPalette(style, enabled, KtmTheme.colors, KtmTheme.accent)
    val loadingText = androidx.compose.ui.res.stringResource(com.navigator.app.R.string.common_loading)
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape: Shape = if (size == ButtonSize.Riding || size.stacked) KtmShapes.Card else KtmShapes.Control
    val label = when (size) {
        ButtonSize.Small -> KtmTheme.type.buttonCompact
        ButtonSize.Tile, ButtonSize.TileLarge -> KtmTheme.type.buttonCompact
        else -> MaterialTheme.typography.labelLarge
    }
    val active = enabled && !loading
    val stacked = size.stacked
    val pressed by source.collectIsPressedAsState()
    val reduced = KtmTheme.motion.reduced
    val scale by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, KtmTheme.motion.effectsFast(), label = "press")
    val ringColor = if (style == ButtonStyle.Primary) KtmTheme.colors.fg else KtmTheme.accent.text
    val outline = palette.border?.let { BorderStroke(1.5.dp, it) }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = size.height)
            .height(size.height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                // Focus ring: 3 dp, 3 dp outside the button.
                if (focused) {
                    val gap = 3.dp.toPx()
                    val r = if (shape === KtmShapes.Card) 16.dp.toPx() else 12.dp.toPx()
                    drawRoundRect(
                        ringColor, Offset(-gap - 1.5.dp.toPx(), -gap - 1.5.dp.toPx()),
                        Size(this.size.width + 2 * (gap + 1.5.dp.toPx()), this.size.height + 2 * (gap + 1.5.dp.toPx())),
                        CornerRadius(r + gap), style = Stroke(3.dp.toPx()),
                    )
                }
            }
            .clip(shape)
            .background(palette.container)
            .let { if (outline != null) it.border(outline, shape) else it }
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(color = palette.content),
                enabled = active,
                role = Role.Button,
            ) {
                when (style) {
                    ButtonStyle.Primary -> haptics.confirm()
                    ButtonStyle.Danger, ButtonStyle.DangerTonal -> haptics.reject()
                    else -> haptics.tap()
                }
                onClick()
            }
            .semantics { if (loading) stateDescription = loadingText },
        contentAlignment = Alignment.Center,
    ) {
        val content: @Composable () -> Unit = {
            val lead: @Composable () -> Unit = {
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = palette.content,
                    )
                    icon != null -> Icon(icon, null, tint = palette.content, modifier = Modifier.size(if (stacked) 24.dp else 22.dp))
                }
            }
            lead()
            Text(text, style = label, color = palette.content, maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        val pad = PaddingValues(horizontal = if (style == ButtonStyle.Text) KtmSpacing.md else if (size == ButtonSize.Small) KtmSpacing.lg else 20.dp)
        if (stacked) {
            Column(
                Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm, Alignment.CenterVertically),
            ) { content() }
        } else {
            Row(
                Modifier.padding(pad), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm, Alignment.CenterHorizontally),
            ) { content() }
        }
    }
}

/** String-resource convenience. */
@Composable
fun KtmButton(
    @StringRes text: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    size: ButtonSize = ButtonSize.Rest,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) = KtmButton(androidx.compose.ui.res.stringResource(text), onClick, modifier, style, size, enabled, loading, icon)
