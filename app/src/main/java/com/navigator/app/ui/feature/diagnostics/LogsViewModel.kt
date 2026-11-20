package com.navigator.app.ui.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.logging.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class LogsUi(
    val entries: List<LogEntry> = emptyList(),
    /** Every subsystem seen so far, for the chips. */
    val tags: List<String> = emptyList(),
    val tag: String? = null,
    val query: String = "",
    /** Lines exist but the filter hides them all. */
    val filteredOut: Boolean = false,
)

/** DG1: the live log, split into parts and filtered by subsystem and text. */
class LogsViewModel(lines: Flow<List<String>>, parsing: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    private val tag = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    val ui: StateFlow<LogsUi> = combine(lines, tag, query) { raw, tag, query ->
        // A key per line that outlives the buffer sliding, so a paused reader keeps their place and new lines can fade in.
        val seen = HashMap<String, Int>()
        val all = raw.map { line ->
            val n = seen.merge(line, 1, Int::plus) ?: 1
            LogEntries.parse(line).copy(key = "$line#$n")
        }
        val tags = LogEntries.tagsOf(all)
        // A chip whose subsystem fell out of the buffer can no longer be shown.
        val active = tag?.takeIf { it in tags }
        val shown = LogEntries.filter(all, active, query)
        LogsUi(shown, tags, active, query, filteredOut = all.isNotEmpty() && shown.isEmpty())
    }.flowOn(parsing).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogsUi())

    fun selectTag(value: String?) { tag.value = value }
    fun setQuery(value: String) { query.value = value }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LogsViewModel(AppLogger.lines) as T
        }
    }
}
