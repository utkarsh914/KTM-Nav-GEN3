package com.navigator.app.ui.feature.settings

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.navigator.app.R
import com.navigator.app.backup.BackupManager
import com.navigator.app.data.permissions.PermissionGrants
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.data.permissions.PermissionPolicy
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.feature.permissions.Explainer
import com.navigator.app.ui.feature.permissions.FixStep
import com.navigator.app.ui.feature.permissions.PermissionFix
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private fun grants(
    nearby: Boolean = true, location: Boolean = true, always: Boolean = true, notifications: Boolean = true,
    access: Boolean = true, battery: Boolean = true,
) = PermissionGrants(nearby, location, always, notifications, access, battery)

class PermissionFixTest {
    private fun state(g: PermissionGrants, engine: NavEngine = NavEngine.MIRROR) = PermissionPolicy.build(g, engine)

    @Test fun locationAsksForFineWithCoarse() {
        val step = PermissionFix.stepFor(PermissionId.LOCATION, state(grants(location = false))) as FixStep.Request
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in step.permissions && Manifest.permission.ACCESS_COARSE_LOCATION in step.permissions)
    }

    @Test fun allTheTimeAsksForPlainLocationFirst() {
        val s = state(grants(location = false, always = false))
        assertTrue(PermissionFix.stepFor(PermissionId.LOCATION_ALWAYS, s) is FixStep.Request)
    }

    @Test fun allTheTimeExplainsBeforeLeavingOnceLocationIsAllowed() {
        val s = state(grants(always = false))
        assertEquals(FixStep.Explain(Explainer.BACKGROUND_LOCATION), PermissionFix.stepFor(PermissionId.LOCATION_ALWAYS, s))
    }

    @Test fun notificationAccessExplainsFirstAndBatteryOpensItsDialog() {
        val s = state(grants(access = false, battery = false))
        assertEquals(FixStep.Explain(Explainer.NOTIFICATION_ACCESS), PermissionFix.stepFor(PermissionId.NOTIFICATION_ACCESS, s))
        assertEquals(FixStep.OpenBattery, PermissionFix.stepFor(PermissionId.BATTERY, s))
    }

    @Test fun nearbyAndNotificationsAreSystemDialogs() {
        val s = state(grants(nearby = false, notifications = false))
        val n = PermissionFix.stepFor(PermissionId.NEARBY_DEVICES, s) as FixStep.Request
        assertTrue(Manifest.permission.BLUETOOTH_CONNECT in n.permissions && Manifest.permission.BLUETOOTH_SCAN in n.permissions)
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), (PermissionFix.stepFor(PermissionId.NOTIFICATIONS, s) as FixStep.Request).permissions)
    }

    @Test fun theFirstMissingRequiredOneIsFixedNext() {
        assertEquals(PermissionId.NEARBY_DEVICES, PermissionFix.nextRequired(state(grants(nearby = false, location = false))))
        assertEquals(PermissionId.LOCATION, PermissionFix.nextRequired(state(grants(location = false, always = false, battery = false))))
        assertNull(PermissionFix.nextRequired(state(grants(always = false, battery = false))))
    }

    @Test fun onlyTheSettingsOnesSayOpen() {
        assertEquals(setOf(PermissionId.NOTIFICATION_ACCESS, PermissionId.BATTERY), PermissionId.entries.filter(PermissionFix::opensSettings).toSet())
    }
}

class BackupLogicTest {
    private fun preview(inFile: Int, new: Int, places: Int, settings: Int) = BackupManager.Preview(inFile, new, places, settings)

    @Test fun choicesStartOnForWhatIsNew() {
        assertEquals(RestoreChoice(true, true, false), RestoreChoice.defaultsFor(preview(3, 2, 4, 5)))
        assertEquals("settings replace the rider's own: never on by default", RestoreChoice(false, false, false), RestoreChoice.defaultsFor(preview(3, 0, 0, 5)))
        assertFalse(RestoreChoice.defaultsFor(preview(0, 0, 0, 0)).any)
    }

    @Test fun theRestoredMessageNamesOnlyWhatWasAsked() {
        val r = BackupManager.Restored(2, 3, 4, true)
        val parts = BackupViewModel.restoredParts(r, rides = true, places = false, settings = true)
        assertEquals(
            listOf(UiText.Plural(R.plurals.backup_restored_rides, 2), UiText.Plural(R.plurals.backup_restored_settings, 4)),
            parts,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupAndAboutViewModelTest {
    private class FakeGateway : BackupGateway {
        var backupOk = true; var preview: BackupManager.Preview? = BackupManager.Preview(3, 2, 1, 4); var restoreOk = true
        val restores = mutableListOf<Triple<Boolean, Boolean, Boolean>>()
        override suspend fun backup(uri: Uri) = BackupManager.Summary(if (backupOk) 7 else 0, backupOk)
        override suspend fun inspect(uri: Uri) = preview
        override suspend fun restore(uri: Uri, rides: Boolean, places: Boolean, settings: Boolean): BackupManager.Restored {
            restores += Triple(rides, places, settings)
            return BackupManager.Restored(2, 1, 4, restoreOk)
        }
    }

    private val uri: Uri = Uri.parse("content://x/backup.zip")
    private lateinit var settings: SettingsRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx: Context = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(AppSettings(ctx))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun aBackupReportsHowManyRidesOrThatItFailed() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(); val vm = BackupViewModel(g)
        vm.backUpTo(uri)
        assertEquals(BackupEvent.BackedUp(7, uri), vm.events.first())
        g.backupOk = false; vm.backUpTo(uri)
        assertEquals(BackupEvent.BackupFailed, vm.events.first())
        assertFalse(vm.ui.value.busy)
    }

    @Test fun aRestoreWithSettingsLeavesARestartReminderUntilTheAppRestarts() = runTest(UnconfinedTestDispatcher()) {
        val vm = BackupViewModel(FakeGateway())
        vm.restore(uri, rides = true, places = false, settings = false)
        assertFalse(vm.ui.value.restartPending)
        vm.restore(uri, rides = false, places = false, settings = true)
        assertTrue(vm.ui.value.restartPending)
        assertFalse(vm.ui.value.restoring)
        vm.backUpTo(uri)
        assertTrue(vm.ui.value.restartPending)
    }

    @Test fun aChosenFileOpensTheSheetWithWhatItHolds() = runTest(UnconfinedTestDispatcher()) {
        val vm = BackupViewModel(FakeGateway())
        vm.openRestore(uri)
        assertTrue(vm.ui.value.restore is RestoreState.Ready)
        vm.dismissRestore()
        assertNull(vm.ui.value.restore)
    }

    @Test fun aFileThatIsNotABackupShowsTheInlineErrorState() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway().apply { preview = null }
        val vm = BackupViewModel(g)
        vm.openRestore(uri)
        assertEquals(RestoreState.Unreadable, vm.ui.value.restore)
    }

    @Test fun restoringClosesTheSheetAndSaysWhatWasAdded() = runTest(UnconfinedTestDispatcher()) {
        val g = FakeGateway(); val vm = BackupViewModel(g)
        vm.openRestore(uri)
        vm.restore(uri, rides = true, places = false, settings = true)
        assertNull(vm.ui.value.restore)
        val e = vm.events.first() as BackupEvent.Restored
        assertTrue(e.restartNeeded)
        assertEquals(listOf(Triple(true, false, true)), g.restores)
    }

    @Test fun restoringWithoutSettingsNeedsNoRestart() = runTest(UnconfinedTestDispatcher()) {
        val vm = BackupViewModel(FakeGateway())
        vm.restore(uri, rides = true, places = true, settings = false)
        assertFalse((vm.events.first() as BackupEvent.Restored).restartNeeded)
    }

    @Test fun aFailedRestoreKeepsWhatIsNeededToRetry() = runTest(UnconfinedTestDispatcher()) {
        val vm = BackupViewModel(FakeGateway().apply { restoreOk = false })
        vm.restore(uri, true, false, true)
        assertEquals(BackupEvent.RestoreFailed(uri, true, false, true), vm.events.first())
    }

    @Test fun aboutWritesTheLogCopyAndTheOverlay() = runTest(UnconfinedTestDispatcher()) {
        var copy = false; var overlay: Boolean? = null
        val vm = AboutViewModel(settings, "1.2", true, { copy }, { copy = it }, { overlay = it }).also { it.ui.launchIn(backgroundScope) }
        vm.setCopyLogs(true); vm.setOverlay(true)
        assertTrue(copy); assertEquals(true, overlay)
        assertTrue(vm.ui.value.copyLogs && vm.ui.value.overlay)
        assertEquals("1.2", vm.ui.value.version)
    }

    @Test fun permissionsUiSplitsRequiredFromOptional() = runTest(UnconfinedTestDispatcher()) {
        var g = grants(location = false, battery = false, always = false)
        val repo = PermissionsRepository({ g }, settings, backgroundScope)
        val vm = PermissionsViewModel(repo).also { it.ui.launchIn(backgroundScope) }
        assertEquals(listOf(PermissionId.LOCATION), vm.ui.value.requiredMissing)
        assertEquals(setOf(PermissionId.LOCATION_ALWAYS, PermissionId.BATTERY), vm.ui.value.optionalMissing.toSet())
        g = grants(); vm.refresh()
        assertTrue(vm.ui.value.requiredMissing.isEmpty() && vm.ui.value.optionalMissing.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h900dp")
class SettingsB16cContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun permissions(g: PermissionGrants) = rule.setContent {
        KtmTheme(darkTheme = false) {
            PermissionsContent(PermissionsUi.of(PermissionPolicy.build(g, NavEngine.MIRROR)), PermissionsActions({}, { log += it.name }))
        }
    }

    @Test fun missingRequiredPermissionsGetABannerAndAnAllowButton() {
        permissions(grants(location = false))
        rule.onNodeWithText("1 thing needs attention").assertIsDisplayed()
        rule.onNodeWithText("Maps, speed and rides won't work.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Allow Location").performClick()
        assertEquals(listOf("LOCATION"), log)
    }

    @Test fun grantedRowsSayAllowedAndHaveNoButton() {
        permissions(grants())
        rule.onNodeWithText("Everything the app needs is allowed.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Allow Location").assertDoesNotExist()
    }

    @Test fun notificationAccessAndBatterySayOpen() {
        permissions(grants(access = false, battery = false))
        rule.onNodeWithContentDescription("Open settings for Notification access").assertExists()
        rule.onNodeWithContentDescription("Open settings for Battery: unrestricted").assertExists()
        rule.onAllNodes(hasText("Open") and hasClickAction()).assertCountEquals(2)
    }

    @Test fun anOptionalOneAloneIsOnlyASuggestion() {
        permissions(grants(always = false))
        rule.onNodeWithText("1 thing needs attention").assertIsDisplayed()
        rule.onNodeWithText("Rides won't record after the bike reconnects on its own.").assertIsDisplayed()
    }

    private fun backup(ui: BackupUi) = rule.setContent {
        KtmTheme(darkTheme = false) {
            BackupContent(
                ui,
                BackupActions({}, { log += "backup" }, { log += "restore" }, RestoreActions({ log += "go${it.rides}${it.places}${it.settings}" }, { log += "another" }, { log += "dismiss" })),
            )
        }
    }

    @Test fun twoBigActionsAndTheChecklist() {
        backup(BackupUi())
        rule.onNode(hasText("Back up now") and hasClickAction()).performClick()
        rule.onNode(hasText("Restore from a file") and hasClickAction()).performClick()
        rule.onNodeWithText("Rides, with tracks and notes").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("backup", "restore"), log)
    }

    @Test fun theChecklistSaysInWordsWhatIsAndIsNotInABackup() {
        backup(BackupUi())
        rule.onNodeWithText("Rides, with tracks and notes").performScrollTo()
        fun state(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
        rule.onNode(hasText("Rides, with tracks and notes") and state("Included")).assertExists()
        rule.onNode(hasText("Bike pairing", substring = true) and state("Not included")).assertExists()
    }

    @Test fun restoreIsOffWhileBackingUp() {
        backup(BackupUi(busy = true))
        rule.onNode(hasText("Restore from a file") and hasClickAction()).assertIsNotEnabled()
    }

    @Test fun theSheetOffersWhatIsNewAndRestoresTheChoice() {
        backup(BackupUi(restore = RestoreState.Ready(BackupManager.Preview(3, 2, 0, 4))))
        rule.onNodeWithText("2 new · 1 already here").assertIsDisplayed()
        rule.onNode(hasText("Restore") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals("settings start unticked", listOf("gotruefalsefalse"), log)
    }

    @Test fun theSheetNamesTheFileAndCountsWhatIsAlreadyHere() {
        backup(BackupUi(restore = RestoreState.Ready(BackupManager.Preview(5, 5, 1, 0), "ktm-backup.zip")))
        rule.onNodeWithText("ktm-backup.zip").assertIsDisplayed()
        rule.onNodeWithText("5 new · 0 already here").assertIsDisplayed()
    }

    @Test fun theExplainerShowsNumberedStepsAndTwoButtonsSideBySide() {
        val gone = mutableListOf<String>()
        rule.setContent {
            KtmTheme(darkTheme = false) {
                com.navigator.app.ui.feature.permissions.PermissionExplainerSheet(Explainer.BACKGROUND_LOCATION, { gone += "go" }, { gone += "no" })
            }
        }
        rule.onNodeWithText("Tap Open settings").assertIsDisplayed()
        rule.onNodeWithText("Permissions › Location").assertIsDisplayed()
        rule.onNodeWithText("Choose Allow all the time").assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        rule.onNodeWithText("Open settings").performClick()
        assertEquals(listOf("no", "go"), gone)
    }

    @Test fun aBadFileSaysSoInTheSheetAndOffersAnother() {
        backup(BackupUi(restore = RestoreState.Unreadable))
        rule.onNodeWithText("Couldn't read that file").assertIsDisplayed()
        rule.onNodeWithText("Choose another file").performClick()
        assertEquals(listOf("another"), log)
    }

    private fun about(debug: Boolean) = rule.setContent {
        KtmTheme(darkTheme = false) {
            AboutContent(
                AboutUi("9.9", copyLogs = false, showOverlayToggle = debug, overlay = false),
                AboutActions({}, { log += "logs" }, { log += "copy$it" }, { log += "symbols" }, { log += "calibration" }, { log += "overlay$it" }),
            )
        }
    }

    @Test fun aboutListsTheToolsAndTheVersion() {
        about(debug = false)
        rule.onNodeWithText("View and share logs").performClick()
        rule.onNodeWithText("Symbol testing").performScrollTo().performClick()
        rule.onNodeWithText("9.9").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("logs", "symbols"), log)
    }

    @Test fun theOverlayIsOnlyInDebugBuilds() {
        about(debug = false)
        rule.onNodeWithText("Dash preview overlay").assertDoesNotExist()
    }

    @Test fun debugBuildsGetTheOverlaySwitch() {
        about(debug = true)
        rule.onNodeWithText("Dash preview overlay").performScrollTo().performClick()
        assertEquals(listOf("overlaytrue"), log)
    }
}

