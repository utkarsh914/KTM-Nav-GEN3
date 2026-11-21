package com.navigator.app.ui.feature.pairing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.navigator.app.ble.ScannedBike
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h900dp")
class PairingContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun actions(back: Boolean = true, later: Boolean = true) = PairingActions(
        onBack = if (back) ({ log += "back" }) else null,
        onPick = { log += "pick ${it.address}" }, onScanAgain = { log += "scan" }, onCancel = { log += "cancel" },
        onRetry = { log += "retry" }, onViewLogs = { log += "logs" }, onShareLog = { log += "share" },
        onBluetoothSettings = { log += "bt" }, onPairLater = if (later) ({ log += "later" }) else null,
        onFixBlock = { log += "fix $it" }, onDebugSkip = { log += "debug" },
    )

    private fun show(ui: PairingUi, actions: PairingActions = actions(), progress: Pair<Int, Int>? = null) =
        rule.setContent { KtmTheme(darkTheme = false) { PairingContent(ui, actions, progress = progress) } }

    private val ktm = ScannedBike("AA:01", "KTM 1290", -50, pairedBefore = false)
    private val old = ScannedBike("AA:02", "KTM 390", null, pairedBefore = true)
    private val headset = ScannedBike("BB:02", "Headset", -90, pairedBefore = false)

    @Test fun theListShowsBikesWithRealSignalWordsAndPairedBefore() {
        show(PairingUi(PairingPhase.Scanning, bikes = listOf(ktm, old), others = listOf(headset)))
        rule.onNodeWithText("Very close").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Paired before").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("KTM 1290").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("AA:01").assertDoesNotExist()
        rule.onNode(hasContentDescription("Pair with KTM 1290") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("pick AA:01"), log)
    }

    @Test fun otherDevicesAreFoldedUntilOpened() {
        show(PairingUi(PairingPhase.Scanning, bikes = listOf(ktm), others = listOf(headset)))
        rule.onNodeWithText("Headset").assertDoesNotExist()
        rule.onNodeWithText("Other device (1)").performScrollTo().performClick()
        rule.onNodeWithText("Headset").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Headset").performClick()
        assertEquals(listOf("pick BB:02"), log)
    }

    @Test fun scanAgainAndTheDebugSkip() {
        show(PairingUi(PairingPhase.Scanning, debugSkip = true))
        rule.onNode(hasText("Scan again") and hasClickAction()).performScrollTo().performClick()
        rule.onNodeWithText("Skip pairing (debug build)").performScrollTo().performClick()
        assertEquals(listOf("scan", "debug"), log)
    }

    @Test fun noBikeFoundSaysSo() {
        show(PairingUi(PairingPhase.Scanning))
        rule.onNodeWithText("No bike found yet. Scan again with the ignition on.").performScrollTo().assertIsDisplayed()
    }

    @Test fun havingTroubleIsAtTheFootAndOpensThreeSteps() {
        show(PairingUi(PairingPhase.Scanning))
        rule.onNode(hasText("Having trouble?") and hasClickAction()).performClick()
        rule.onNodeWithText("Keep the phone within a metre of the dash while you accept.").assertIsDisplayed()
        rule.onNodeWithText("Try again").assertDoesNotExist()
        rule.onNode(hasText("Share log") and hasClickAction()).performClick()
        rule.onNode(hasText("View log") and hasClickAction()).performClick()
        assertEquals(listOf("share", "logs"), log)
    }

    @Test fun theChecklistTicksBluetoothAndNeverShowsAnAddress() {
        show(PairingUi(PairingPhase.Scanning, bikes = listOf(ktm), scanning = true))
        rule.onNodeWithText("Bluetooth is on").assertIsDisplayed()
        rule.onNodeWithText("Nearby bikes").assertIsDisplayed()
        rule.onNodeWithText("Scanning").assertIsDisplayed()
        rule.onNodeWithText("Scan again").assertDoesNotExist()
    }

    @Test fun theFirstRunBarShowsItsLastSegment() {
        show(PairingUi(PairingPhase.Scanning), progress = 4 to 4)
        rule.onNodeWithContentDescription("Step 4 of 4").assertIsDisplayed()
    }

    @Test fun confirmingHasATimerNoBackAndACancel() {
        val bike = Attempt("AA:01", "KTM 1290", 0)
        show(PairingUi(PairingPhase.Confirming(bike, 65, securing = false)))
        rule.onNodeWithText("Pair with ${android.os.Build.MODEL}?").assertIsDisplayed()
        rule.onNodeWithText("Found KTM 1290").assertIsDisplayed()
        rule.onNodeWithText("Waiting for you on the dash · 1:05").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
        rule.onNode(hasText("Cancel") and hasClickAction()).performClick()
        assertEquals(listOf("cancel"), log)
    }

    @Test fun securingSaysSoInPlainWords() {
        show(PairingUi(PairingPhase.Confirming(Attempt("AA", null, 0), 5, securing = true)))
        rule.onNodeWithText("Securing the connection").assertIsDisplayed()
    }

    @Test fun blockedForPermissionAllowsThenOffersPairLater() {
        show(PairingUi(PairingPhase.Blocked(BlockReason.NO_PERMISSION)))
        rule.onNode(hasText("Allow Nearby devices") and hasClickAction()).performClick()
        rule.onNodeWithText("Pair later").performClick()
        assertEquals(listOf("fix NO_PERMISSION", "later"), log)
    }

    @Test fun blockedForBluetoothOffTurnsItOn() {
        show(PairingUi(PairingPhase.Blocked(BlockReason.BLUETOOTH_OFF)), actions(later = false))
        rule.onNode(hasText("Turn on Bluetooth") and hasClickAction()).performClick()
        rule.onNodeWithText("Pair later").assertDoesNotExist()
        assertEquals(listOf("fix BLUETOOTH_OFF"), log)
    }

    @Test fun failedOpensTheHelpWhichTriesAgain() {
        show(PairingUi(PairingPhase.Failed))
        rule.onNodeWithText("Couldn't pair").assertIsDisplayed()
        rule.onNodeWithText("Ignition on, and the dash on its main screen (not a menu).").assertIsDisplayed()
        rule.onNode(hasText("Try again") and hasClickAction()).performClick()
        assertEquals(listOf("retry"), log)
    }

    @Test fun closingTheFailureHelpLeavesTryAgainAndPairLaterOnThePage() {
        show(PairingUi(PairingPhase.Failed))
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNode(hasText("Pair later") and hasClickAction()).assertIsDisplayed()
        rule.onNode(hasText("Try again") and hasClickAction()).performClick()
        assertEquals(listOf("retry"), log)
    }
}
