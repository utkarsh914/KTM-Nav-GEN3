package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmCard
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.LargeTitle
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.PillTone
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets
import java.time.Instant
import java.time.ZoneId

class SettingsHomeActions(
    val onOpenPage: (SettingsPage) -> Unit,
    val onRePair: () -> Unit,
    val onQuit: () -> Unit,
)

/**
 * ST1, ST10: the Settings home. A bike card (the wordmark on its own line, so it never clips) that opens Bike & dash, the
 * re-pair banner when the link is lost, eight categories with live summaries (Permissions amber when something needs fixing),
 * and Quit KTM Navigator as the last row, apart and in red. Stateless.
 */
@Composable
fun SettingsHomeContent(ui: SettingsHomeUi, actions: SettingsHomeActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        KtmTopBar()
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .pageInsets(),
        ) {
            LargeTitle(stringResource(R.string.settings_title))
            if (ui.repairSuggested) {
                InfoBanner(
                    message = stringResource(R.string.settings_repair_message), tone = BannerTone.Bad,
                    title = stringResource(R.string.settings_repair_title),
                    action = { KtmButton(stringResource(R.string.settings_repair_action), actions.onRePair, style = ButtonStyle.Danger, size = ButtonSize.Small) },
                    modifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.md),
                )
            }
            BikeCardView(ui.bike, onClick = { actions.onOpenPage(SettingsPage.BIKE_DASH) })
            ui.rows.forEach { row ->
                ListRow(
                    title = stringResource(row.page.title),
                    supporting = row.summary.parts.map { it.resolve() }.joinToString(" · "),
                    leadingIcon = row.page.icon,
                    onClick = { actions.onOpenPage(row.page) },
                    attention = row.summary.attention,
                    trailing = { RowChevron() },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = KtmSpacing.sm), color = c.line)
            ListRow(
                title = stringResource(R.string.settings_quit), supporting = stringResource(R.string.settings_quit_supporting),
                leadingIcon = KtmIcons.Quit, destructive = true,
                onClick = actions.onQuit,
            )
        }
    }
}

@Composable
internal fun BikeCardView(
    bike: BikeCard,
    onClick: (() -> Unit)?,
    /** The big wordmark belongs to the Settings home; the Bike & dash page leaves it out (ST2). */
    wordmark: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
) {
    val c = KtmTheme.colors
    val tone = when (bike.status.tone) {
        PillTone.Connected -> BadgeTone.Ok
        PillTone.Connecting -> BadgeTone.Info
        PillTone.Offline -> BadgeTone.Warn
        PillTone.NotPaired -> BadgeTone.Neutral
    }
    KtmCard(onClick = onClick, modifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.md).fillMaxWidth()) {
        if (wordmark) {
            Text(
                stringResource(R.string.settings_wordmark), style = MaterialTheme.typography.headlineMedium,
                color = KtmTheme.accent.text, maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
            // ST2: the bike in a round tile (green while it is connected), its name, and when it was paired.
            val connected = bike.status.tone == PillTone.Connected
            Box(
                Modifier.size(KtmLayout.badgeTile).background(if (connected) c.okContainer else c.surf3, KtmShapes.Control),
                contentAlignment = Alignment.Center,
            ) { Icon(KtmIcons.Bike, null, tint = if (connected) c.ok else c.fg2) }
            Column(Modifier.weight(1f)) {
                Text(
                    bike.name ?: stringResource(R.string.settings_no_bike), style = MaterialTheme.typography.titleLarge, color = c.fg,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                bike.pairedMs?.let {
                    Text(
                        stringResource(R.string.settings_paired_on, PairedDate.format(it)), style = MaterialTheme.typography.bodySmall,
                        color = c.fg2, maxLines = 1,
                    )
                }
            }
            KtmBadge(stringResource(bike.status.state), tone = tone)
            if (onClick != null) RowChevron()
        }
        footer?.invoke()
    }
}

/** "12 Nov": when the bike was paired, in the rider's locale and zone. */
object PairedDate {
    fun format(pairedMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        ClockFormat.formatter(date = "MMMd").format(Instant.ofEpochMilli(pairedMs).atZone(zone))
}
