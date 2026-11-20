package com.navigator.app.ui.feature.diagnostics

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.ble.BccuProtocol.NotificationIcon
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
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
import org.robolectric.annotation.Config

class LogEntriesTest {
    @Test fun aLineSplitsIntoTimeTagAndMessage() {
        val e = LogEntries.parse("[12:30:01.123] [BLE] Connected to dash")
        assertEquals("12:30:01.123", e.time); assertEquals("BLE", e.tag); assertEquals("Connected to dash", e.message)
    }

    @Test fun anUnformattedLineKeepsItsText() {
        val e = LogEntries.parse("just some text")
        assertEquals("", e.time); assertEquals("", e.tag); assertEquals("just some text", e.message)
    }

    @Test fun aMultiLineMessageStaysWhole() {
        assertEquals("boom\nat Foo.bar", LogEntries.parse("[1:00:00.000] [CRASH] boom\nat Foo.bar").message)
    }

    @Test fun failuresAreWarningsAndSuccessesAreOk() {
        assertEquals(LogLevel.Warn, LogEntries.parse("[t] [BLE] !! write failed").level)
        assertEquals(LogLevel.Warn, LogEntries.parse("[t] [CRASH] anything").level)
        assertEquals(LogLevel.Warn, LogEntries.parse("[t] [Nav] Permission denied").level)
        assertEquals(LogLevel.Ok, LogEntries.parse("[t] [BLE] Authenticated").level)
        assertEquals(LogLevel.Ok, LogEntries.parse("[t] [SymbolTest] Result: turn_GO_STRAIGHT = WORKS").level)
        assertEquals(LogLevel.Info, LogEntries.parse("[t] [BLE] Scanning").level)
        assertEquals("a quoted road name is content, not a warning", LogLevel.Info, LogEntries.parse("[t] [Nav] Road: \"Error Street\"").level)
        assertEquals("only a leading marker counts", LogLevel.Info, LogEntries.parse("[t] [Msg] Hello!!").level)
        assertEquals(LogLevel.Warn, LogEntries.parse("[t] [BLE] write failed after \"x\"").level)
        assertEquals("a word that merely contains fail is not a failure", LogLevel.Info, LogEntries.parse("[t] [X] failsafe armed").level)
    }

    @Test fun tagsAreOrderedByHowOftenTheyAppear() {
        val all = listOf("[t] [BLE] a", "[t] [Nav] b", "[t] [BLE] c", "no tag", "[t] [Nav] d", "[t] [BLE] e").map(LogEntries::parse)
        assertEquals("A to Z, so chips never move", listOf("BLE", "Nav"), LogEntries.tagsOf(all))
    }

    @Test fun filteringMatchesTagAndTextIgnoringCase() {
        val all = listOf("[t] [BLE] Scanning", "[t] [Nav] Route ready", "[t] [BLE] Connected").map(LogEntries::parse)
        assertEquals(2, LogEntries.filter(all, "BLE", "").size)
        assertEquals(listOf("Route ready"), LogEntries.filter(all, null, "ROUTE").map { it.message })
        assertEquals(listOf("Connected"), LogEntries.filter(all, "BLE", " conn ").map { it.message })
        assertEquals(3, LogEntries.filter(all, null, "").size)
        assertEquals("the tag itself matches a query", 2, LogEntries.filter(all, null, "ble").size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticsViewModelsTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun logsFilterByChipAndQuery() = runTest(UnconfinedTestDispatcher()) {
        val lines = MutableStateFlow(listOf("[t] [BLE] one", "[t] [Nav] two"))
        val vm = LogsViewModel(lines, UnconfinedTestDispatcher()).also { it.ui.launchIn(backgroundScope) }
        assertEquals(listOf("BLE", "Nav"), vm.ui.value.tags.sorted())
        vm.selectTag("Nav")
        assertEquals(listOf("two"), vm.ui.value.entries.map { it.message })
        vm.setQuery("zzz")
        assertTrue(vm.ui.value.filteredOut)
        vm.setQuery("")
        lines.value = listOf("[t] [BLE] three")
        assertNull("a chip whose subsystem left the buffer is dropped", vm.ui.value.tag)
        assertEquals(listOf("three"), vm.ui.value.entries.map { it.message })
        assertFalse(vm.ui.value.filteredOut)
    }

    private class Ports : SymbolTestPorts {
        val saved = mutableMapOf<String, String>()
        val sentTurns = mutableListOf<TurnIcon>()
        val sentNotes = mutableListOf<NotificationIcon>()
        var connected = true
        override fun result(key: String) = saved[key]
        override fun saveResult(key: String, value: String) { saved[key] = value }
        override fun sendTurn(icon: TurnIcon) { sentTurns += icon }
        override fun sendNotification(icon: NotificationIcon) { sentNotes += icon }
        override fun dashConnected() = connected
    }

    @Test fun sendingExpandsTheRowAndMarkingSavesAndCollapsesIt() = runTest(UnconfinedTestDispatcher()) {
        val ports = Ports().also { it.saved["turn_UTURN_LEFT"] = "wrong" }
        val vm = SymbolTestViewModel(ports).also { it.ui.launchIn(backgroundScope) }
        assertEquals(SymbolResult.WRONG, vm.ui.value.rows.first { it.code == "UTURN_LEFT" }.result)
        assertEquals(1, vm.ui.value.wrong)
        val row = vm.ui.value.rows.first { it.code == "GO_STRAIGHT" }
        assertEquals("Go straight", row.name)
        vm.send(row)
        assertEquals(listOf(TurnIcon.GO_STRAIGHT), ports.sentTurns)
        assertTrue(vm.ui.value.rows.first { it.code == "GO_STRAIGHT" }.expanded)
        vm.mark(row, SymbolResult.WORKS)
        assertEquals("works", ports.saved["turn_GO_STRAIGHT"])
        assertFalse(vm.ui.value.rows.any { it.expanded })
        assertEquals(1, vm.ui.value.works)
    }

    @Test fun theNotificationListSendsBannerIconsAndSwitchingKindCollapses() = runTest(UnconfinedTestDispatcher()) {
        val ports = Ports()
        val vm = SymbolTestViewModel(ports).also { it.ui.launchIn(backgroundScope) }
        vm.selectKind(SymbolKind.NOTIFICATION)
        assertEquals(NotificationIcon.entries.size, vm.ui.value.rows.size)
        assertTrue(vm.ui.value.rows.all { it.turn == null && it.key.startsWith("notif_") })
        vm.send(vm.ui.value.rows.first { it.code == "GPS_LOST" })
        assertEquals(listOf(NotificationIcon.GPS_LOST), ports.sentNotes)
        vm.selectKind(SymbolKind.TURN)
        assertFalse(vm.ui.value.rows.any { it.expanded })
        assertTrue(ports.sentTurns.isEmpty())
    }

    @Test fun withoutADashNothingIsSentOrExpandedAndTheRiderIsTold() = runTest(UnconfinedTestDispatcher()) {
        val ports = Ports().also { it.connected = false }
        val vm = SymbolTestViewModel(ports).also { it.ui.launchIn(backgroundScope) }
        var told = 0
        backgroundScope.launch { vm.notSent.collect { told++ } }
        vm.send(vm.ui.value.rows.first())
        assertTrue(ports.sentTurns.isEmpty())
        assertFalse(vm.ui.value.rows.any { it.expanded })
        assertEquals(1, told)
    }

    @Test fun symbolNamesReadAsWordsAndRoundaboutExitsAreNumbered() {
        assertEquals("Roundabout exit 13 (RH)", SymbolNames.human("RAB_SECT_13_RH"))
        assertEquals("U-turn left", SymbolNames.human("UTURN_LEFT"))
        assertEquals("Go straight", SymbolNames.human("GO_STRAIGHT"))
    }

    private class CalPorts : CalibrationPorts {
        val labels = mutableMapOf<Int, String>()
        val previewed = mutableListOf<TurnIcon>()
        override fun captured() = listOf(CapturedIcon("/none/a.png", 11), CapturedIcon("/none/b.png", 22))
        override fun seen(hash: Int) = if (hash == 11) 14 else 0
        override fun assigned(hash: Int) = labels[hash]
        override fun assign(hash: Int, icon: TurnIcon) { labels[hash] = icon.name }
        override fun clear(hash: Int) { labels.remove(hash) }
        override fun works(icon: TurnIcon) = icon == TurnIcon.UTURN_LEFT
        override fun preview(icon: TurnIcon) { previewed += icon }
    }

    @Test fun calibrationLabelsOnceSendsItAndCollapsesTheRow() = runTest(UnconfinedTestDispatcher()) {
        val ports = CalPorts().also { it.labels[22] = "KEEP_LEFT" }
        val vm = CalibrationViewModel(ports).also { it.ui.launchIn(backgroundScope) }
        assertEquals(1, vm.ui.value.rows.count { it.assigned == null })
        assertEquals(setOf(TurnIcon.UTURN_LEFT), vm.ui.value.working)
        assertEquals(TurnIcon.KEEP_LEFT, vm.ui.value.rows.first { it.hash == 22 }.assigned)
        assertEquals(14, vm.ui.value.rows.first { it.hash == 11 }.seen)
        vm.toggle(11)
        assertTrue(vm.ui.value.rows.first { it.hash == 11 }.expanded)
        vm.assign(11, TurnIcon.UTURN_LEFT)
        assertEquals("UTURN_LEFT", ports.labels[11])
        assertEquals(listOf(TurnIcon.UTURN_LEFT), ports.previewed)
        assertFalse(vm.ui.value.rows.first { it.hash == 11 }.expanded)
        assertEquals(0, vm.ui.value.rows.count { it.assigned == null })
        vm.clear(11)
        assertNull(ports.labels[11])
        assertEquals(1, vm.ui.value.rows.count { it.assigned == null })
    }

    @Test fun everySymbolIsACandidateAndTheCommonOnesComeFirst() {
        val order = CalibrationCandidates.ordered()
        assertEquals(TurnIcon.entries.toSet(), order.toSet())
        assertEquals(TurnIcon.entries.size, order.size)
        assertEquals(TurnIcon.GO_STRAIGHT, order.first())
    }
}

class DebugOverlayRulesTest {
    @Test fun ridingScreensHideItUnlessPinned() {
        assertTrue(DebugOverlayRules.visible(enabled = true, riding = false, pinned = false))
        assertFalse(DebugOverlayRules.visible(enabled = true, riding = true, pinned = false))
        assertTrue(DebugOverlayRules.visible(enabled = true, riding = true, pinned = true))
        assertFalse(DebugOverlayRules.visible(enabled = false, riding = false, pinned = true))
    }

    @Test fun droppingItSnapsToTheNearestCorner() {
        assertEquals(OverlayCorner.TopStart, DebugOverlayRules.nearest(100f, 100f, 1000f, 2000f))
        assertEquals(OverlayCorner.TopEnd, DebugOverlayRules.nearest(900f, 300f, 1000f, 2000f))
        assertEquals(OverlayCorner.BottomStart, DebugOverlayRules.nearest(200f, 1800f, 1000f, 2000f))
        assertEquals(OverlayCorner.BottomEnd, DebugOverlayRules.nearest(600f, 1200f, 1000f, 2000f))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h900dp")
class DiagnosticsContentTest {
    @get:Rule val rule = createComposeRule()

    @Test fun logsShowPartsChipsAndTheFollowPill() {
        var tag: String? = "x"
        var shared = false
        val ui = LogsUi(
            entries = listOf(LogEntries.parse("[12:30:01.123] [BLE] Scanning"), LogEntries.parse("[12:30:02.000] [BLE] !! write failed")),
            tags = listOf("BLE", "Nav"),
        )
        rule.setContent { KtmTheme(darkTheme = true) { LogsContent(ui, LogsActions({}, { shared = true }, { tag = it }, {})) } }
        rule.onNodeWithText("12:30:01.123").assertIsDisplayed()
        rule.onNodeWithText("Scanning").assertIsDisplayed()
        rule.onNodeWithText("Following live").assertIsDisplayed()
        rule.onNodeWithText("Following live").performClick()
        rule.onNodeWithText("Paused").assertIsDisplayed()
        rule.onNode(hasText("Nav") and hasClickAction()).performClick()
        assertEquals("Nav", tag)
        rule.onNode(hasText("All") and hasClickAction()).performClick()
        assertNull(tag)
        rule.onNodeWithContentDescription("Share log").performClick()
        assertTrue(shared)
    }

    @Test fun logsSaySoWhenTheFilterHidesEverything() {
        rule.setContent { KtmTheme(darkTheme = true) { LogsContent(LogsUi(filteredOut = true), LogsActions({}, {}, {}, {})) } }
        rule.onNodeWithText("No matching lines").assertIsDisplayed()
    }

    @Test fun aSentSymbolAsksWorksOrWrong() {
        val marked = mutableListOf<Pair<String, SymbolResult>>()
        val sent = mutableListOf<String>()
        val row = SymbolRow("turn_GO_STRAIGHT", "Go straight", "GO_STRAIGHT", TurnIcon.GO_STRAIGHT, null, expanded = true)
        val other = SymbolRow("turn_START", "Start", "START", TurnIcon.START, SymbolResult.WORKS, expanded = false)
        rule.setContent {
            KtmTheme(darkTheme = true) {
                SymbolTestContent(
                    SymbolTestUi(rows = listOf(row, other), works = 1), SymbolTestActions({}, {}, {}, { sent += it.key }, { r, v -> marked += r.key to v }),
                )
            }
        }
        rule.onNodeWithText("Does the dash show this symbol?").assertIsDisplayed()
        rule.onNode(hasText("Wrong") and hasClickAction()).performClick()
        rule.onNode(hasText("Works") and hasClickAction()).performClick()
        assertEquals(listOf("turn_GO_STRAIGHT" to SymbolResult.WRONG, "turn_GO_STRAIGHT" to SymbolResult.WORKS), marked)
        rule.onAllNodes(hasText("Send") and hasClickAction()).assertCountEquals(2)
        rule.onAllNodes(hasText("Send") and hasClickAction())[1].performClick()
        assertEquals(listOf("turn_START"), sent)
    }

    @Test fun calibrationShowsSeenCountsAndOpensOnTap() {
        var toggled = -1
        val rows = listOf(
            CalibrationRow(11, "/none/a.png", 14, null, expanded = false),
            CalibrationRow(22, "/none/b.png", 1, TurnIcon.KEEP_LEFT, expanded = false),
        )
        rule.setContent {
            KtmTheme(darkTheme = true) {
                CalibrationContent(
                    CalibrationUi(rows, listOf(TurnIcon.GO_STRAIGHT), emptySet()), CalibrationActions({}, { toggled = it }, { _, _ -> }, {}),
                )
            }
        }
        rule.onNodeWithText("Seen 14 times").assertIsDisplayed()
        rule.onNodeWithText("Seen 1 time").assertIsDisplayed()
        rule.onNodeWithText("Not labelled").performClick()
        assertEquals(11, toggled)
    }

    @Test fun calibrationWithNothingCapturedExplainsHow() {
        rule.setContent { KtmTheme(darkTheme = true) { CalibrationContent(CalibrationUi(), CalibrationActions({}, {}, { _, _ -> }, {})) } }
        rule.onNodeWithText("No turn icons yet").assertIsDisplayed()
    }
}
