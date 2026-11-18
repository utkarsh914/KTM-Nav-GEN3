package com.navigator.app.settings

/**
 * Serialisation of the 16 x 16-byte session-key pool. Decoding is strict: a
 * truncated, odd-length or wrong-sized pool is rejected (null) so a corrupt
 * store can never be used for a handshake or crash the encrypt path.
 */
object SessionKeyCodec {
    const val POOL_SIZE = 16
    const val KEY_BYTES = 16

    fun encode(keys: List<ByteArray>): String =
        keys.joinToString(",") { k -> k.joinToString("") { "%02x".format(it) } }

    fun decode(stored: String?): List<ByteArray>? {
        if (stored.isNullOrEmpty()) return null
        val parts = stored.split(",")
        if (parts.size != POOL_SIZE) return null
        val keys = ArrayList<ByteArray>(POOL_SIZE)
        for (p in parts) {
            if (p.length != KEY_BYTES * 2) return null
            val bytes = ByteArray(KEY_BYTES)
            for (i in 0 until KEY_BYTES) {
                val hi = Character.digit(p[i * 2], 16)
                val lo = Character.digit(p[i * 2 + 1], 16)
                if (hi < 0 || lo < 0) return null
                bytes[i] = ((hi shl 4) or lo).toByte()
            }
            keys += bytes
        }
        return keys
    }
}
