package com.navigator.app.ui.feature.pairing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
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
        onRetry = { log += "retry" }, onTrouble = { log += "trouble" }, onPairLater = if (later) ({ log += "later" }) else null,
        onFixBlock = { log += "fix $it" }, onDebugSkip = { log += "debug" },
    )

    private fun show(ui: PairingUi, actions: PairingActions = actions()) =
        rule.setContent { KtmTheme(darkTheme = false) { PairingContent(ui, actions) } }

    private val ktm = ScannedBike("AA:01", "KTM 1290", -50, pairedBefore = false)
    private val old = ScannedBike("AA:02", "KTM 390", null, pairedBefore = true)
    private val headset = ScannedBike("BB:02", "Headset", -90, pairedBefore = false)

    @Test fun theListShowsBikesWithRealSignalWordsAndPairedBefore() {
        show(PairingUi(PairingPhase.Scanning, bikes = listOf(ktm, old), others = listOf(headset)))
        rule.onNodeWithText("Very close").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Paired before").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("KTM 1290").performScrollTo().performClick()
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

    @Test fun havingTroubleIsInTheHeader() {
        show(PairingUi(PairingPhase.Scanning))
        rule.onNodeWithContentDescription("Having trouble?").performClick()
        assertEquals(listOf("trouble"), log)
    }

    @Test fun confirmingHasATimerNoBackAndACancel() {
        val bike = Attempt("AA:01", "KTM 1290", 0)
        show(PairingUi(PairingPhase.Confirming(bike, 65, securing = false)))
        rule.onNodeWithText("1:05").assertIsDisplayed()
        rule.onNodeWithText("Waiting for the dash").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
        rule.onNodeWithContentDescription("Having trouble?").assertDoesNotExist()
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

    @Test fun failedExplainsTheCausesAndTriesAgain() {
        show(PairingUi(PairingPhase.Failed))
        rule.onNodeWithText("Couldn't pair").assertIsDisplayed()
        rule.onNodeWithText("Bluetooth is on in Android.").performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("Try again") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("retry"), log)
    }
}
