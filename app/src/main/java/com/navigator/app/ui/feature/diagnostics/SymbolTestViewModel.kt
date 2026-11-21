package com.navigator.app.ui.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.ble.BccuProtocol
import com.navigator.app.logging.AppLogger
import com.navigator.app.ui.components.TurnIconNames
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class SymbolKind { TURN, NOTIFICATION }

enum class SymbolResult(val stored: String) {
    WORKS("works"), WRONG("wrong");

    companion object {
        fun of(stored: String?): SymbolResult? = entries.firstOrNull { it.stored == stored }
    }
}

/** What a test row shows: a human [name] above the protocol [code], and where the rider got to with it. */
data class SymbolRow(
    val key: String,
    val name: String,
    val code: String,
    val turn: BccuProtocol.TurnIcon?,
    val result: SymbolResult?,
    /** Sent and waiting for the rider's Works / Wrong. */
    val expanded: Boolean,
)

data class SymbolTestUi(
    val kind: SymbolKind = SymbolKind.TURN,
    val rows: List<SymbolRow> = emptyList(),
    val works: Int = 0,
    val wrong: Int = 0,
    val showInfo: Boolean = false,
)

/** Where results are kept, and how a symbol reaches the dash. */
interface SymbolTestPorts {
    fun result(key: String): String?
    fun saveResult(key: String, value: String)
    fun sendTurn(icon: BccuProtocol.TurnIcon)
    fun sendNotification(icon: BccuProtocol.NotificationIcon)

    /** False when nothing would reach the dash, so a result can't be recorded for a symbol that was never shown. */
    fun dashConnected(): Boolean
}

/** DG2: send each dash symbol, look at the dash, mark Works or Wrong. Results persist across sessions. */
class SymbolTestViewModel(private val ports: SymbolTestPorts) : ViewModel() {
    private val kind = MutableStateFlow(SymbolKind.TURN)
    private val expanded = MutableStateFlow<String?>(null)
    private val showInfo = MutableStateFlow(false)
    private val results = MutableStateFlow(loadResults())
    private val _notSent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a Send found no dash connection. */
    val notSent: SharedFlow<Unit> = _notSent

    private fun keyOf(icon: BccuProtocol.TurnIcon) = "turn_${icon.name}"
    private fun keyOf(icon: BccuProtocol.NotificationIcon) = "notif_${icon.name}"

    private fun loadResults(): Map<String, SymbolResult> = buildMap {
        BccuProtocol.TurnIcon.entries.forEach { i -> SymbolResult.of(ports.result(keyOf(i)))?.let { put(keyOf(i), it) } }
        BccuProtocol.NotificationIcon.entries.forEach { i -> SymbolResult.of(ports.result(keyOf(i)))?.let { put(keyOf(i), it) } }
    }

    val ui: StateFlow<SymbolTestUi> = combine(kind, expanded, results, showInfo) { kind, open, results, info ->
        val rows = when (kind) {
            SymbolKind.TURN -> BccuProtocol.TurnIcon.entries.map {
                SymbolRow(keyOf(it), TurnIconNames.human(it.name), it.name, it, results[keyOf(it)], open == keyOf(it))
            }
            SymbolKind.NOTIFICATION -> BccuProtocol.NotificationIcon.entries.map {
                SymbolRow(keyOf(it), TurnIconNames.human(it.name), it.name, null, results[keyOf(it)], open == keyOf(it))
            }
        }
        SymbolTestUi(kind, rows, rows.count { it.result == SymbolResult.WORKS }, rows.count { it.result == SymbolResult.WRONG }, info)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SymbolTestUi())

    fun selectKind(value: SymbolKind) { kind.value = value; expanded.value = null }
    fun showInfo(show: Boolean) { showInfo.value = show }

    fun send(row: SymbolRow) {
        if (!ports.dashConnected()) { _notSent.tryEmit(Unit); return }
        AppLogger.log("SymbolTest", "Sending ${row.key}")
        val turn = row.turn
        if (turn != null) ports.sendTurn(turn)
        else BccuProtocol.NotificationIcon.entries.firstOrNull { keyOf(it) == row.key }?.let(ports::sendNotification)
        expanded.value = row.key
    }

    fun mark(row: SymbolRow, result: SymbolResult) {
        ports.saveResult(row.key, result.stored)
        results.update { it + (row.key to result) }
        expanded.value = null
        AppLogger.log("SymbolTest", "Result: ${row.key} = ${result.name}")
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val store = container.settings.legacy
                return SymbolTestViewModel(object : SymbolTestPorts {
                    override fun result(key: String) = store.getIconTestResult(key)
                    override fun saveResult(key: String, value: String) = store.setIconTestResult(key, value)
                    override fun dashConnected() = BccuConnectionService.connectionState.value == BccuConnectionService.ConnectionState.AUTHENTICATED
                    override fun sendTurn(icon: BccuProtocol.TurnIcon) {
                        // Light the guidance view so icons render without a trip, then send the icon with its name and code.
                        BccuConnectionService.setNavStateIfRunning(guidanceOn = true, gpsIconOn = true)
                        BccuConnectionService.sendTurnIconIfRunning(icon)
                        BccuConnectionService.sendGuidanceIfRunning("code=${icon.binary}", icon.name)
                    }
                    override fun sendNotification(icon: BccuProtocol.NotificationIcon) =
                        BccuConnectionService.sendNotificationIfRunning(icon.name, icon)
                }) as T
            }
        }
    }
}
