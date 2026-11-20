package com.navigator.app.ui.feature.settings

import android.content.Context
import android.net.Uri
import com.navigator.app.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Reads and writes backup files chosen through Android's file picker (no storage permission needed). */
interface BackupGateway {
    suspend fun backup(uri: Uri): BackupManager.Summary
    suspend fun inspect(uri: Uri): BackupManager.Preview?
    suspend fun restore(uri: Uri, rides: Boolean, places: Boolean, settings: Boolean): BackupManager.Restored
}

/** Slow file work: off the main thread, and it finishes even if the page is left meanwhile. */
class AndroidBackupGateway(context: Context) : BackupGateway {
    private val app = context.applicationContext
    private val io = Dispatchers.IO + NonCancellable

    override suspend fun backup(uri: Uri): BackupManager.Summary = withContext(io) {
        val summary = runCatching { app.contentResolver.openOutputStream(uri)?.use { BackupManager(app).backup(it) } }.getOrNull()
            ?: BackupManager.Summary(0, false)
        // The picker already created the file: a failed backup must not leave an empty one behind.
        if (!summary.ok) runCatching { android.provider.DocumentsContract.deleteDocument(app.contentResolver, uri) }
        summary
    }

    override suspend fun inspect(uri: Uri): BackupManager.Preview? = withContext(io) {
        runCatching { app.contentResolver.openInputStream(uri)?.use { BackupManager(app).inspect(it) } }.getOrNull()
    }

    override suspend fun restore(uri: Uri, rides: Boolean, places: Boolean, settings: Boolean): BackupManager.Restored = withContext(io) {
        runCatching { app.contentResolver.openInputStream(uri)?.use { BackupManager(app).restore(it, rides, places, settings) } }.getOrNull()
            ?: BackupManager.Restored(0, 0, 0, false)
    }
}
