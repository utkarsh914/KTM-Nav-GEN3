package com.navigator.app.ui.feature.settings.picker

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.feature.settings.AudioDevice
import com.navigator.app.ui.feature.settings.AudioDeviceList
import com.navigator.app.ui.theme.KtmTheme
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class AppPickerListTest {
    private val apps = listOf(
        AppEntry("com.zed", "Zed"), AppEntry("com.whatsapp", "WhatsApp"), AppEntry("com.alpha", "alpha"),
        AppEntry("com.mid", "Mid"), AppEntry("com.google.android.dialer", "Phone"),
    )

    @Test fun selectedFirstThenSuggestedThenTheRestAToZ() {
        val sections = AppPickerList.build(apps, selected = setOf("com.zed"), query = "")
        assertEquals(listOf("Zed"), sections[0].apps.map { it.label })
        assertEquals(listOf("Phone", "WhatsApp"), sections[1].apps.map { it.label })
        assertEquals(listOf("alpha", "Mid"), sections[2].apps.map { it.label })
        assertEquals(3, sections.size)
    }

    @Test fun aSelectedSuggestionIsNotListedTwice() {
        val sections = AppPickerList.build(apps, selected = setOf("com.whatsapp"), query = "")
        assertEquals(5, sections.sumOf { it.apps.size })
    }

    @Test fun emptySectionsAreLeftOut() {
        val sections = AppPickerList.build(listOf(AppEntry("com.mid", "Mid")), emptySet(), "")
        assertEquals(1, sections.size)
        assertEquals(com.navigator.app.R.string.picker_all, (sections.single().title as com.navigator.app.ui.feature.settings.UiText.Res).id)
    }

    @Test fun searchFlattensAndMatchesLabelOrPackageIgnoringCase() {
        val byLabel = AppPickerList.build(apps, emptySet(), "ALP")
        assertEquals(listOf("alpha"), byLabel.single().apps.map { it.label })
        assertNull(byLabel.single().title)
        assertEquals(listOf("WhatsApp"), AppPickerList.build(apps, emptySet(), "com.whats").single().apps.map { it.label })
        assertTrue(AppPickerList.build(apps, emptySet(), "nothing").isEmpty())
    }

    @Test fun aSelectedAppThatIsNotInstalledIsSimplyNotShown() {
        val sections = AppPickerList.build(apps, setOf("gone.app"), "")
        assertEquals(5, sections.sumOf { it.apps.size })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PickerViewModelTest {
    private lateinit var settings: SettingsRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx: Context = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(AppSettings(ctx))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun tappingAnAppSavesItAndTappingAgainRemovesIt() = runTest(UnconfinedTestDispatcher()) {
        settings.update { notificationSourceApps = emptySet() }
        val vm = AppPickerViewModel(settings) { listOf(AppEntry("a", "A"), AppEntry("b", "B")) }.also { it.ui.launchIn(backgroundScope) }
        vm.toggle("a")
        assertEquals(setOf("a"), settings.snapshot.value.notificationSourceApps)
        assertEquals(setOf("a"), vm.ui.value.selected)
        vm.toggle("a")
        assertTrue(settings.snapshot.value.notificationSourceApps.isEmpty())
    }

    @Test fun tickingKeepsTheRowWhereItIsUntilThePickerIsReopened() = runTest(UnconfinedTestDispatcher()) {
        settings.update { notificationSourceApps = emptySet() }
        val vm = AppPickerViewModel(settings) { listOf(AppEntry("a", "Alpha"), AppEntry("z", "Zulu")) }.also { it.ui.launchIn(backgroundScope) }
        vm.toggle("z")
        assertEquals(listOf("Alpha", "Zulu"), vm.ui.value.sections.single().apps.map { it.label })
        assertEquals(setOf("z"), vm.ui.value.selected)
    }

    @Test fun searchNarrowsTheList() = runTest(UnconfinedTestDispatcher()) {
        val vm = AppPickerViewModel(settings) { listOf(AppEntry("a", "Maps"), AppEntry("b", "Mail")) }.also { it.ui.launchIn(backgroundScope) }
        vm.setQuery("mai")
        assertEquals(listOf("Mail"), vm.ui.value.sections.single().apps.map { it.label })
        assertFalse(vm.ui.value.loading)
    }

    @Test fun choosingADeviceSavesItAndNoneClearsIt() = runTest(UnconfinedTestDispatcher()) {
        val vm = AudioPickerViewModel(settings) { AudioDeviceList(listOf(AudioDevice("11", "Sena"))) }.also { it.ui.launchIn(backgroundScope) }
        vm.choose("11")
        assertEquals("11", vm.ui.value.selected)
        vm.choose(null)
        assertNull(settings.snapshot.value.callAudioDeviceAddress)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class PickerContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    @Test fun anAppRowTogglesAndTheListIsSectioned() {
        val ui = AppPickerUi(false, "", AppPickerList.build(listOf(AppEntry("a", "Alpha"), AppEntry("com.whatsapp", "WhatsApp")), setOf("a"), ""), setOf("a"))
        rule.setContent { KtmTheme(darkTheme = false) { AppPickerContent(ui, AppPickerActions({}, {}, { log += it })) } }
        rule.onNodeWithText("Selected").assertIsDisplayed()
        rule.onNodeWithText("Messaging and phone").assertIsDisplayed()
        rule.onNodeWithText("WhatsApp").performClick()
        assertEquals(listOf("com.whatsapp"), log)
    }

    @Test fun noMatchesSaysSo() {
        rule.setContent { KtmTheme(darkTheme = false) { AppPickerContent(AppPickerUi(false, "zzz", emptyList(), emptySet()), AppPickerActions({}, {}, {})) } }
        rule.onNodeWithText("Nothing matches").assertIsDisplayed()
    }

    @Test fun anEmptySearchOffersToClearItAndAnEmptyListDoesNotAskForAnotherName() {
        val queries = mutableListOf<String>()
        rule.setContent { KtmTheme(darkTheme = false) { AppPickerContent(AppPickerUi(false, "zzz", emptyList(), emptySet()), AppPickerActions({}, { queries += it }, {})) } }
        rule.onNodeWithText("Try another name.").assertIsDisplayed()
        rule.onNodeWithText("Clear search").performClick()
        assertEquals(listOf(""), queries)
    }

    @Test fun anEmptyListWithNoSearchSaysThereAreNoApps() {
        rule.setContent { KtmTheme(darkTheme = false) { AppPickerContent(AppPickerUi(false, "", emptyList(), emptySet()), AppPickerActions({}, {}, {})) } }
        rule.onNodeWithText("No apps found").assertIsDisplayed()
        rule.onAllNodesWithText("Try another name.").assertCountEquals(0)
    }

    @Test fun withoutNearbyDevicesAccessItOffersAndroidSettings() {
        rule.setContent { KtmTheme(darkTheme = false) { AudioPickerContent(AudioPickerUi(false, emptyList(), null, permissionDenied = true), AudioPickerActions({}, {}, { log += "settings" })) } }
        rule.onNodeWithText("Open settings").performClick()
        assertEquals(listOf("settings"), log)
    }

    @Test fun theAudioPickerListsNoneAndTheDevicesAndChoosesOne() {
        val ui = AudioPickerUi(false, listOf(AudioDevice("11", "Sena")), null)
        rule.setContent { KtmTheme(darkTheme = false) { AudioPickerContent(ui, AudioPickerActions({}, { log += "choose$it" })) } }
        rule.onNodeWithText("None").assertIsDisplayed()
        rule.onNodeWithText("Sena").performClick()
        assertEquals(listOf("choose11"), log)
    }

    @Test fun withoutNearbyDevicesAccessItSaysSoInsteadOfToPair() {
        rule.setContent { KtmTheme(darkTheme = false) { AudioPickerContent(AudioPickerUi(false, emptyList(), null, permissionDenied = true), AudioPickerActions({}, {})) } }
        rule.onNodeWithText("Allow Nearby devices", substring = true).assertIsDisplayed()
    }

    @Test fun aSavedDeviceThatIsNotListedStaysVisibleAndSelected() {
        rule.setContent { KtmTheme(darkTheme = false) { AudioPickerContent(AudioPickerUi(false, emptyList(), "99", false), AudioPickerActions({}, {})) } }
        rule.onNodeWithText("Chosen device (not available now)").assertIsDisplayed()
    }

    @Test fun withoutDevicesItSaysToPairAHeadsetFirst() {
        rule.setContent { KtmTheme(darkTheme = false) { AudioPickerContent(AudioPickerUi(false, emptyList(), null), AudioPickerActions({}, {})) } }
        rule.onNodeWithText("No paired audio devices", substring = true).assertIsDisplayed()
    }
}
