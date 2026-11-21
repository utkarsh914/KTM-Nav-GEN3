package com.navigator.app.logging

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.navigator.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide logger: logcat + an in-memory buffer (for the in-app Logs screen)
 * + a timestamped file. The file is written to two places: the app-private
 * external files dir (always works, no permission needed, used by the
 * in-app Share button) and an opt-in (debug default) public Downloads/OpenDash copy (so the log can
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
    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Disk + MediaStore writes happen here, off the caller's thread. Logging is
    // called from the main thread and from BLE/RCM callbacks (hot paths); doing
    // file I/O and a MediaStore openOutputStream() per line on those threads was
    // a real source of UI jank and callback latency.
    private val ioHandler: Handler by lazy {
        val thread = HandlerThread("OpenDashLogIO").apply { start() }
        Handler(thread.looper)
    }

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    private const val MAX_FILE_BYTES = 4L * 1024 * 1024
    private const val KEEP_LOG_FILES = 5
    private const val PREFS = "opendash_logging"
    private const val KEY_PUBLIC_MIRROR = "public_mirror"
    private var privateBytes = 0L
    private var publicBytes = 0L

    /**
     * Whether log lines are also copied to the public Download/OpenDash folder
     * (readable by any app with storage access). Off by default in release builds.
     */
    var publicMirrorEnabled: Boolean
        get() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PUBLIC_MIRROR, BuildConfig.DEBUG)
        set(value) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PUBLIC_MIRROR, value).apply()
        }

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        pruneOldLogs(dir)
        logFile = File(dir, "opendash_log_$stamp.txt")
        initialized = true
        installCrashHandler()
        log("App", "OpenDash logging started: ${logFile.absolutePath}")
    }

    /** Keep only the newest [KEEP_LOG_FILES] - 1 old logs (a new one is about to be created). */
    private fun pruneOldLogs(dir: File) {
        runCatching {
            dir.listFiles { f -> f.name.startsWith("opendash_log_") && f.name.endsWith(".txt") }
                ?.sortedByDescending { it.name }
                ?.drop(KEEP_LOG_FILES - 1)
                ?.forEach { it.delete() }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Written synchronously: the async log thread would be killed with the
            // process before it flushed, so crashes were almost never captured.
            try {
                val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                val line = "[$timestamp] [CRASH] Uncaught exception on thread ${thread.name}: ${Log.getStackTraceString(throwable)}"
                Log.e(TAG, line)
                logFile.appendText(line + "\n")
                if (publicMirrorEnabled) writeToPublicCopy(line, force = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log crash", e)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Wrap user content (destinations, road names, message text, names) before
     * logging: full text in debug builds, length only in release, because every
     * log line is mirrored to the public Downloads copy.
     */
    fun sensitive(s: String?): String = when {
        s == null -> "null"
        BuildConfig.DEBUG -> "\"$s\""
        s.isEmpty() -> "<empty>"
        else -> "<${s.length} chars>"
    }

    fun log(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        if (!initialized) return
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$timestamp] [$tag] $message"
        // Offload all disk/MediaStore I/O to the dedicated log thread.
        ioHandler.post {
            if (privateBytes < MAX_FILE_BYTES) {
                try {
                    logFile.appendText(line + "\n")
                    privateBytes += line.length + 1
                    if (privateBytes >= MAX_FILE_BYTES) logFile.appendText("[log size cap reached - further lines in this file dropped]\n")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write private log file", e)
                }
            }
            if (publicMirrorEnabled) writeToPublicCopy(line)
        }
        mainHandler.post {
            _lines.value = (_lines.value + line).takeLast(MAX_LINES)
        }
    }

    private fun writeToPublicCopy(line: String, force: Boolean = false) {
        if (!force && publicBytes >= MAX_FILE_BYTES) return
        try {
            publicBytes += line.length + 1
            writeToMediaStoreDownloads(line)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write public log copy", e)
        }
    }

    @Volatile private var mediaStoreUri: Uri? = null
    @Volatile private var mediaStoreUriResolved = false

    @Synchronized
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
