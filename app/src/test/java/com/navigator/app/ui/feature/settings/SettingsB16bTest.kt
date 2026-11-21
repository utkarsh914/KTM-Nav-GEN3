package com.navigator.app.ui.feature.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.navigator.app.R
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingSpecs
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.nav.model.TravelMode
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.settings.AppSettings
import com.navigator.app.settings.DrivingSidePreference
import com.navigator.app.ui.components.Hsv
import com.navigator.app.ui.feature.settings.picker.AppEntry
import com.navigator.app.ui.feature.settings.picker.NavAppPickerContent
import com.navigator.app.ui.feature.settings.picker.NavAppPickerUi
import com.navigator.app.ui.feature.settings.picker.NavAppPickerViewModel
import com.navigator.app.ui.theme.AccentPalettes
import com.navigator.app.ui.theme.FontMode
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.NavThemeMode
import com.navigator.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class SettingsB16bViewModelsTest {
    private lateinit var settings: SettingsRepository
    private val switched = mutableListOf<NavEngine>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx: Context = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(AppSettings(ctx))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun nav(available: Boolean = true) = NavigationViewModel(settings, available, { "label:$it" }, { switched += it })

    @Test fun switchingEngineWritesItAndTellsTheTripLayerOnlyOnAChange() = runTest(UnconfinedTestDispatcher()) {
        settings.update { googleNavEnabled = true }
        val vm = nav().also { it.ui.launchIn(backgroundScope) }
        vm.setEngine(NavEngine.IN_APP)
        assertTrue("no change, no callback", switched.isEmpty())
        vm.setEngine(NavEngine.MIRROR)
        assertEquals(NavEngine.MIRROR, vm.ui.value.engine)
        assertFalse(settings.snapshot.value.engine == NavEngine.IN_APP)
        assertEquals(listOf(NavEngine.MIRROR), switched)
    }

    @Test fun withoutTheSdkTheEngineIsMirrorAndCannotBeChanged() = runTest(UnconfinedTestDispatcher()) {
        settings.update { googleNavEnabled = true }
        val vm = nav(available = false).also { it.ui.launchIn(backgroundScope) }
        assertFalse(vm.ui.value.engineAvailable)
        assertEquals(NavEngine.MIRROR, vm.ui.value.engine)
        vm.setEngine(NavEngine.MIRROR)
        assertTrue(settings.snapshot.value.engine == NavEngine.IN_APP)
        assertTrue(switched.isEmpty())
    }

    @Test fun navigationWritesItsSettings() = runTest(UnconfinedTestDispatcher()) {
        val vm = nav().also { it.ui.launchIn(backgroundScope) }
        vm.setVehicle(TravelMode.DRIVING); vm.setMarker(AppSettings.LOCATION_MARKER_BEAM); vm.setLiveUpdates(false); vm.setDrivingSide(DrivingSidePreference.LEFT)
        with(vm.ui.value) {
            assertEquals(TravelMode.DRIVING, vehicle); assertEquals(AppSettings.LOCATION_MARKER_BEAM, marker)
            assertFalse(liveUpdates); assertEquals(DrivingSidePreference.LEFT, drivingSide)
        }
    }

    @Test fun theNavAppIsAutoUntilOneIsChosenAndAutoClearsIt() = runTest(UnconfinedTestDispatcher()) {
        settings.update { navAppOverride = null }
        val vm = nav().also { it.ui.launchIn(backgroundScope) }
        assertTrue(vm.ui.value.autoDetect); assertNull(vm.ui.value.navAppLabel)
        settings.update { navAppOverride = "com.waze" }
        assertFalse(vm.ui.value.autoDetect); assertEquals("label:com.waze", vm.ui.value.navAppLabel)
        vm.detectNavApp()
        assertNull(settings.snapshot.value.navAppOverride)
    }

    @Test fun choosingANavAppSavesItAndListsTheKnownApps() = runTest(UnconfinedTestDispatcher()) {
        val vm = NavAppPickerViewModel(settings) { "L-$it" }.also { it.ui.launchIn(backgroundScope) }
        assertEquals(AppSettings.KNOWN_NAV_APPS.size, vm.ui.value.apps.size)
        assertEquals(vm.ui.value.apps.map { it.label }.sortedBy { it.lowercase() }, vm.ui.value.apps.map { it.label })
        vm.choose("com.waze")
        assertEquals("com.waze", vm.ui.value.selected)
    }

    @Test fun recordingFieldsAreKeptInTheirRanges() = runTest(UnconfinedTestDispatcher()) {
        val vm = RecordingViewModel(settings).also { it.ui.launchIn(backgroundScope) }
        RecordingField.entries.forEach { f ->
            vm.set(f, Int.MAX_VALUE); assertEquals(f.name, f.spec.max, vm.ui.value.valueOf(f))
            vm.set(f, Int.MIN_VALUE); assertEquals(f.name, f.spec.min, vm.ui.value.valueOf(f))
        }
    }

    @Test fun recordingKeepsItsOtherSettingsWhenOneChanges() = runTest(UnconfinedTestDispatcher()) {
        val vm = RecordingViewModel(settings).also { it.ui.launchIn(backgroundScope) }
        vm.setRecording(false); vm.setAvgMode(AvgSpeedMode.OVERALL); vm.set(RecordingField.HISTORY, 50)
        with(vm.ui.value) { assertFalse(recording); assertEquals(AvgSpeedMode.OVERALL, avgMode); assertEquals(50, history) }
        vm.set(RecordingField.MIN_DISTANCE, 300)
        assertEquals(50, vm.ui.value.history)
        assertEquals(300, vm.ui.value.minDistanceM)
    }

    @Test fun loweringTheHistoryLimitPrunesOnceWhenThePageIsLeft() = runTest(UnconfinedTestDispatcher()) {
        settings.update { rideHistoryLimit = 100 }
        val pruned = mutableListOf<Int>()
        val vm = RecordingViewModel(settings) { pruned += it }.also { it.ui.launchIn(backgroundScope) }
        vm.set(RecordingField.HISTORY, 90); vm.set(RecordingField.HISTORY, 80)
        assertTrue("no prune while stepping", pruned.isEmpty())
        vm.commit()
        assertEquals(listOf(80), pruned)
    }

    @Test fun raisingOrKeepingTheLimitPrunesNothing() = runTest(UnconfinedTestDispatcher()) {
        settings.update { rideHistoryLimit = 50 }
        val pruned = mutableListOf<Int>()
        val vm = RecordingViewModel(settings) { pruned += it }.also { it.ui.launchIn(backgroundScope) }
        vm.commit(); vm.set(RecordingField.HISTORY, 100); vm.commit()
        assertTrue(pruned.isEmpty())
    }

    @Test fun appearanceAppliesEachChoice() = runTest(UnconfinedTestDispatcher()) {
        val vm = AppearanceViewModel(settings).also { it.ui.launchIn(backgroundScope) }
        vm.setTheme(ThemeMode.DARK); vm.setRidingScreens(NavThemeMode.APP); vm.setFont(FontMode.SYSTEM); vm.setAccent("blue")
        assertEquals(AppearanceUi(ThemeMode.DARK, NavThemeMode.APP, FontMode.SYSTEM, "blue"), vm.ui.value)
        vm.setAccent(null)
        assertNull(vm.ui.value.accentId)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccentSupportTest {
    @Test fun everyPresetHasAName() {
        AccentPalettes.forEach { assertTrue(it.id, AccentNames.res(it.id) != R.string.accent_custom) }
        assertEquals(R.string.accent_orange, AccentNames.res(null))
        assertEquals(R.string.accent_custom, AccentNames.res("#123456"))
    }

    @Test fun onlyAHexIdIsCustom() {
        assertFalse(AccentNames.isCustom(null)); assertFalse(AccentNames.isCustom("teal")); assertFalse(AccentNames.isCustom("junk"))
        assertTrue(AccentNames.isCustom("#12AB34"))
    }

    @Test fun aColourCloseToTheBackgroundIsAdjusted() {
        val lightBg = Color.White
        assertTrue(AccentCheck.of(Color(0xFFFFF59D), lightBg, dark = false).adjusted)
        assertFalse(AccentCheck.of(Color(0xFF0B3D91), lightBg, dark = false).adjusted)
    }

    @Test fun adjustedTextIsStillReadable() {
        listOf(0xFFFFF59D, 0xFF101030, 0xFFFF6A00, 0xFF22C55E).forEach { seed ->
            listOf(Color.White to false, Color(0xFF121212) to true).forEach { (bg, dark) ->
                assertTrue("$seed on $dark", AccentCheck.of(Color(seed), bg, dark).textContrast >= 3.0)
            }
        }
    }

    @Test fun hexInputTakesSixDigitsWithOrWithoutAHash() {
        assertEquals(Color(0xFFFF6A00), HexInput.parse("FF6A00"))
        assertEquals(Color(0xFFFF6A00), HexInput.parse("#ff6a00"))
        assertNull(HexInput.parse("FF6A0")); assertNull(HexInput.parse("")); assertNull(HexInput.parse("GG0000"))
        assertEquals("FF6A00", HexInput.clean("#ff-6a00zz99"))
    }

    @Test fun hsvRoundTripsAColourWithinARounding() {
        val back = Hsv.of(Color(0xFF3B82F6)).toColor()
        listOf(back.red to 0x3B, back.green to 0x82, back.blue to 0xF6).forEach { (got, want) ->
            assertEquals(want.toDouble(), got * 255.0, 1.5)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h900dp")
class SettingsB16bContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private val navUi = NavigationUi(true, NavEngine.MIRROR, TravelMode.TWO_WHEELER, AppSettings.LOCATION_MARKER_CHEVRON, true, true, null, DrivingSidePreference.AUTO)

    private fun navigation(ui: NavigationUi) = rule.setContent {
        KtmTheme(darkTheme = false) {
            NavigationContent(
                ui,
                NavigationActions(
                    {}, { log += "engine$it" }, { log += "vehicle$it" }, { log += "marker$it" }, { log += "live$it" },
                    { log += "auto$it" }, { log += "chooseApp" }, { log += "side$it" },
                ),
            )
        }
    }

    @Test fun theEngineRowsAreThereOnlyWhenTheSdkIs() {
        navigation(navUi.copy(engineAvailable = false))
        rule.onNodeWithText("In-app maps (Google)").assertDoesNotExist()
    }

    @Test fun choosingAnEngineReportsIt() {
        navigation(navUi)
        rule.onNodeWithText("In-app maps (Google)").performClick()
        assertEquals(listOf("engineIN_APP"), log)
    }

    @Test fun routeForAndMarkerAreSegmented() {
        navigation(navUi)
        rule.onNodeWithText("Car").performScrollTo().performClick()
        rule.onNodeWithText("Beam").performScrollTo().performClick()
        assertEquals(listOf("vehicleDRIVING", "markerbeam"), log)
    }

    @Test fun theNavAppRowShowsOnlyWhenNotDetecting() {
        navigation(navUi)
        rule.onNodeWithText("Nav app").assertDoesNotExist()
    }

    @Test fun aChosenNavAppShowsItsNameAndOpensThePicker() {
        navigation(navUi.copy(autoDetect = false, navAppLabel = "Waze"))
        rule.onNodeWithText("Waze").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Nav app").performScrollTo().performClick()
        assertEquals(listOf("chooseApp"), log)
    }

    @Test fun mirrorSettingsStayEditableWhileInAppMapsAreOn() {
        navigation(navUi.copy(engine = NavEngine.IN_APP))
        rule.onNodeWithText("Left").performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("sideLEFT"), log)
        rule.onNodeWithText("These apply only to the notification mirror", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun theNavAppPickerMarksTheChosenAppAndChooses() {
        rule.setContent {
            KtmTheme(darkTheme = false) {
                NavAppPickerContent(NavAppPickerUi(listOf(AppEntry("a", "Alpha"), AppEntry("w", "Waze")), "w"), {}, { log += it })
            }
        }
        rule.onNodeWithText("Alpha").performClick()
        assertEquals(listOf("a"), log)
    }

    private val recUi = RecordingUi(false, AvgSpeedMode.MOVING, 100, 60, 50, 60, 10, 6)

    private fun recording(ui: RecordingUi) = rule.setContent {
        KtmTheme(darkTheme = false) {
            RecordingContent(ui, RecordingActions({}, { log += "rec$it" }, { log += "avg$it" }, { f, v -> log += "$f=$v" }, { log += "edit$it" }))
        }
    }

    @Test fun theKeepLimitsStayVisibleWhileRecordingIsOff() {
        recording(recUi)
        listOf("Minimum distance", "Minimum time", "Rides to keep").forEach { rule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }

    @Test fun eachNumberShowsItsRangeAndStepsByItsStep() {
        recording(recUi)
        val spec = SettingSpecs.rideMinDistance
        rule.onNodeWithText("${spec.min} to ${spec.max} m").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Raise Minimum distance").performScrollTo().performClick()
        assertEquals(listOf("MIN_DISTANCE=${spec.stepUp(100)}"), log)
    }

    @Test fun tappingTheNumberOpensItsEditSheet() {
        recording(recUi)
        rule.onNodeWithContentDescription("Raise Rides to keep").performScrollTo()
        rule.onNode(hasText("50") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("editHISTORY"), log)
    }

    @Test fun theLegendHasOneBlockPerBandAndSaysItInWords() {
        recording(recUi)
        rule.onNodeWithContentDescription("Speed colours from 0 to 110 km per hour and over").performScrollTo().assertIsDisplayed()
    }

    @Test fun theLegendNamesTheTopSpeed() {
        recording(recUi)
        rule.onNodeWithText("${60 + 10 * 5}+ km/h").performScrollTo().assertIsDisplayed()
    }

    private fun appearance(id: String? = null) = rule.setContent {
        KtmTheme(darkTheme = false) {
            AppearanceContent(
                AppearanceUi(ThemeMode.SYSTEM, NavThemeMode.DAY_NIGHT, FontMode.BRAND, id),
                AppearanceActions({}, { log += "theme$it" }, { log += "riding$it" }, { log += "font$it" }, { log += "accent$it" }, { log += "custom" }),
            )
        }
    }

    @Test fun sevenSwatchesOnOneRowAndOrangeIsTheNullId() {
        appearance()
        AccentPalettes.forEach { rule.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(AccentNames.res(it.id))).assertExists() }
        rule.onNodeWithContentDescription("Custom colour").assertExists()
        rule.onNodeWithContentDescription("KTM orange").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Teal").performScrollTo().performClick()
        assertEquals(listOf("accentnull", "accentteal"), log)
    }

    @Test fun customOpensTheSheet() {
        appearance()
        rule.onNodeWithContentDescription("Custom colour").performScrollTo().performClick()
        assertEquals(listOf("custom"), log)
    }

    @Test fun themeFontAndRidingScreensAreInline() {
        appearance()
        rule.onNodeWithText("Dark").performScrollTo().performClick()
        rule.onNodeWithText("Same as app").performScrollTo().performClick()
        assertEquals(listOf("themeDARK", "ridingAPP"), log)
    }

    @Test fun theCustomSheetKeepsSaveOffForABadHexAndExplainsIt() {
        val saved = mutableListOf<String>()
        rule.setContent { KtmTheme(darkTheme = false) { CustomAccentSheet(Color(0xFF3B82F6), dark = false, { saved += it }, {}) } }
        rule.onNode(hasText("Use colour") and hasClickAction()).assertIsEnabled()
        rule.onNode(hasSetTextAction()).performTextReplacement("12")
        rule.onNodeWithText("Enter 6 hex digits", substring = true).assertIsDisplayed()
        rule.onNode(hasText("Use colour") and hasClickAction()).assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextReplacement("FFF59D")
        rule.onNodeWithText("too close to the background", substring = true).assertIsDisplayed()
        rule.onNode(hasText("Use colour") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(listOf("#FFF59D"), saved)
    }

    @Test fun aTypedHexIsSavedExactlyAsTyped() {
        val saved = mutableListOf<String>()
        rule.setContent { KtmTheme(darkTheme = false) { CustomAccentSheet(Color(0xFF3B82F6), dark = false, { saved += it }, {}) } }
        rule.onNode(hasSetTextAction()).performTextReplacement("3B83F7")
        rule.onNode(hasText("Use colour") and hasClickAction()).performClick()
        assertEquals(listOf("#3B83F7"), saved)
    }
}
