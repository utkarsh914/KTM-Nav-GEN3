package com.navigator.app.ui.feature.diagnostics

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.logging.AppLogger
import com.navigator.app.ui.shell.LocalFeedback

@Composable
fun LogsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val feedback = LocalFeedback.current
    val vm: LogsViewModel = viewModel(factory = LogsViewModel.factory())
    val ui by vm.ui.collectAsState()
    val failed = stringResource(R.string.logs_share_failed)
    LogsContent(
        ui,
        LogsActions(onBack, { if (!shareLog(context)) feedback.snackbar(failed) }, vm::selectTag, vm::setQuery),
        modifier,
    )
}

/** Hands the current log file to the share sheet; false when there is no file or no app took it. */
private fun shareLog(context: Context): Boolean {
    val file = AppLogger.currentLogFile() ?: return false
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "com.navigator.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.logs_share_chooser)))
    }.isSuccess
}
