package com.navigator.app.ui.feature.places

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.navigator.app.data.places.PlacesRepository
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
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

private val toit = SavedPlace(13.0, 77.5, "Toit", "Indiranagar")
private val park = SavedPlace(12.2, 77.2, "Cubbon Park")
private val office = SavedPlace(12.9, 77.6, "Office")

class PlaceActionsTest {
    @Test fun homeAndWorkChangeOrGo() {
        assertEquals(
            listOf(PlaceAction.Change, PlaceAction.Remove),
            PlaceActions.forTarget(PlaceTarget.Slot(FavoriteSlot.HOME, toit), inApp = true),
        )
    }

    @Test fun aSavedPlaceHasDirectionsAndTheMapInTheApp() {
        assertEquals(
            listOf(PlaceAction.Directions, PlaceAction.ShowOnMap, PlaceAction.SetHome, PlaceAction.SetWork, PlaceAction.Remove),
            PlaceActions.forTarget(PlaceTarget.Saved(toit), inApp = true),
        )
    }

    @Test fun inMirrorModeGoogleMapsReplacesBoth() {
        val actions = PlaceActions.forTarget(PlaceTarget.Saved(toit), inApp = false)
        assertEquals(listOf(PlaceAction.OpenInMaps, PlaceAction.SetHome, PlaceAction.SetWork, PlaceAction.Remove), actions)
        assertEquals("Remove is always last", PlaceAction.Remove, actions.last())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaceIntentsTest {
    @Test fun navigationStartsGuidanceInGoogleMaps() {
        val i = PlaceActions.navigationIntent(toit)
        assertEquals("google.navigation:q=13.000000,77.500000", i.data.toString())
        assertEquals(PlaceActions.MAPS_PACKAGE, i.`package`)
    }

    @Test fun theMapLinkNamesThePinAndEncodesTheName() {
        val i = PlaceActions.showIntent(SavedPlace(1.5, 2.5, "Café & Bar"))
        assertEquals("geo:1.500000,2.500000?q=1.500000,2.500000(Caf%C3%A9%20%26%20Bar)", i.data.toString())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlacesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: PlacesRepository
    private var origin: Pair<Double, Double>? = null

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val ctx: Context = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences(PlacesStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        repo = PlacesRepository(ctx)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm() = PlacesViewModel(repo) { origin }.also { it.ui.launchIn(backgroundScope) }

    @Test fun splitsHomeWorkAndSaved() = runTest(dispatcher) {
        repo.assign(toit, FavoriteSlot.HOME); repo.assign(office, FavoriteSlot.WORK); repo.assign(park, FavoriteSlot.OTHER)
        val v = vm()
        assertEquals(toit, v.ui.value.home)
        assertEquals(office, v.ui.value.work)
        assertEquals(listOf(park), v.ui.value.saved)
    }

    @Test fun removingHomeOffersUndo() = runTest(dispatcher) {
        repo.assign(toit, FavoriteSlot.HOME)
        val v = vm()
        val seen = mutableListOf<PlacesEvent>()
        v.events.onEach { seen += it }.launchIn(backgroundScope)
        v.removeSlot(FavoriteSlot.HOME)
        assertEquals(null, v.ui.value.home)
        val removed = seen.single() as PlacesEvent.Removed
        v.undoRemove(removed.items)
        assertEquals(toit, v.ui.value.home)
    }

    @Test fun selectingAndDeletingSeveralIsOneUndo() = runTest(dispatcher) {
        repo.assign(toit, FavoriteSlot.OTHER); repo.assign(park, FavoriteSlot.OTHER); repo.assign(office, FavoriteSlot.OTHER)
        val v = vm()
        val seen = mutableListOf<PlacesEvent>()
        v.events.onEach { seen += it }.launchIn(backgroundScope)
        v.startSelecting(PlacesRepository.key(toit))
        v.toggle(PlacesRepository.key(park))
        assertTrue(v.ui.value.selecting)
        assertEquals(2, v.ui.value.selected.size)
        v.deleteSelected()
        assertFalse(v.ui.value.selecting)
        assertEquals(listOf(office), v.ui.value.saved)
        v.undoRemove((seen.single() as PlacesEvent.Removed).items)
        assertEquals(3, v.ui.value.saved.size)
    }

    @Test fun selectAllAndNone() = runTest(dispatcher) {
        repo.assign(toit, FavoriteSlot.OTHER); repo.assign(park, FavoriteSlot.OTHER)
        val v = vm()
        v.startSelecting()
        v.selectAll(true)
        assertTrue(v.ui.value.allSelected)
        v.selectAll(false)
        assertTrue(v.ui.value.selected.isEmpty())
        assertTrue("still selecting with nothing chosen", v.ui.value.selecting)
    }

    @Test fun aSelectionNeverOutlivesItsPlaces() = runTest(dispatcher) {
        repo.assign(toit, FavoriteSlot.OTHER); repo.assign(park, FavoriteSlot.OTHER)
        val v = vm()
        v.startSelecting(PlacesRepository.key(toit))
        repo.removeSaved(setOf(PlacesRepository.key(toit)))
        assertTrue(v.ui.value.selected.isEmpty())
        repo.removeSaved(setOf(PlacesRepository.key(park)))
        assertFalse("the list emptied: selecting ends", v.ui.value.selecting)
    }

    @Test fun settingHomeFromASavedPlaceOffersUndoToWhatWasThere() = runTest(dispatcher) {
        repo.assign(office, FavoriteSlot.HOME); repo.assign(toit, FavoriteSlot.OTHER)
        val v = vm()
        val seen = mutableListOf<PlacesEvent>()
        v.events.onEach { seen += it }.launchIn(backgroundScope)
        v.assign(toit, FavoriteSlot.HOME)
        assertEquals(toit, v.ui.value.home)
        v.undoAssign(seen.single() as PlacesEvent.Assigned)
        assertEquals(office, v.ui.value.home)
    }

    @Test fun theOriginComesFromTheLocationAndKeepsTheLastKnownOne() = runTest(dispatcher) {
        origin = 12.0 to 77.0
        val v = vm()
        assertEquals(12.0 to 77.0, v.ui.value.origin)
        origin = null
        v.refreshOrigin()
        assertEquals("a lost fix keeps the last known one", 12.0 to 77.0, v.ui.value.origin)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class PlacesContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun ui(home: SavedPlace? = null, work: SavedPlace? = null, saved: List<SavedPlace> = emptyList(), selecting: Boolean = false, selected: Set<String> = emptySet()) =
        PlacesUi(home, work, saved, 12.9 to 77.6, selecting, selected)

    private fun show(ui: PlacesUi) = rule.setContent {
        KtmTheme(darkTheme = false) {
            PlacesContent(
                ui,
                PlacesActions(
                    { s, _ -> log += "ride$s" }, { log += "set$it" }, { s, _ -> log += "slotMore$s" }, { log += "open${it.label}" },
                    { log += "more${it.label}" }, { log += "add" }, { log += "select$it" }, { log += "toggle$it" }, { log += "all$it" },
                    { log += "stop" }, { log += "delete" },
                ),
            )
        }
    }

    @Test fun emptyShowsWhatHomeAndWorkAreForAndWhereSavesComeFrom() {
        show(ui())
        rule.onNodeWithText("Home & work").assertIsDisplayed()
        rule.onNodeWithText("Set home").assertIsDisplayed()
        rule.onNodeWithText("Set work").assertIsDisplayed()
        rule.onNodeWithText("No saved places yet").assertIsDisplayed()
        rule.onNodeWithText("Save places from search, a dropped pin, or the end of a trip.").assertIsDisplayed()
        rule.onNodeWithText("Add place").assertIsDisplayed()
    }

    @Test fun anEmptySlotOpensThePicker() {
        show(ui())
        rule.onNodeWithText("Set work").performClick()
        assertEquals(listOf("setWORK"), log)
    }

    @Test fun aSetSlotRoutesThereAndItsMenuOpens() {
        show(ui(home = toit))
        rule.onNode(hasText("Home") and hasClickAction()).performClick()
        rule.onNodeWithContentDescription("More actions for Home").performClick()
        assertEquals(listOf("rideHOME", "slotMoreHOME"), log)
    }

    @Test fun theSavedHeaderCountsThePlaces() {
        show(ui(saved = listOf(toit, park)))
        rule.onNodeWithText("Saved · 2").assertIsDisplayed()
    }

    @Test fun savedPlacesShowAndOpen() {
        show(ui(saved = listOf(toit, park)))
        rule.onNodeWithText("Toit").performClick()
        rule.onNodeWithContentDescription("More actions for Cubbon Park").performClick()
        assertEquals(listOf("openToit", "moreCubbon Park"), log)
    }

    @Test fun addPlaceIsTheFloatingAction() {
        show(ui())
        rule.onNodeWithText("Add place").performClick()
        assertEquals(listOf("add"), log)
    }

    @Test fun selectingHidesHomeWorkAndTheAddButtonAndShowsTheCount() {
        show(ui(home = toit, saved = listOf(park, office), selecting = true, selected = setOf(PlacesRepository.key(park))))
        rule.onNodeWithText("1 selected").assertIsDisplayed()
        rule.onNodeWithText("Home").assertDoesNotExist()
        rule.onNodeWithText("Add place").assertDoesNotExist()
        rule.onNodeWithContentDescription("Select all").performClick()
        rule.onNodeWithContentDescription("Remove selected places").performClick()
        assertEquals(listOf("alltrue", "delete"), log)
    }

    @Test fun theMenuStartsSelectingOnlyWhenThereAreSavedPlaces() {
        show(ui())
        rule.onAllNodesWithContentDescription("More").assertCountEquals(0)
    }

    @Test fun theTopBarMenuStartsSelecting() {
        show(ui(saved = listOf(toit)))
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Select places").performClick()
        assertEquals(listOf("selectnull"), log)
    }

    @Test fun aLongPressStartsSelectingWithThatPlace() {
        show(ui(saved = listOf(toit)))
        rule.onNodeWithText("Toit").performTouchInput { longClick() }
        assertEquals(listOf("select" + PlacesRepository.key(toit)), log)
    }

    @Test fun aRowTogglesWhileSelecting() {
        show(ui(saved = listOf(toit, park), selecting = true))
        rule.onNodeWithText("Cubbon Park").performClick()
        assertEquals(listOf("toggle" + PlacesRepository.key(park)), log)
    }

    @Test fun theSheetListsRemoveLastAndReportsTheChoice() {
        val chosen = mutableListOf<PlaceAction>()
        rule.setContent {
            KtmTheme(darkTheme = false) {
                PlaceActionsSheet(PlaceTarget.Saved(toit), inApp = false, onAction = { chosen += it }, onDismiss = {})
            }
        }
        rule.onNodeWithText("Open in Google Maps").assertIsDisplayed()
        rule.onNodeWithText("Directions").assertDoesNotExist()
        rule.onNodeWithText("Set as Work").performClick()
        assertEquals(listOf(PlaceAction.SetWork), chosen)
    }

    @Test fun theSheetNamesThePlaceWithItsAddressAndDistanceAndHintsAtMirrorMode() {
        rule.setContent {
            KtmTheme(darkTheme = false) {
                PlaceActionsSheet(PlaceTarget.Saved(toit), inApp = false, onAction = {}, onDismiss = {}, distance = "3.4 km away")
            }
        }
        rule.onNodeWithText("Toit").assertIsDisplayed()
        rule.onNodeWithText("Indiranagar · 3.4 km away").assertIsDisplayed()
        rule.onNodeWithText("Mirror mode: we copy the turns").assertIsDisplayed()
    }

    @Test fun thePlaceActionsTargetSurvivesTheProcessBeingKilled() {
        val place = SavedPlace(12.5, 77.5, "Toit", "Indiranagar")
        val saver = PlaceTarget.Saver
        val scope = androidx.compose.runtime.saveable.SaverScope { true }
        for (t in listOf<PlaceTarget?>(null, PlaceTarget.Saved(place), PlaceTarget.Slot(FavoriteSlot.WORK, place))) {
            val saved = with(saver) { scope.save(t) }!!
            assertEquals(t, saver.restore(saved))
        }
    }
}
