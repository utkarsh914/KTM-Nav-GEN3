package com.navigator.app.notify

import android.content.res.Resources
import com.navigator.app.R

/** The states the bike-connection notification can be in (SY5). */
enum class ConnectionNotice { CONNECTING, CONNECTED, RECONNECTING, BLUETOOTH_OFF, CANT_CONNECT }

enum class ConnectionAction { TURN_ON, REPAIR, QUIT }

data class ConnectionContent(
    val title: String,
    /** The second line: the likely fix. */
    val text: String,
    val actions: List<ConnectionAction>,
    /** A real problem gets one heads-up on the alert channel; the other states stay silent. */
    val alert: Boolean,
)

object ConnectionNoticeContent {
    /**
     * Whether the ongoing notification is re-posted for the one-liner: when the line changed, including back to
     * none (a trip or recording ended), so the old line does not stay. Never off a connected link.
     */
    fun repostForLine(connected: Boolean, line: String?, last: String?): Boolean = connected && line != last

    /** [mirrorLine] is the "Mirroring to dash · 350 m · Turn right · ● Rec" one-liner; only a connected link shows it. */
    fun of(notice: ConnectionNotice, mirrorLine: String?, res: Resources): ConnectionContent = when (notice) {
        ConnectionNotice.CONNECTING -> ConnectionContent(
            res.getString(R.string.conn_connecting), res.getString(R.string.conn_connecting_fix), listOf(ConnectionAction.QUIT), alert = false,
        )
        ConnectionNotice.CONNECTED -> ConnectionContent(
            res.getString(R.string.conn_connected), mirrorLine ?: res.getString(R.string.conn_connected_hint), listOf(ConnectionAction.QUIT), alert = false,
        )
        ConnectionNotice.RECONNECTING -> ConnectionContent(
            res.getString(R.string.conn_reconnecting), res.getString(R.string.conn_reconnecting_fix), listOf(ConnectionAction.QUIT), alert = false,
        )
        ConnectionNotice.BLUETOOTH_OFF -> ConnectionContent(
            res.getString(R.string.conn_bt_off), res.getString(R.string.conn_bt_off_fix),
            listOf(ConnectionAction.TURN_ON, ConnectionAction.QUIT), alert = false,
        )
        ConnectionNotice.CANT_CONNECT -> ConnectionContent(
            res.getString(R.string.conn_cant), res.getString(R.string.conn_cant_fix),
            listOf(ConnectionAction.REPAIR, ConnectionAction.QUIT), alert = true,
        )
    }

    /** "Mirroring to dash · 350 m · Turn right · ● Rec" from whatever parts are known. */
    fun mirrorLine(toTurn: String?, turn: String?, recording: String?, res: Resources): String =
        listOfNotNull(res.getString(R.string.conn_mirroring), toTurn, turn, recording?.let { res.getString(R.string.live_rec, it) })
            .joinToString(" \u00b7 ")
}
