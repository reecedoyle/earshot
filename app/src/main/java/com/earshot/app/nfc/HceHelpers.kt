package com.earshot.app.nfc

object HceHelpers {
    const val AID_HEX = "F045415253484F5401"
    val AID_BYTES: ByteArray = AID_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    val SW_OK: ByteArray = byteArrayOf(0x90.toByte(), 0x00)
    val SW_NOT_FOUND: ByteArray = byteArrayOf(0x6A, 0x82.toByte())

    /**
     * Returns true when [apdu] is a SELECT (by AID) command for our AID.
     *
     * The APDU layout expected (ISO 7816-4 standard):
     *   [0] CLA         (0x00 or 0x80)
     *   [1] INS         (0xA4)
     *   [2] P1          (0x04)
     *   [3] P2          (0x00)
     *   [4] Lc          (single byte, length of AID)
     *   [5..5+Lc-1] AID bytes
     *   [5+Lc] Le       (Le, optional)
     */
    fun parseSelectAid(apdu: ByteArray): Boolean {
        if (apdu.size < 5 + AID_BYTES.size) return false
        val cla = apdu[0].toInt() and 0xFF
        val ins = apdu[1].toInt() and 0xFF
        val p1  = apdu[2].toInt() and 0xFF
        val p2  = apdu[3].toInt() and 0xFF
        if (ins != 0xA4 || p1 != 0x04 || p2 != 0x00) return false
        if (cla != 0x00 && cla != 0x80) return false
        val lc = apdu[4].toInt() and 0xFF
        if (lc != AID_BYTES.size) return false
        if (apdu.size < 5 + lc) return false
        for (i in 0 until lc) {
            if (apdu[5 + i] != AID_BYTES[i]) return false
        }
        return true
    }

    fun buildResponse(payload: ByteArray): ByteArray = payload + SW_OK
}
