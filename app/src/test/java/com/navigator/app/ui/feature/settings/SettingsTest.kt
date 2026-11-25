package com.navigator.app.ui.feature.settings

import android.content.Context
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.navigator.app.R
import com.navigator.app.ble.BccuConnectionService.ConnectionState
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.connection.ConnectionRepository
import com.navigator.app.data.connection.LinkSources
import com.navigator.app.data.permissions.PermissionGrants
import com.navigator.app.data.permissions.PermissionPolicy
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.permissions.PermissionsState
import com.navigator.app.data.settings.IntSpec
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingSpecs
import com.navigator.app.data.settings.SettingUnit
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.data.settings.SpecValidation
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.feature.navigate.BikePillMapper
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
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
class SettingsViewModelsTest {
    private lateinit var ctx: Context
    private lateinit var settings: SettingsRepository
    private val link = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val repairHint = MutableStateFlow(false)
    private var rePaired = 0
    private var grants = PermissionGrants(true, true, false, true, notificationAccess = false, batteryUnrestricted = false)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(AppSettings(ctx))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.connection() = ConnectionRepository(
        backgroundScope, settings,
        LinkSources(
            state = link, scanning = MutableStateFlow(false), attempt = MutableStateFlow(0), repairHint = repairHint,
            lastError = MutableStateFlow(null), deviceName = MutableStateFlow(null), bluetoothOn = MutableStateFlow(true),
        ),
        stopService = { rePaired++ }, cancelNotifications = {},
    )

    private fun TestScope.permissions() = PermissionsRepository({ grants }, settings, backgroundScope)

    private fun pair() = settings.update { bondedDeviceAddress = "AA:BB"; bondedDeviceName = "KTM 390" }

    @Test fun theHomeListsTheEightCategoriesInOrderWithSummaries() = runTest(UnconfinedTestDispatcher()) {
        val vm = SettingsHomeViewModel(settings, connection(), permissions(), "1.2").also { it.ui.launchIn(backgroundScope) }
        assertEquals(SettingsPage.entries, vm.ui.value.rows.map { it.page })
        assertEquals(8, vm.ui.value.rows.size)
    }

    @Test fun theRepairBannerNeedsAPairedBikeAndTheHint() = runTest(UnconfinedTestDispatcher()) {
        val vm = SettingsHomeViewModel(settings, connection(), permissions(), "1").also { it.ui.launchIn(backgroundScope) }
        repairHint.value = true
        assertFalse("nothing to re-pair", vm.ui.value.repairSuggested)
        pair()
        assertTrue(vm.ui.value.repairSuggested)
        assertEquals("KTM 390", vm.ui.value.bike.name)
        repairHint.value = false
        assertFalse(vm.ui.value.repairSuggested)
    }

    @Test fun rePairForgetsTheBike() = runTest(UnconfinedTestDispatcher()) {
        pair()
        val vm = SettingsHomeViewModel(settings, connection(), permissions(), "1")
        vm.rePair()
        assertEquals(1, rePaired)
        assertNull(settings.snapshot.value.bondedDeviceAddress)
    }

    @Test fun bikeDashWritesItsSettings() = runTest(UnconfinedTestDispatcher()) {
        val vm = BikeDashViewModel(settings, connection(), permissions(), io = Dispatchers.Unconfined).also { it.ui.launchIn(backgroundScope) }
        vm.setShowOnDash(false); vm.setMarquee(false); vm.setLatinOnly(false); vm.setName("Asha")
        with(vm.ui.value) {
            assertFalse(showOnDash); assertFalse(marquee); assertFalse(latinOnly); assertEquals("Asha", userName)
        }
        assertFalse("no notification access in this test", vm.ui.value.accessGranted)
        assertFalse("not connected: no test sends", vm.ui.value.connected)
    }

    @Test fun bikeDashKnowsWhenDirectionsAreOnTheDash() = runTest(UnconfinedTestDispatcher()) {
        val live = MutableStateFlow(false)
        val vm = BikeDashViewModel(settings, connection(), permissions(), io = Dispatchers.Unconfined, guidanceLive = live).also { it.ui.launchIn(backgroundScope) }
        assertFalse(vm.ui.value.guidanceLive)
        live.value = true
        assertTrue(vm.ui.value.guidanceLive)
    }

    @Test fun bikeDashCountsTheChosenApps() = runTest(UnconfinedTestDispatcher()) {
        val vm = BikeDashViewModel(settings, connection(), permissions(), io = Dispatchers.Unconfined).also { it.ui.launchIn(backgroundScope) }
        settings.update { notificationSourceApps = setOf("a", "b", "c") }
        assertEquals(3, vm.ui.value.appCount)
    }

    @Test fun volumeIsOneOfFourStepsAndOldValuesRoundToTheNearest() = runTest(UnconfinedTestDispatcher()) {
        val vm = SoundsViewModel(settings, { AudioDeviceList() }).also { it.ui.launchIn(backgroundScope) }
        vm.setVolume(BeepVolume.HIGH)
        assertEquals(60, settings.snapshot.value.beepVolumePercent)
        assertEquals(BeepVolume.HIGH, vm.ui.value.volume)
        settings.update { beepVolumePercent = 45 }
        assertEquals("45 is nearer 35 than 60", BeepVolume.MEDIUM, vm.ui.value.volume)
        settings.update { beepVolumePercent = 90 }
        assertEquals(BeepVolume.MAX, vm.ui.value.volume)
    }

    @Test fun theAlertSpeedIsKeptInRange() = runTest(UnconfinedTestDispatcher()) {
        val vm = SoundsViewModel(settings, { AudioDeviceList() }).also { it.ui.launchIn(backgroundScope) }
        vm.setAlertAt(10_000)
        assertEquals(SettingSpecs.overspeedLimit.max, vm.ui.value.alertAtKmh)
        vm.setAlertAt(0)
        assertEquals(SettingSpecs.overspeedLimit.min, vm.ui.value.alertAtKmh)
    }

    @Test fun theCallAudioNameComesFromThePairedDevices() = runTest(UnconfinedTestDispatcher()) {
        settings.update { preferredCallAudioDeviceAddress = "11:22" }
        val vm = SoundsViewModel(settings, { AudioDeviceList(listOf(AudioDevice("11:22", "Sena 50S"))) }).also { it.ui.launchIn(backgroundScope) }
        assertEquals("Sena 50S", vm.ui.value.callAudioName)
        assertFalse(vm.ui.value.callAudioUnavailable)
        settings.update { preferredCallAudioDeviceAddress = "99:99" }
        assertNull(vm.ui.value.callAudioName)
        assertTrue("a chosen device that can't be named is still in force", vm.ui.value.callAudioUnavailable)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsSummaryTest {
    private val perms = PermissionPolicy.build(PermissionGrants(true, true, false, true, notificationAccess = false, batteryUnrestricted = false), NavEngine.IN_APP)
    private fun snap(change: AppSettings.() -> Unit = {}): SettingsSnapshot {
        val s = AppSettings(RuntimeEnvironment.getApplication())
        s.change()
        return SettingsSnapshot.read(s)
    }

    private fun summary(page: SettingsPage, snap: SettingsSnapshot = snap(), bike: BikeStatus = BikeStatus.NotPaired, p: PermissionsState = perms) =
        SettingsSummary.of(page, snap, bike, p, "9.9", snap.engine)

    @Test fun everyCategoryHasASummary() {
        SettingsPage.entries.forEach { assertTrue(it.name, summary(it).parts.isNotEmpty()) }
    }

    @Test fun permissionsAreAmberOnlyWhenARequiredOneIsMissing() {
        assertFalse(summary(SettingsPage.PERMISSIONS).attention)
        val missing = PermissionPolicy.build(PermissionGrants(false, true, false, true, false, false), NavEngine.IN_APP)
        val s = summary(SettingsPage.PERMISSIONS, p = missing)
        assertTrue(s.attention)
        assertEquals(UiText.Plural(R.plurals.settings_sum_permissions_missing, 1), s.parts.single())
    }

    @Test fun theSoundsSummaryNamesTheAlertSpeedOnlyWhenItIsOn() {
        val on = summary(SettingsPage.SOUNDS, snap { overspeedEnabled = true; overspeedLimitKmh = 80 })
        assertTrue(UiText.Res(R.string.settings_sum_alert_at, listOf(80)) in on.parts)
        val off = summary(SettingsPage.SOUNDS, snap { overspeedEnabled = false })
        assertTrue(UiText.Res(R.string.settings_sum_alert_off) in off.parts)
    }

    @Test fun theBikeSummaryReadsTheNameAndNotifications() {
        val unset = summary(SettingsPage.BIKE_DASH, snap { mirrorEnabled = false; userName = null }, BikeStatus.Connected("KTM"))
        assertEquals(UiText.Res(R.string.settings_sum_name_unset), unset.parts[0])
        assertEquals(UiText.Res(R.string.settings_sum_messages_off), unset.parts[1])
        val named = summary(SettingsPage.BIKE_DASH, snap { userName = " Arjun " }, BikeStatus.Connected("KTM"))
        assertEquals(UiText.Raw("Arjun"), named.parts[0])
    }

    @Test fun theSoundsSummaryNamesTheBeepVolume() {
        val s = summary(SettingsPage.SOUNDS, snap { turnBeepEnabled = true; beepVolumePercent = 35 })
        assertEquals(UiText.Res(R.string.settings_sum_beeps_at, listOf(35)), s.parts[0])
    }

    @Test fun appearanceNamesTheThemeAndTheAccent() {
        val s = summary(SettingsPage.APPEARANCE, snap { themeMode = ThemeMode.DARK })
        assertEquals(UiText.Res(R.string.settings_sum_theme_dark), s.parts[0])
        assertEquals(UiText.Res(R.string.accent_orange), s.parts[1])
    }

    @Test fun aboutShowsTheVersion() {
        assertEquals(UiText.Res(R.string.settings_sum_about, listOf("9.9")), summary(SettingsPage.ABOUT).parts.single())
    }
}

class EditValueTextTest {
    private val spec = IntSpec(10, 200, 5, 60, SettingUnit.KMH)

    @Test fun anInRangeNumberHasNoMessage() = assertNull(EditValueText.error(spec.validate("80"), "80", spec))

    @Test fun anOutOfRangeNumberSaysTheLimit() {
        // One message for both ends: the whole range, as the mock says ("Enter a number from 10 to 200").
        assertEquals(UiText.Res(R.string.edit_value_range, listOf(10, 200)), EditValueText.error(spec.validate("5"), "5", spec))
        assertEquals(UiText.Res(R.string.edit_value_range, listOf(10, 200)), EditValueText.error(spec.validate("999"), "999", spec))
    }

    @Test fun anEmptyFieldIsNotAnErrorYetButGarbageIs() {
        assertNull(EditValueText.error(SpecValidation.NotANumber, "", spec))
        assertNotNull(EditValueText.error(SpecValidation.NotANumber, "1a", spec))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h900dp")
class SettingsContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()
    private val bike = BikeCard("KTM 390", BikePillMapper.map(BikeStatus.Connected("KTM 390")), paired = true)
    private val rows = SettingsPage.entries.map { CategoryRow(it, CategorySummary(listOf(UiText.Raw("sum-${it.name}")), attention = it == SettingsPage.PERMISSIONS)) }

    private fun home(repair: Boolean = false) = rule.setContent {
        KtmTheme(darkTheme = false) {
            SettingsHomeContent(SettingsHomeUi(bike, repair, rows), SettingsHomeActions({ log += "page${it.name}" }, { log += "repair" }, { log += "quit" }))
        }
    }

    @Test fun showsTheCategoriesTheirSummariesAndQuitLast() {
        home()
        rule.onNodeWithText("sum-SOUNDS").assertIsDisplayed()
        rule.onNodeWithText("Quit KTM Navigator").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("quit"), log)
    }

    @Test fun theBikeCardSaysWhenItWasPairedWhenKnownAndNothingWhenNot() {
        val paired = java.time.ZonedDateTime.of(2026, 11, 12, 9, 0, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        rule.setContent {
            KtmTheme(darkTheme = false) {
                SettingsHomeContent(SettingsHomeUi(bike.copy(pairedMs = paired), false, rows), SettingsHomeActions({}, {}, {}))
            }
        }
        rule.onNodeWithText("Paired", substring = true).assertIsDisplayed()
        rule.onNodeWithText(PairedDate.format(paired), substring = true).assertIsDisplayed()
    }

    @Test fun withoutAPairDateTheCardHasNoPairedLine() {
        home()
        rule.onAllNodesWithText("Paired", substring = true).assertCountEquals(0)
    }

    @Test fun aCategoryOpensItsPage() {
        home()
        rule.onNodeWithText("Sounds & alerts").performClick()
        assertEquals(listOf("pageSOUNDS"), log)
    }

    @Test fun theBikeCardOpensBikeAndDash() {
        home()
        rule.onNodeWithText("KTM 390").performClick()
        assertEquals(listOf("pageBIKE_DASH"), log)
    }

    @Test fun theRepairBannerOffersRePair() {
        home(repair = true)
        rule.onNodeWithText("Can't reach your bike").assertIsDisplayed()
        rule.onNode(hasText("Re-pair") and hasClickAction()).performClick()
        assertEquals(listOf("repair"), log)
    }

    @Test fun noBannerWhenAllIsWell() {
        home()
        rule.onNodeWithText("Can't reach your bike").assertDoesNotExist()
    }

    private fun bikeDash(connected: Boolean, access: Boolean = true, apps: Int = 0, live: Boolean = false) = rule.setContent {
        KtmTheme(darkTheme = false) {
            BikeDashContent(
                BikeDashUi(bike, false, connected, true, apps, true, true, "", access, live),
                BikeDashActions({}, { log += "repair" }, {}, { log += "apps" }, {}, {}, { log += "perms" }, { log += "name" }, { log += "msg" }, { log += "turn" }),
            )
        }
    }

    @Test fun aTestTurnWaitsWhileDirectionsAreOnTheDash() {
        bikeDash(connected = true, live = true)
        rule.onNode(hasText("Test turn") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        rule.onNode(hasText("Test message") and hasClickAction()).performScrollTo().assertIsEnabled()
        rule.onNodeWithText("Not while directions are on the dash.").performScrollTo().assertIsDisplayed()
    }

    @Test fun testSendsNeedALiveLink() {
        bikeDash(connected = false)
        rule.onNode(hasText("Test message") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Connect your bike to send a test.").performScrollTo().assertIsDisplayed()
    }

    @Test fun testSendsWorkWhenConnected() {
        bikeDash(connected = true)
        rule.onNode(hasText("Test turn") and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("turn"), log)
    }

    @Test fun missingNotificationAccessLinksToPermissions() {
        bikeDash(connected = true, access = false)
        rule.onNodeWithText("Notification access").performScrollTo().performClick()
        assertEquals(listOf("perms"), log)
    }

    @Test fun theAppsRowSaysWhenNoneAreChosen() {
        bikeDash(connected = true, apps = 0)
        rule.onNodeWithText("None chosen").assertIsDisplayed()
        rule.onNodeWithText("Apps on your dash").performClick()
        assertEquals(listOf("apps"), log)
    }

    private fun sounds(ui: SoundsUi) = rule.setContent {
        KtmTheme(darkTheme = false) {
            SoundsContent(ui, SoundsActions({}, {}, { log += "vol${it.percent}" }, {}, { log += "hearBeeps" }, {}, {}, {}, { log += "hearAlert" }, { log += "audio" }))
        }
    }

    private val on = SoundsUi(beeps = true, volume = BeepVolume.MEDIUM, swapChannels = false, speedAlert = true, alertAtKmh = 80, callAudioName = null)

    @Test fun volumeShowsAllFourStepsAndPicksOne() {
        sounds(on)
        listOf("Low", "Medium", "High", "Max").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("High").performClick()
        assertEquals(listOf("vol60"), log)
    }

    @Test fun volumeAndSwapAreOffWhileBeepsAreOff() {
        sounds(on.copy(beeps = false))
        rule.onNodeWithText("High").assertIsNotEnabled()
    }

    @Test fun theAlertStepperShowsItsRangeAndValue() {
        sounds(on)
        rule.onNodeWithText("80").assertIsDisplayed()
        rule.onNodeWithText("${SettingSpecs.overspeedLimit.min} to ${SettingSpecs.overspeedLimit.max} km/h").assertIsDisplayed()
    }

    @Test fun previewsSitUnderWhatTheyPreview() {
        sounds(on)
        rule.onNode(hasContentDescription("Play L · R · L, hear turn beeps") and hasClickAction()).performClick()
        rule.onNode(hasContentDescription("Play, hear speed alert") and hasClickAction()).performClick()
        assertEquals(listOf("hearBeeps", "hearAlert"), log)
    }

    @Test fun theCallAudioRowShowsNoneOrTheDevice() {
        sounds(on)
        rule.onNodeWithText("None").performScrollTo().assertIsDisplayed()
    }

    @Test fun aChosenDeviceThatCannotBeNamedIsNotShownAsNone() {
        sounds(on.copy(callAudioUnavailable = true))
        rule.onNodeWithText("Not available now").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("None").assertDoesNotExist()
    }

    @Test fun theNumberSheetKeepsSaveOffUntilTheValueIsInRange() {
        val saved = mutableListOf<Int>()
        rule.setContent { KtmTheme(darkTheme = false) { EditNumberSheet("Alert at", 80, SettingSpecs.overspeedLimit, "km/h", { saved += it }, {}) } }
        rule.onNode(hasText("Save") and hasClickAction()).assertIsEnabled()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("5")
        rule.onNodeWithText("Enter a number from ${SettingSpecs.overspeedLimit.min} to ${SettingSpecs.overspeedLimit.max}").assertIsDisplayed()
        rule.onNode(hasText("Save") and hasClickAction()).assertIsNotEnabled()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("90")
        rule.onNode(hasText("Save") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(listOf(90), saved)
    }
}
