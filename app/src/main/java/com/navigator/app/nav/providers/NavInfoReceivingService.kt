package com.navigator.app.nav.providers

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager
import com.navigator.app.logging.AppLogger

/**
 * The Navigation SDK binds to this service and streams ~1 Hz turn-by-turn
 * updates over an Android [Messenger]. We convert each message to a `NavInfo`
 * with [TurnByTurnManager] and forward it to [GoogleNavSdkProvider], which
 * normalises it for the KTM pipeline.
 *
 * Registered with the SDK via `Navigator.registerServiceForNavUpdates(...)`.
 */
class NavInfoReceivingService : Service() {

    private lateinit var incomingMessenger: Messenger
    private lateinit var turnByTurnManager: TurnByTurnManager

    private inner class IncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            if (msg.what == TurnByTurnManager.MSG_NAV_INFO) {
                val navInfo = runCatching { turnByTurnManager.readNavInfoFromBundle(msg.data) }
                    .getOrNull()
                if (navInfo != null) {
                    GoogleNavSdkProvider.onNavInfo(navInfo)
                }
            } else {
                super.handleMessage(msg)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        turnByTurnManager = TurnByTurnManager.createInstance()
        val thread = HandlerThread("NavInfoReceiving", Process.THREAD_PRIORITY_DEFAULT).apply { start() }
        incomingMessenger = Messenger(IncomingHandler(thread.looper))
        AppLogger.log("Nav", "NavInfoReceivingService created")
    }

    override fun onBind(intent: Intent?): IBinder = incomingMessenger.binder
}
