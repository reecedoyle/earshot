package com.earshot.app.nfc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HceHelpersTest {

    @Test fun `AID_BYTES matches the hex spec`() {
        assertThat(HceHelpers.AID_BYTES.toHex()).isEqualTo("F045415253484F5401".lowercase())
    }

    @Test fun `parseSelectAid returns true for valid SELECT AID matching ours`() {
        val select = "00A4040009".hexToBytes() + HceHelpers.AID_BYTES + "00".hexToBytes()
        assertThat(HceHelpers.parseSelectAid(select)).isTrue()
    }

    @Test fun `parseSelectAid returns false for SELECT AID of a different AID`() {
        val otherAid = ByteArray(9) { 0xAA.toByte() }
        val select = "00A4040009".hexToBytes() + otherAid + "00".hexToBytes()
        assertThat(HceHelpers.parseSelectAid(select)).isFalse()
    }

    @Test fun `parseSelectAid returns false for non-SELECT command`() {
        val other = "00B000000000".hexToBytes()  // READ BINARY
        assertThat(HceHelpers.parseSelectAid(other)).isFalse()
    }

    @Test fun `parseSelectAid returns false for truncated APDU`() {
        assertThat(HceHelpers.parseSelectAid("00A4".hexToBytes())).isFalse()
    }

    @Test fun `buildResponse appends SW 9000`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val response = HceHelpers.buildResponse(payload)
        assertThat(response.copyOfRange(0, 3)).isEqualTo(payload)
        assertThat(response.copyOfRange(3, 5)).isEqualTo(HceHelpers.SW_OK)
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it.toInt() and 0xFF) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) {
        val hi = Character.digit(this[it * 2], 16)
        val lo = Character.digit(this[it * 2 + 1], 16)
        ((hi shl 4) or lo).toByte()
    }
}
