package com.navigator.app.ble

import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BccuCryptoTest {

    private fun hexToBytes(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    /** Dash->app control message: the command is byte[2] (app->dash puts 0xFF there and the status at [4]). */
    private fun dashMsg(cmd: Int) = BccuCrypto.randomNonce().also { it[2] = cmd.toByte() }
    private fun seq(start: Int) = ByteArray(16) { (start + it).toByte() }

    @Test fun aesMatchesNistCbcVector() {
        val key = hexToBytes("2b7e151628aed2a6abf7158809cf4f3c")
        val iv = hexToBytes("000102030405060708090a0b0c0d0e0f")
        val plain = hexToBytes("6bc1bee22e409f96e93d7e117393172a")
        val cipher = hexToBytes("7649abac8119b246cee98e9b12e9197d")
        assertArrayEquals(cipher, BccuCrypto.aes(plain, key, iv, true))
        assertArrayEquals(plain, BccuCrypto.aes(cipher, key, iv, false))
    }

    @Test fun frameLayoutAndRoundTrip() {
        for (n in listOf(0, 1, 2, 5, 15, 16, 17, 40)) {
            val data = ByteArray(n) { (it + 1).toByte() }
            val framed = BccuCrypto.frame(data)
            val pad = 16 - (n % 16)
            assertEquals(n + 16 + pad, framed.size)
            assertEquals(0, framed.size % 16)
            assertEquals(pad, framed.last().toInt() and 0xFF)
            assertArrayEquals(data, BccuCrypto.unframe(framed))
        }
    }

    @Test fun dataEncryptDecryptRoundTrip() {
        val key = seq(1); val iv = seq(100)
        val payload = BccuProtocol.buildTurnIconPayload(BccuProtocol.TurnIcon.HEAVY_LEFT)
        val enc = BccuCrypto.encryptData(payload, key, iv)
        assertEquals(0, enc.size % 16)
        assertArrayEquals(payload, BccuCrypto.decryptData(enc, key, iv))
    }

    @Test fun controlMessageShapeAndRoundTrip() {
        val msg = BccuCrypto.buildControlMessage(2)
        assertEquals(16, msg.size)
        assertEquals(0xFF, msg[2].toInt() and 0xFF)
        assertEquals(2, msg[4].toInt())
        assertEquals(1, msg[6].toInt())
        val key = seq(7); val iv = seq(50)
        val enc = BccuCrypto.encryptControl(msg, key, iv)
        assertEquals(16, enc.size)
        assertArrayEquals(msg, BccuCrypto.decryptControl(enc, key, iv))
    }

    @Test fun tempIvAndSecretMixing() {
        val m1 = seq(0); val m2 = seq(16)
        val (iv, secret) = BccuCrypto.computeTempIvAndSecret(m1, m2)
        assertArrayEquals(m1.copyOfRange(8, 16) + m2.copyOfRange(0, 8), iv)
        assertArrayEquals(m2.copyOfRange(8, 16) + m1.copyOfRange(0, 8), secret)
    }

    @Test fun mirroredIsPalindromeOfTail() {
        val m = BccuCrypto.buildMirrored(seq(0))
        assertArrayEquals(byteArrayOf(8, 9, 10, 11, 12, 13, 14, 15, 15, 14, 13, 12, 11, 10, 9, 8), m)
    }

    @Test fun sessionKeyDerivationIsSha512Rotations() {
        val c = seq(0); val m = seq(16); val iv = seq(32); val s = seq(48)
        val keys = BccuCrypto.deriveSessionKeys(c, m, iv, s)
        assertEquals(16, keys.size)
        keys.forEach { assertEquals(16, it.size) }
        val sha = MessageDigest.getInstance("SHA-512")
        val rot0 = sha.digest(c + m + iv + s)
        val rot1 = sha.digest(m + iv + s + c)
        val rot3 = sha.digest(s + c + m + iv)
        assertArrayEquals(rot0.copyOfRange(0, 16), keys[0])
        assertArrayEquals(rot0.copyOfRange(48, 64), keys[3])
        assertArrayEquals(rot1.copyOfRange(0, 16), keys[4])
        assertArrayEquals(rot3.copyOfRange(48, 64), keys[15])
        assertEquals(16, keys.map { BccuCrypto.hex(it) }.toSet().size)
    }

    /**
     * Fake dash on one side, app-side logic (same BccuCrypto calls as
     * BccuConnectionService.handleAuthMessage) on the other: m1 -> m2 -> HELLO ->
     * GENERATE_KEYS -> key index -> encrypted guidance the dash can decrypt.
     * Note: both sides use BccuCrypto, so this checks internal consistency of the
     * flow; independent correctness is covered by the NIST AES and SHA-512 tests.
     * It does not exercise handleAuthMessage itself (that needs a service extraction).
     */
    @Test fun fullHandshakeAgainstFakeDash() {
        // dash -> app: m1; app -> dash: m2
        val m1 = BccuCrypto.randomNonce()
        val m2 = BccuCrypto.randomNonce()
        val (appIv, appSecret) = BccuCrypto.computeTempIvAndSecret(m1, m2)
        val (dashIv, dashSecret) = BccuCrypto.computeTempIvAndSecret(m1, m2)
        assertArrayEquals(appIv, dashIv)
        assertArrayEquals(appSecret, dashSecret)

        // dash: HELLO (cmd 0); app decrypts and sees CMD_HELLO
        val hello = BccuCrypto.encryptControl(dashMsg(BccuProtocol.CMD_HELLO), dashSecret, dashIv)
        val helloDec = BccuCrypto.decryptControl(hello, appSecret, appIv)
        assertEquals(BccuProtocol.CMD_HELLO, helloDec[2].toInt() and 0xFF)
        // app replies INITIAL_CONNECTION; dash can read it
        val reply = BccuCrypto.encryptControl(
            BccuCrypto.buildControlMessage(BccuProtocol.STATUS_INITIAL_CONNECTION), appSecret, appIv,
        )
        assertEquals(BccuProtocol.STATUS_INITIAL_CONNECTION, BccuCrypto.decryptControl(reply, dashSecret, dashIv)[4].toInt())

        // dash: GENERATE_KEYS; both sides derive the same pool from the decrypted challenge
        val gen = BccuCrypto.encryptControl(dashMsg(BccuProtocol.CMD_GENERATE_KEYS), dashSecret, dashIv)
        val genDec = BccuCrypto.decryptControl(gen, appSecret, appIv)
        assertEquals(BccuProtocol.CMD_GENERATE_KEYS, genDec[2].toInt() and 0xFF)
        val appKeys = BccuCrypto.deriveSessionKeys(genDec, BccuCrypto.buildMirrored(genDec), appIv, appSecret)
        val dashKeys = BccuCrypto.deriveSessionKeys(genDec, BccuCrypto.buildMirrored(genDec), dashIv, dashSecret)
        appKeys.indices.forEach { assertArrayEquals(dashKeys[it], appKeys[it]) }

        // dash picks key index 9 (cmd 16|9); app acks and switches to that key
        val idxMsg = BccuCrypto.encryptControl(dashMsg(BccuProtocol.CMD_KEY_ACK_BASE or 9), dashSecret, dashIv)
        val cmd = BccuCrypto.decryptControl(idxMsg, appSecret, appIv)[2].toInt() and 0xFF
        assertTrue(cmd in 16..31)
        val activeKey = appKeys[cmd and 0x0F]
        assertArrayEquals(dashKeys[9], activeKey)

        // data plane
        val payload = BccuProtocol.buildTurnDistancePayload("250 m")
        val enc = BccuCrypto.encryptData(payload, activeKey, appIv)
        assertArrayEquals(payload, BccuCrypto.decryptData(enc, dashKeys[9], dashIv))
        assertNotEquals(BccuCrypto.hex(payload), BccuCrypto.hex(enc))
    }

    @Test fun persistedPoolKeyRoundTrips() {
        val pool = BccuCrypto.deriveSessionKeys(seq(0), seq(16), seq(32), seq(48))
        val iv = seq(90)
        val enc = BccuCrypto.encryptData(byteArrayOf(1, 2, 3), pool[5], iv)
        assertArrayEquals(byteArrayOf(1, 2, 3), BccuCrypto.decryptData(enc, pool[5], iv))
    }
}
