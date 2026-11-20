package com.navigator.app.ui.feature.onboarding

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.permissions.PermissionGrants
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.feature.permissions.FixStep
import com.navigator.app.ui.feature.settings.PermissionsUi
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingViewModelTest {
    private lateinit var settings: SettingsRepository
    private var grants = PermissionGrants(false, false, false, false, false, false)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx: Context = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(AppSettings(ctx))
        settings.update { googleNavEnabled = true; userName = null; onboardingComplete = false }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.vm(
        available: Boolean = true, accepted: Boolean = false, saved: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle(),
    ): OnboardingViewModel {
        val perms = PermissionsRepository({ grants }, settings, backgroundScope)
        return OnboardingViewModel(settings, perms, available, accepted, saved).also { it.ui.launchIn(backgroundScope) }
    }

    @Test fun theStepSurvivesTheProcessBeingKilled() = runTest(UnconfinedTestDispatcher()) {
        val handle = androidx.lifecycle.SavedStateHandle()
        val first = vm(saved = handle)
        first.next(); first.chooseEngine(NavEngine.IN_APP); first.next()
        assertEquals(OnboardingStep.TERMS, first.ui.value.step)
        val restored = vm(saved = handle)
        assertEquals(OnboardingStep.TERMS, restored.ui.value.step)
    }

    @Test fun aSavedStepThatIsNoLongerInTheListStartsOver() = runTest(UnconfinedTestDispatcher()) {
        val handle = androidx.lifecycle.SavedStateHandle(mapOf("onboarding_step" to OnboardingStep.TERMS.name))
        assertEquals(OnboardingStep.WELCOME, vm(available = true, accepted = true, saved = handle).ui.value.step)
        assertEquals(OnboardingStep.WELCOME, vm(saved = androidx.lifecycle.SavedStateHandle(mapOf("onboarding_step" to "NOPE"))).ui.value.step)
    }

    @Test fun walksTheStepsWithTermsForInAppAndSavesTheChoices() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        assertEquals(OnboardingStep.WELCOME, vm.ui.value.step)
        assertEquals(null, vm.ui.value.progress)
        vm.next(); assertEquals(OnboardingStep.ENGINE, vm.ui.value.step)
        vm.chooseEngine(NavEngine.IN_APP); vm.next()
        assertEquals(OnboardingStep.TERMS, vm.ui.value.step)
        vm.termsAccepted()
        assertEquals(OnboardingStep.NAME, vm.ui.value.step)
        vm.back()
        assertEquals(OnboardingStep.ENGINE, vm.ui.value.step)
        vm.next()
        assertEquals(OnboardingStep.NAME, vm.ui.value.step)
        vm.setName("  Alex "); vm.next()
        assertEquals("Alex", settings.snapshot.value.userName)
        assertEquals(OnboardingStep.PERMISSIONS, vm.ui.value.step)
    }

    @Test fun usingAnotherAppInsteadOfTheTermsContinuesInMirrorMode() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        vm.next(); vm.chooseEngine(NavEngine.IN_APP); vm.next()
        vm.useMirror()
        assertEquals(OnboardingStep.NAME, vm.ui.value.step)
        assertEquals(NavEngine.MIRROR, vm.ui.value.engine)
        assertEquals(NavEngine.MIRROR, settings.snapshot.value.engine)
    }

    @Test fun mirrorSkipsTermsAndNoEngineChoiceSkipsTheEngineStep() = runTest(UnconfinedTestDispatcher()) {
        val mirror = vm()
        mirror.next(); mirror.chooseEngine(NavEngine.MIRROR); mirror.next()
        assertEquals(OnboardingStep.NAME, mirror.ui.value.step)
        val none = vm(available = false)
        none.next()
        assertEquals(OnboardingStep.NAME, none.ui.value.step)
    }

    @Test fun skippingTheNameSavesNothing() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm(available = false)
        vm.next(); vm.setName("Typed"); vm.skipName()
        assertEquals(OnboardingStep.PERMISSIONS, vm.ui.value.step)
        assertTrue(settings.snapshot.value.userName.isNullOrEmpty())
    }

    @Test fun theNameIsCapped() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        vm.setName("x".repeat(100))
        assertEquals(OnboardingViewModel.MAX_NAME, vm.ui.value.name.length)
    }

    @Test fun backReturnsAndStopsAtTheWelcome() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        vm.next(); vm.back(); vm.back()
        assertEquals(OnboardingStep.WELCOME, vm.ui.value.step)
        assertFalse(vm.ui.value.canGoBack)
    }

    @Test fun finishingMarksOnboardingDoneAndLeaves() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm(available = false)
        vm.next(); vm.next(); vm.next()
        assertEquals(OnboardingEvent.Finished, vm.events.first())
        assertTrue(settings.snapshot.value.onboardingComplete)
    }

    @Test fun theAllowButtonFixesRequiredOnesInOrder() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        val first = vm.nextPermissionStep() as FixStep.Request
        assertTrue(first.permissions.any { it.contains("BLUETOOTH") })
        grants = PermissionGrants(true, true, true, true, true, true)
        vm.refreshPermissions()
        assertEquals(null, vm.nextPermissionStep())
    }

    @Test fun readyUiNamesTheBikeAndFallsBackToMirrorWithoutGoogle() {
        val snap = settings.snapshot.value
        val ui = ReadyUi.of(snap, BikeStatus.Connected("KTM 390"), engineAvailable = false)
        assertEquals("KTM 390", ui.bikeName)
        assertTrue(ui.connected)
        assertEquals(NavEngine.MIRROR, ui.engine)
        assertFalse(ReadyUi.of(snap, BikeStatus.NotPaired, true).connected)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h900dp")
class OnboardingContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun actions() = OnboardingActions(
        onBack = { log += "back" }, onNext = { log += "next" }, onSkipName = { log += "skip" }, onEngine = { log += "engine $it" },
        onName = { log += "name $it" }, onReviewTerms = { log += "terms" }, onUseMirror = { log += "mirror" },
        onFix = { log += "fix ${it.name}" }, onAllowRequired = { log += "allow" },
    )

    private fun show(step: OnboardingStep, engine: NavEngine = NavEngine.IN_APP, name: String = "", terms: TermsUi = TermsUi(), missing: Boolean = true) {
        val grants = if (missing) PermissionGrants(false, true, true, true, true, true) else PermissionGrants(true, true, true, true, true, true)
        val perms = PermissionsUi.of(com.navigator.app.data.permissions.PermissionPolicy.build(grants, NavEngine.MIRROR))
        val ui = OnboardingUi(step, engine, name, progress = if (step == OnboardingStep.WELCOME) null else 2 to 4, canGoBack = step != OnboardingStep.WELCOME, permissions = perms)
        rule.setContent { KtmTheme(darkTheme = false) { OnboardingContent(ui, terms, actions(), reducedMotion = true) } }
    }

    @Test fun welcomeShowsThePromisesAndGetStarted() {
        show(OnboardingStep.WELCOME)
        rule.onNodeWithText("Turns on your dash").assertIsDisplayed()
        rule.onNode(hasText("Get started") and hasClickAction()).performClick()
        assertEquals(listOf("next"), log)
    }

    @Test fun engineStepSelectsEitherChoice() {
        show(OnboardingStep.ENGINE)
        rule.onNodeWithText("Use another navigation app").performScrollTo().performClick()
        assertEquals(listOf("engine MIRROR"), log)
    }

    @Test fun termsStepOffersReviewAndMirror() {
        show(OnboardingStep.TERMS)
        rule.onNode(hasText("Review the terms") and hasClickAction()).performClick()
        rule.onNodeWithText("Use another navigation app instead").performClick()
        assertEquals(listOf("terms", "mirror"), log)
    }

    @Test fun aDeclinedTermsSaysWhyAndOffersAnotherTry() {
        show(OnboardingStep.TERMS, terms = TermsUi(declined = true))
        rule.onNodeWithText("In-app navigation can't be used without accepting the terms.").assertIsDisplayed()
        rule.onNodeWithText("Review the terms again").assertIsDisplayed()
    }

    @Test fun nameStepPreviewsTheGreetingAndCanBeSkipped() {
        show(OnboardingStep.NAME, name = "Alex")
        rule.onNodeWithText("Hi Alex!").assertIsDisplayed()
        rule.onNode(hasText("Skip") and hasClickAction()).performClick()
        assertEquals(listOf("skip"), log)
    }

    @Test fun anEmptyNameGreetsWelcome() {
        show(OnboardingStep.NAME)
        rule.onNodeWithText("Welcome!").assertIsDisplayed()
    }

    @Test fun permissionsStepAllowsRequiredOrCarriesOn() {
        show(OnboardingStep.PERMISSIONS)
        rule.onNode(hasText("Allow required") and hasClickAction()).performClick()
        rule.onNode(hasText("Not now") and hasClickAction()).performClick()
        assertEquals(listOf("allow", "next"), log)
    }

    @Test fun withEverythingAllowedItJustContinues() {
        show(OnboardingStep.PERMISSIONS, missing = false)
        rule.onNodeWithText("Allow required").assertDoesNotExist()
        rule.onNode(hasText("Continue") and hasClickAction()).performClick()
        assertEquals(listOf("next"), log)
    }

    @Test fun backIsOfferedAfterTheWelcomeOnly() {
        show(OnboardingStep.ENGINE)
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), log)
    }
}
