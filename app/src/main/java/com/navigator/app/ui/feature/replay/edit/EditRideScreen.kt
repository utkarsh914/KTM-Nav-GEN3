package com.navigator.app.ui.feature.replay.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmDialog
import com.navigator.app.ui.shell.LocalFeedback
import com.navigator.app.ui.shell.MERGE_OFFER
import com.navigator.app.ui.shell.PopWhenResumed

/**
 * The edit page: wires [EditRideContent] to its ViewModel. [onDeleted] offers Undo (the shell owns the
 * snackbar); [onTrim] opens the trim page. What was typed is saved when the page is left, whichever way.
 */
@Composable
fun EditRideScreen(
    rideId: String,
    onBack: () -> Unit,
    onTrim: () -> Unit,
    onDeleted: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val feedback = LocalFeedback.current
    val vm: EditRideViewModel = viewModel(factory = EditRideViewModel.factory(rideId, container))
    DisposableEffect(vm, feedback) {
        vm.onMergedRideChanged = { feedback.endOffer(MERGE_OFFER + rideId) }
        onDispose { vm.onMergedRideChanged = {} }
    }
    val ui by vm.ui.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is EditEvent.Deleted -> onDeleted(event.ids)
                EditEvent.Gone -> gone = true
            }
        }
    }
    val closed by vm.closed.collectAsState()
    PopWhenResumed(gone || closed, onBack)
    DisposableEffect(vm) { onDispose { vm.flush() } }

    EditRideContent(
        ui = ui, modifier = modifier,
        actions = EditRideActions(
            onBack = onBack, onName = vm::onName, onNotes = vm::onNotes,
            onTrim = { vm.flush(); onTrim() },
            onToggleStar = { vm.toggleStar() },
            onDelete = { if ((ui as? EditUi.Ready)?.saved == true) confirmDelete = true else vm.delete() },
        ),
    )
    if (confirmDelete) {
        KtmDialog(
            title = stringResource(R.string.replay_delete_starred_title),
            message = stringResource(
                R.string.replay_delete_starred_message,
                (ui as? EditUi.Ready)?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.replay_delete_unnamed),
            ),
            onDismiss = { confirmDelete = false },
            confirm = DialogAction(stringResource(R.string.rides_delete_confirm), { confirmDelete = false; vm.delete() }, ButtonStyle.Danger),
            dismiss = DialogAction(stringResource(R.string.replay_delete_keep), { confirmDelete = false }, ButtonStyle.Text),
        )
    }
}
