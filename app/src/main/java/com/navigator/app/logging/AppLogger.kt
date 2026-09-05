package com.navigator.app.logging

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-wide logger: logcat + an in-memory buffer (for the in-app Logs screen)
 * + a timestamped file. The file is written to two places: the app-private
 * external files dir (always works, no permission needed, used by the
 * in-app Share button) and a public Downloads/OpenDash copy (so the log can
 * be found with a normal file manager even if the app has crashed and can't
 * be reopened to hit Share). Needs init(context) called once, from
 * OpenDashApplication - also installs a global uncaught-exception handler so
 * a crash's actual stack trace ends up in the log file instead of being lost.
 */
object AppLogger {

    private const val TAG = "OpenDash"
    private const val MAX_LINES = 500

    private lateinit var appContext: Context
    private lateinit var logFile: File
    private var publicLogFile: File? = null
    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Disk + MediaStore writes happen here, off the caller's thread. Logging is
    // called from the main thread and from BLE/RCM callbacks (hot paths); doing
    // file I/O and a MediaStore openOutputStream() per line on those threads was
    // a real source of UI jank and callback latency.
    private val ioHandler: Handler by lazy {
        val thread = android.os.HandlerThread("OpenDashLogIO").apply { start() }
        Handler(thread.looper)
    }

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        logFile = File(dir, "opendash_log_$stamp.txt")
        publicLogFile = preparePublicLogFile(stamp)
        initialized = true
        installCrashHandler()
        log("App", "OpenDash logging started: ${logFile.absolutePath}")
        publicLogFile?.let { log("App", "Also mirrored to: ${it.absolutePath}") }
    }

    /**
     * Pre-API29 only: a plain file under the public Downloads directory,
     * visible to any file manager without needing our app to be running.
     * On API29+ scoped storage blocks direct File access to public
     * directories, so writeToPublicCopy() uses MediaStore instead.
     */
    private fun preparePublicLogFile(stamp: String): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null
        return try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val folder = File(downloads, "OpenDash")
            folder.mkdirs()
            File(folder, "opendash_log_$stamp.txt")
        } catch (e: Exception) {
            null
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                log("CRASH", "Uncaught exception on thread ${thread.name}: ${Log.getStackTraceString(throwable)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log crash", e)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun log(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        if (!initialized) return
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$timestamp] [$tag] $message"
        // Offload all disk/MediaStore I/O to the dedicated log thread.
        ioHandler.post {
            try {
                logFile.appendText(line + "\n")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write private log file", e)
            }
            writeToPublicCopy(line)
        }
        mainHandler.post {
            _lines.value = (_lines.value + line).takeLast(MAX_LINES)
        }
    }

    private fun writeToPublicCopy(line: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeToMediaStoreDownloads(line)
            } else {
                publicLogFile?.appendText(line + "\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write public log copy", e)
        }
    }

    private var mediaStoreUri: android.net.Uri? = null
    private var mediaStoreUriResolved = false

    private fun writeToMediaStoreDownloads(line: String) {
        val resolver = appContext.contentResolver
        if (!mediaStoreUriResolved) {
            mediaStoreUriResolved = true
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, logFile.name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenDash")
            }
            mediaStoreUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }
        val uri = mediaStoreUri ?: return
        resolver.openOutputStream(uri, "wa")?.use { it.write((line + "\n").toByteArray()) }
    }

    fun currentLogFile(): File? = if (initialized) logFile else null
}
