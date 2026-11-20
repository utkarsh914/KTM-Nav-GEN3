package com.navigator.app.ui.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.ui.feature.navigate.findHostActivity
import com.navigator.app.ui.feature.permissions.rememberPermissionFixer
import com.navigator.app.ui.theme.KtmTheme

/** First run: [OnboardingContent] over its view model, with the permission requests and Google's terms dialog. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(context.appContainer, context))
    val ui by vm.ui.collectAsState()
    // Not saved: Google's dialog is gone after the process dies, so a restored flag would never clear.
    var declined by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf(false) }
    val fixer = rememberPermissionFixer(onResult = vm::refreshPermissions)
    val appName = stringResource(R.string.app_wordmark)
    val colors = KtmTheme.colors

    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(vm) { vm.refreshPermissions(); vm.events.collect { if (it == OnboardingEvent.Finished) finished() } }
    BackHandler(enabled = ui.canGoBack && ui.step != OnboardingStep.WELCOME) { vm.back() }

    OnboardingContent(
        ui, TermsUi(declined, showing),
        OnboardingActions(
            onBack = vm::back, onNext = vm::next, onSkipName = vm::skipName, onEngine = vm::chooseEngine, onName = vm::setName,
            onReviewTerms = {
                val activity = context.findHostActivity()
                if (activity != null && !showing) {
                    showing = true
                    GoogleTerms.show(activity, appName, colors) { accepted ->
                        showing = false
                        declined = !accepted
                        if (accepted) vm.termsAccepted()
                    }
                }
            },
            onUseMirror = vm::useMirror,
            onFix = { id -> fixer.fix(vm.stepFor(id)) },
            onAllowRequired = { vm.nextPermissionStep()?.let(fixer::fix) },
        ),
        reducedMotion = KtmTheme.motion.reduced,
        modifier = modifier,
    )
    fixer.Sheet()
}
