package com.navigator.app.ui.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.nav.ktm.EtaFormatter
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DashPreview
import com.navigator.app.ui.components.DashPreviewState
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmRadio
import com.navigator.app.ui.components.KtmStepHeader
import com.navigator.app.ui.components.KtmTextField
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.components.TonalIconBadge
import com.navigator.app.ui.feature.permissions.PermissionRow
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

class OnboardingActions(
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onSkipName: () -> Unit,
    val onEngine: (NavEngine) -> Unit,
    val onName: (String) -> Unit,
    val onReviewTerms: () -> Unit,
    val onUseMirror: () -> Unit,
    val onFix: (PermissionId) -> Unit,
    val onAllowRequired: () -> Unit,
)

/** Extra facts only the Android side knows: whether Google's dialog was declined or is open. */
data class TermsUi(val declined: Boolean = false, val showing: Boolean = false)

/** ON1 to ON5 in one frame: progress and Back on top, one step below, sliding sideways between them. Stateless. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingContent(
    ui: OnboardingUi, terms: TermsUi, actions: OnboardingActions, reducedMotion: Boolean, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(KtmTheme.colors.bg)) {
        Header(ui, actions)
        AnimatedContent(
            targetState = ui.step,
            transitionSpec = {
                if (reducedMotion) fadeIn().togetherWith(fadeOut())
                else {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally { w -> dir * w / 6 } + fadeIn()).togetherWith(slideOutHorizontally { w -> -dir * w / 6 } + fadeOut())
                }
            },
            label = "onboarding-step",
            modifier = Modifier.weight(1f),
        ) { step ->
            Column(Modifier.fillMaxSize()) {
                when (step) {
                    OnboardingStep.WELCOME -> Welcome(actions)
                    OnboardingStep.ENGINE -> Engine(ui.engine, actions)
                    OnboardingStep.TERMS -> Terms(terms, actions)
                    OnboardingStep.NAME -> Name(ui.name, actions)
                    OnboardingStep.PERMISSIONS -> Permissions(ui, actions)
                }
            }
        }
    }
}

@Composable
private fun Header(ui: OnboardingUi, actions: OnboardingActions) {
    val progress = ui.progress
    Column(Modifier.then(if (progress == null) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) else Modifier).fillMaxWidth()) {
        if (progress == null) {
            // ON1: the wordmark, no progress yet.
            Row(
                Modifier.fillMaxWidth().height(KtmLayout.topBar).padding(horizontal = KtmSpacing.edge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_wordmark), style = MaterialTheme.typography.titleLarge, color = KtmTheme.colors.fg, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.onb_product), style = MaterialTheme.typography.labelLarge, color = KtmTheme.colors.fg2)
            }
        } else {
            // ON2 to ON5: the bar on top, Back and Skip on their own row under it.
            KtmStepHeader(progress.first, progress.second, actions.onBack.takeIf { ui.canGoBack }) {
                if (ui.step == OnboardingStep.NAME) {
                    KtmButton(stringResource(R.string.onb_skip), actions.onSkipName, style = ButtonStyle.Text, size = ButtonSize.Small)
                }
            }
        }
    }
}

/** A step: scrolling content, then its actions pinned at the bottom so they never hide on a short screen. */
@Composable
private fun StepFrame(
    title: String, message: String? = null, actions: @Composable ColumnScope.() -> Unit, smallMessage: Boolean = false,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                title, style = MaterialTheme.typography.headlineLarge, color = KtmTheme.colors.fg,
                modifier = Modifier.semantics { heading() }.padding(horizontal = KtmSpacing.edge).padding(top = KtmSpacing.sm, bottom = KtmSpacing.sm),
            )
            if (message != null) {
                Text(
                    message, style = if (smallMessage) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg2,
                    modifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.lg),
                )
            }
            body()
        }
        Column(Modifier.padding(KtmSpacing.edge), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm), content = actions)
    }
}

@Composable
private fun rememberDashTime(): String = remember { EtaFormatter.format(System.currentTimeMillis()) }

@Composable
private fun Welcome(actions: OnboardingActions) {
    StepFrame(
        title = stringResource(R.string.onb_welcome_title), message = stringResource(R.string.onb_welcome_message),
        actions = { KtmButton(stringResource(R.string.common_continue), actions.onNext, Modifier.fillMaxWidth()) },
    ) {
        DashPreview(
            DashPreviewState.Turn(
                rememberDashTime(), TurnIcon.QUITE_RIGHT, stringResource(R.string.onb_demo_distance), stringResource(R.string.onb_demo_road),
                stringResource(R.string.onb_demo_eta), stringResource(R.string.onb_demo_remaining),
            ),
            Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.lg),
        )
        // One line each on a 40 dp rhythm: they promise, they are not rows to tap.
        Promise(KtmIcons.Navigation, R.string.onb_promise_dash)
        Promise(KtmIcons.Message, R.string.onb_promise_messages)
        Promise(KtmIcons.Route, R.string.onb_promise_rides)
    }
}

@Composable
private fun Promise(icon: androidx.compose.ui.graphics.vector.ImageVector, @androidx.annotation.StringRes text: Int) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = KtmLayout.promiseRow).padding(horizontal = KtmSpacing.edge),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        TonalIconBadge(icon)
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg)
    }
}

@Composable
private fun Engine(engine: NavEngine, actions: OnboardingActions) {
    StepFrame(
        title = stringResource(R.string.onb_engine_title), message = stringResource(R.string.onb_engine_message),
        actions = { KtmButton(stringResource(R.string.common_continue), actions.onNext, Modifier.fillMaxWidth()) },
    ) {
        Column(Modifier.padding(horizontal = KtmSpacing.edge), verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
            EngineCard(
                KtmIcons.Route, R.string.onb_engine_inapp, R.string.onb_engine_inapp_tag, R.string.onb_engine_inapp_hint,
                R.string.onb_engine_inapp_note, engine == NavEngine.IN_APP,
            ) { actions.onEngine(NavEngine.IN_APP) }
            EngineCard(
                KtmIcons.Navigation, R.string.onb_engine_mirror, R.string.onb_engine_mirror_tag, R.string.onb_engine_mirror_hint,
                null, engine == NavEngine.MIRROR,
            ) { actions.onEngine(NavEngine.MIRROR) }
        }
    }
}

/** ON2: a choice as a card: what it is called, a line on it, how it works, and a radio. The chosen one is outlined in the accent. */
@Composable
private fun EngineCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    @androidx.annotation.StringRes title: Int,
    @androidx.annotation.StringRes tag: Int,
    @androidx.annotation.StringRes body: Int,
    @androidx.annotation.StringRes note: Int?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    Column(
        Modifier.fillMaxWidth().clip(KtmShapes.Card)
            .background(if (selected) a.container else c.surf)
            .border(if (selected) KtmLayout.selectedOutline else KtmLayout.hairline, if (selected) a.fill else c.line, KtmShapes.Card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { haptics.toggle(true); onSelect() })
            .padding(KtmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
            Box(
                Modifier.size(KtmLayout.actionTile).background(if (selected) a.fill else c.surf3, KtmShapes.Control),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (selected) a.onFill else c.fg2) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = c.fg)
                Text(stringResource(tag), style = MaterialTheme.typography.bodySmall, color = if (selected) a.onContainer else c.fg2)
            }
            KtmRadio(selected)
        }
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.fg)
        if (note != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                Icon(KtmIcons.Info, null, tint = c.fg2, modifier = Modifier.size(KtmSpacing.lg))
                Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = c.fg2)
            }
        }
    }
}

@Composable
private fun Terms(terms: TermsUi, actions: OnboardingActions) {
    StepFrame(
        title = stringResource(R.string.onb_terms_title), message = stringResource(R.string.onb_terms_message),
        actions = {
            KtmButton(
                stringResource(if (terms.declined) R.string.onb_terms_again else R.string.onb_terms_review), actions.onReviewTerms,
                Modifier.fillMaxWidth(), loading = terms.showing,
            )
            KtmButton(stringResource(R.string.onb_terms_mirror), actions.onUseMirror, Modifier.fillMaxWidth(), style = ButtonStyle.Text)
        },
    ) {
        if (terms.declined) {
            InfoBanner(
                stringResource(R.string.onb_terms_declined), Modifier.padding(horizontal = KtmSpacing.edge), tone = BannerTone.Warn,
            )
        }
    }
}

@Composable
private fun Name(name: String, actions: OnboardingActions) {
    val greeting = if (name.isBlank()) stringResource(R.string.onb_greeting_blank) else stringResource(R.string.onb_greeting, name.trim())
    StepFrame(
        title = stringResource(R.string.onb_name_title), message = stringResource(R.string.onb_name_message),
        actions = { KtmButton(stringResource(R.string.common_continue), actions.onNext, Modifier.fillMaxWidth()) },
    ) {
        KtmTextField(
            name, actions.onName, label = stringResource(R.string.onb_name_label), placeholder = stringResource(R.string.onb_name_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = KtmSpacing.edge),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.onNext() }),
        )
        SectionHeader(stringResource(R.string.onb_preview))
        DashPreview(
            DashPreviewState.Message(rememberDashTime(), stringResource(R.string.dash_preview_banner_app), greeting),
            Modifier.padding(horizontal = KtmSpacing.edge),
        )
    }
}

@Composable
private fun Permissions(ui: OnboardingUi, actions: OnboardingActions) {
    val missing = ui.permissions.requiredMissing.isNotEmpty()
    StepFrame(
        title = stringResource(R.string.onb_perm_title),
        message = stringResource(R.string.onb_perm_message, ui.permissions.items.count { it.required }),
        smallMessage = true,
        actions = {
            if (missing) {
                KtmButton(stringResource(R.string.onb_perm_allow), actions.onAllowRequired, Modifier.fillMaxWidth())
                KtmButton(stringResource(R.string.onb_perm_later), actions.onNext, Modifier.fillMaxWidth(), style = ButtonStyle.Text)
            } else {
                KtmButton(stringResource(R.string.common_continue), actions.onNext, Modifier.fillMaxWidth())
            }
        },
    ) {
        ui.permissions.items.forEach { PermissionRow(it, onFix = { actions.onFix(it.id) }) }
    }
}
