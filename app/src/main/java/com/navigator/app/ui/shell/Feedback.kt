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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
class Feedback(
    private val scope: CoroutineScope,
    val snackbarHost: SnackbarHostState,
    /** How long a message that offers an action stays, given the usual time (a screen reader may ask for longer). */
    private val actionTimeout: () -> Long = { ACTION_MS },
) {
    var chip by mutableStateOf<RidingChipMessage?>(null)
        private set

    /** How far a screen with its own bottom sheet lifts the snackbar so it sits above it (the replay). */
    var snackbarLift by mutableStateOf(0.dp)

    private var chipJob: Job? = null
    private var chipSeq = 0L

    /** A message with an optional action. With an action it stays 8 s, else about 4 s. [onDismiss] runs when it goes without the action being taken (timed out, swiped away or replaced). */
    fun snackbar(
        message: String, actionLabel: String? = null, onDismiss: (() -> Unit)? = null,
        /** Names an offer so [endOffer] can end this one and no other. */
        tag: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            offerTag = tag
            // An offer with an action stays 8 s (Undo), a plain message about 4 s.
            val result = if (actionLabel == null) {
                snackbarHost.showSnackbar(message = message, duration = SnackbarDuration.Short)
            } else {
                withTimeoutOrNull(actionTimeout()) {
                    snackbarHost.showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Indefinite)
                } ?: SnackbarResult.Dismissed
            }
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke() else onDismiss?.invoke()
        }
    }

    private var offerTag: String? = null

    /** Ends the snackbar tagged [tag], if it is the one on show, as if it timed out (its `onDismiss` runs): the rider moved on from what it offered. */
    fun endOffer(tag: String) {
        if (offerTag == tag) snackbarHost.currentSnackbarData?.dismiss()
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
        const val ACTION_MS = 8_000L
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback> { error("No Feedback provided: wrap the UI in KtmApp") }

@Composable
fun rememberFeedback(): Feedback {
    val scope = rememberCoroutineScope()
    val host = remember { SnackbarHostState() }
    val accessibility = LocalAccessibilityManager.current
    val timeout by rememberUpdatedState { accessibility?.calculateRecommendedTimeoutMillis(Feedback.ACTION_MS, containsIcons = false, containsText = true, containsControls = true) ?: Feedback.ACTION_MS }
    return remember(scope, host) { Feedback(scope, host, actionTimeout = { timeout() }) }
}
