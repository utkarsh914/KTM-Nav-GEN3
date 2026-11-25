package com.navigator.app.notify

import android.content.res.Resources
import com.navigator.app.R

/** The states the bike-connection notification can be in (SY5). */
enum class ConnectionNotice { CONNECTING, CONNECTED, RECONNECTING, BLUETOOTH_OFF, CANT_CONNECT, DISCONNECTED }

enum class ConnectionAction { TURN_ON, REPAIR, QUIT }

data class ConnectionContent(
    val title: String,
    /** The second line: the likely fix; none while connected (SY5). */
    val text: String?,
    val actions: List<ConnectionAction>,
    /** A real problem gets one heads-up on the alert channel; the other states stay silent. */
    val alert: Boolean,
)

object ConnectionNoticeContent {
    fun of(notice: ConnectionNotice, res: Resources, bikeName: String? = null): ConnectionContent = when (notice) {
        ConnectionNotice.CONNECTING -> ConnectionContent(
            named(res, R.string.conn_connecting, R.string.conn_connecting_named, bikeName), res.getString(R.string.conn_connecting_fix), listOf(ConnectionAction.QUIT), alert = false,
        )
        ConnectionNotice.CONNECTED -> ConnectionContent(
            named(res, R.string.conn_connected, R.string.conn_connected_named, bikeName), null, listOf(ConnectionAction.QUIT), alert = false,
        )
        ConnectionNotice.RECONNECTING -> ConnectionContent(
            named(res, R.string.conn_reconnecting, R.string.conn_reconnecting_named, bikeName), res.getString(R.string.conn_reconnecting_fix), listOf(ConnectionAction.QUIT), alert = false,
        )
        ConnectionNotice.DISCONNECTED -> ConnectionContent(
            named(res, R.string.conn_disconnected, R.string.conn_disconnected_named, bikeName), res.getString(R.string.conn_disconnected_fix), listOf(ConnectionAction.QUIT), alert = false,
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

    /** The title with the bike's name when it is known (SY5): which bike, not just "the dash". */
    private fun named(res: Resources, plain: Int, withName: Int, name: String?): String =
        if (name.isNullOrBlank()) res.getString(plain) else res.getString(withName, name)
}
