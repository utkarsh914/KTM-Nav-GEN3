package com.navigator.app.ui.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
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
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmRadioRow
import com.navigator.app.ui.components.KtmTextField
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.feature.permissions.PermissionRow
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

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
    Row(
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .fillMaxWidth().height(KtmLayout.topBar).padding(horizontal = KtmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.xs),
    ) {
        if (ui.canGoBack) {
            KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
        } else {
            Spacer(Modifier.size(KtmLayout.targetRest))
        }
        val progress = ui.progress
        if (progress != null) {
            ProgressBar(progress.first, progress.second, Modifier.weight(1f))
        } else {
            Text(
                stringResource(R.string.app_wordmark), style = MaterialTheme.typography.titleMedium, color = KtmTheme.colors.fg,
                modifier = Modifier.weight(1f),
            )
        }
        if (ui.step == OnboardingStep.NAME) {
            KtmButton(stringResource(R.string.onb_skip), actions.onSkipName, style = ButtonStyle.Text, size = ButtonSize.Small)
        } else {
            Spacer(Modifier.size(KtmLayout.targetRest))
        }
    }
}

@Composable
private fun ProgressBar(position: Int, total: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onb_progress, position, total)
    Row(modifier.semantics { contentDescription = description; progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 1f..total.toFloat(), total - 1) }, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).height(KtmSpacing.xs)
                    .background(if (i < position) KtmTheme.accent.fill else KtmTheme.colors.line2, KtmShapes.Pill),
            )
        }
    }
}

/** A step: scrolling content, then its actions pinned at the bottom so they never hide on a short screen. */
@Composable
private fun StepFrame(
    title: String, message: String? = null, actions: @Composable ColumnScope.() -> Unit, body: @Composable ColumnScope.() -> Unit,
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
                    message, style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg2,
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
        title = stringResource(R.string.onb_welcome_title),
        actions = { KtmButton(stringResource(R.string.onb_get_started), actions.onNext, Modifier.fillMaxWidth()) },
    ) {
        DashPreview(
            DashPreviewState.Turn(
                rememberDashTime(), TurnIcon.QUITE_RIGHT, stringResource(R.string.onb_demo_distance), stringResource(R.string.onb_demo_road),
                stringResource(R.string.onb_demo_eta), stringResource(R.string.onb_demo_remaining),
            ),
            Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.lg),
        )
        ListRow(
            title = stringResource(R.string.onb_promise_dash), supporting = stringResource(R.string.onb_promise_dash_hint),
            supportingLines = 2, leadingIcon = KtmIcons.Navigation,
        )
        ListRow(
            title = stringResource(R.string.onb_promise_messages), supporting = stringResource(R.string.onb_promise_messages_hint),
            supportingLines = 2, leadingIcon = KtmIcons.Message,
        )
        ListRow(
            title = stringResource(R.string.onb_promise_rides), supporting = stringResource(R.string.onb_promise_rides_hint),
            supportingLines = 2, leadingIcon = KtmIcons.Route,
        )
    }
}

@Composable
private fun Engine(engine: NavEngine, actions: OnboardingActions) {
    StepFrame(
        title = stringResource(R.string.onb_engine_title), message = stringResource(R.string.onb_engine_message),
        actions = { KtmButton(stringResource(R.string.common_continue), actions.onNext, Modifier.fillMaxWidth()) },
    ) {
        KtmRadioRow(
            stringResource(R.string.onb_engine_inapp), engine == NavEngine.IN_APP, { actions.onEngine(NavEngine.IN_APP) },
            supporting = stringResource(R.string.onb_engine_inapp_hint), supportingLines = 4,
        )
        KtmRadioRow(
            stringResource(R.string.onb_engine_mirror), engine == NavEngine.MIRROR, { actions.onEngine(NavEngine.MIRROR) },
            supporting = stringResource(R.string.onb_engine_mirror_hint), supportingLines = 4,
        )
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
        Spacer(Modifier.height(KtmSpacing.lg))
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
        title = stringResource(R.string.onb_perm_title), message = stringResource(R.string.onb_perm_message),
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
