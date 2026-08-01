package com.earshot.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FingerprintTest {
    @Test fun `fingerprint on a 32-byte key shows first-4-hex then ellipsis then last-4-hex`() {
        val key = ByteArray(32).also {
            it[0] = 0xa4.toByte(); it[1] = 0xc9.toByte()
            it[30] = 0x7f.toByte(); it[31] = 0x22.toByte()
        }
        assertThat(key.fingerprint()).isEqualTo("a4c9…7f22")
    }

    @Test fun `fingerprint is lowercase hex`() {
        val key = ByteArray(32).also {
            it[0] = 0xFF.toByte(); it[31] = 0xAB.toByte()
        }
        val fp = key.fingerprint()
        assertThat(fp).contains("ff")
        assertThat(fp).contains("ab")
        assertThat(fp).doesNotContain("FF")
    }

    @Test fun `fingerprint rejects wrong-size input`() {
        try {
            ByteArray(31).fingerprint()
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
