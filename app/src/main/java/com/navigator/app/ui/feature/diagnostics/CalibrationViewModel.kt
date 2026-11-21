package com.navigator.app.ui.feature.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.logging.AppLogger
import com.navigator.app.notifications.IconHashCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A turn icon Google Maps showed that the app couldn't match, saved as an image. */
data class CapturedIcon(val path: String, val hash: Int)

data class CalibrationRow(
    val hash: Int,
    val path: String,
    val seen: Int,
    val assigned: TurnIcon?,
    val expanded: Boolean,
)

data class CalibrationUi(
    val rows: List<CalibrationRow> = emptyList(),
    /** What can be picked, the commonest first; icons already confirmed on this dash are in [working]. */
    val candidates: List<TurnIcon> = emptyList(),
    val working: Set<TurnIcon> = emptySet(),
    /** The icons have been read; until then the page shows nothing rather than "no icons". */
    val loaded: Boolean = false,
)

interface CalibrationPorts {
    fun captured(): List<CapturedIcon>
    fun seen(hash: Int): Int
    fun assigned(hash: Int): String?
    fun assign(hash: Int, icon: TurnIcon)
    fun clear(hash: Int)
    fun works(icon: TurnIcon): Boolean
    fun preview(icon: TurnIcon)
}

object CalibrationCandidates {
    private val COMMON = listOf(
        TurnIcon.GO_STRAIGHT,
        TurnIcon.LIGHT_LEFT, TurnIcon.QUITE_LEFT, TurnIcon.HEAVY_LEFT, TurnIcon.UTURN_LEFT, TurnIcon.KEEP_LEFT,
        TurnIcon.LIGHT_RIGHT, TurnIcon.QUITE_RIGHT, TurnIcon.HEAVY_RIGHT, TurnIcon.UTURN_RIGHT, TurnIcon.KEEP_RIGHT,
        TurnIcon.ENTER_HIGHWAY_RIGHT_LANE, TurnIcon.ENTER_HIGHWAY_LEFT_LANE,
        TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE, TurnIcon.LEAVE_HIGHWAY_LEFT_LANE,
        TurnIcon.RAB_SECT_1_RH, TurnIcon.RAB_SECT_2_RH, TurnIcon.RAB_SECT_3_RH, TurnIcon.RAB_SECT_4_RH,
        TurnIcon.START, TurnIcon.END, TurnIcon.CHANGE_LINE, TurnIcon.HEAD_TO,
    )

    /** The maneuvers riders hit most and the pixel heuristic can't infer first, then every other symbol. */
    fun ordered(): List<TurnIcon> = COMMON + TurnIcon.entries.filter { it !in COMMON }
}

/** DG3: label each captured Maps icon once with the matching dash symbol; it is exact and permanent afterwards. */
class CalibrationViewModel(
    private val ports: CalibrationPorts,
    /** The icon files and the settings are read here, off the main thread. */
    io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val captured = MutableStateFlow<List<CapturedIcon>?>(null)
    private val assignments = MutableStateFlow(emptyMap<Int, String?>())
    private val seen = MutableStateFlow(emptyMap<Int, Int>())
    private val working = MutableStateFlow(emptySet<TurnIcon>())
    private val open = MutableStateFlow<Set<Int>>(emptySet())
    private val candidates = CalibrationCandidates.ordered()

    init {
        viewModelScope.launch(io) {
            val icons = ports.captured()
            seen.value = icons.associate { it.hash to ports.seen(it.hash) }
            assignments.value = icons.associate { it.hash to ports.assigned(it.hash) }
            working.value = candidates.filter(ports::works).toSet()
            captured.value = icons // last: the rows appear with everything they show
        }
    }

    val ui: StateFlow<CalibrationUi> = combine(captured, assignments, open, seen, working) { icons, assigned, open, seen, working ->
        val rows = icons.orEmpty().map { icon ->
            CalibrationRow(
                icon.hash, icon.path, seen[icon.hash] ?: 0,
                assigned[icon.hash]?.let { name -> TurnIcon.entries.firstOrNull { it.name == name } },
                expanded = icon.hash in open,
            )
        }
        CalibrationUi(rows, candidates, working, loaded = icons != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalibrationUi())

    fun toggle(hash: Int) = open.update { if (hash in it) it - hash else it + hash }

    fun assign(hash: Int, icon: TurnIcon) {
        ports.assign(hash, icon)
        AppLogger.log("IconCache", "Calibrated hash=$hash -> ${icon.name}")
        // Sent live so the rider can see it on the dash before moving on.
        ports.preview(icon)
        assignments.update { it + (hash to icon.name) }
        open.update { it - hash }
    }

    fun clear(hash: Int) {
        ports.clear(hash)
        assignments.update { it + (hash to null) }
    }

    companion object {
        fun factory(container: AppContainer, context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                val store = container.settings.legacy
                return CalibrationViewModel(object : CalibrationPorts {
                    override fun captured() = IconHashCache.savedIcons(app).map { (file, hash) -> CapturedIcon(file.path, hash) }
                    override fun seen(hash: Int) = IconHashCache.seenCount(app, hash)
                    override fun assigned(hash: Int) = store.getTurnCalibration(hash)
                    override fun assign(hash: Int, icon: TurnIcon) = store.setTurnCalibration(hash, icon.name)
                    override fun clear(hash: Int) = store.clearTurnCalibration(hash)
                    override fun works(icon: TurnIcon) = store.getIconTestResult("turn_${icon.name}") == "works"
                    override fun preview(icon: TurnIcon) = BccuConnectionService.sendTurnIconIfRunning(icon)
                }) as T
            }
        }
    }
}
