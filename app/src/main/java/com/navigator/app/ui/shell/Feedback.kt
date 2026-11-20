package com.navigator.app.ui.shell

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ok: done. Warn: degraded. Bad: needs the rider. Info: neutral. */
enum class ChipTone { Ok, Warn, Bad, Info }

/** A short message shown over the map while riding: one at a time, the newest wins. */
data class RidingChipMessage(val id: Long, val text: String, val tone: ChipTone, val onTap: (() -> Unit)? = null)

/**
 * The two feedback patterns, one entry point (no toasts):
 * - [snackbar] at rest: one optional action, shown above the tab bar or activity strip;
 * - [chip] while riding: large, timed, rendered by the riding screens (state only here).
 * Failures that belong to a control are shown inline by that control instead.
 */
@Stable
class Feedback(private val scope: CoroutineScope, val snackbarHost: SnackbarHostState) {
    var chip by mutableStateOf<RidingChipMessage?>(null)
        private set

    private var chipJob: Job? = null
    private var chipSeq = 0L

    /** A message with an optional action. With an action it stays longer (about 10 s), else about 4 s. */
    fun snackbar(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            val result = snackbarHost.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    fun chip(text: String, tone: ChipTone = ChipTone.Info, onTap: (() -> Unit)? = null) {
        val message = RidingChipMessage(++chipSeq, text, tone, onTap)
        chip = message
        chipJob?.cancel()
        chipJob = scope.launch {
            delay(CHIP_MS)
            if (chip?.id == message.id) chip = null
        }
    }

    companion object {
        const val CHIP_MS = 4_000L
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback> { error("No Feedback provided: wrap the UI in KtmApp") }

@Composable
fun rememberFeedback(): Feedback {
    val scope = rememberCoroutineScope()
    val host = remember { SnackbarHostState() }
    return remember(scope, host) { Feedback(scope, host) }
}
