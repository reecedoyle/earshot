package com.earshot.app.nfc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PayloadCodecTest {

    private val pk32 = ByteArray(32) { it.toByte() }

    @Test fun `encode then decode round-trips a typical payload`() {
        val encoded = PayloadCodec.encode("Reece", pk32)
        val decoded = PayloadCodec.decode(encoded).getOrThrow()
        assertThat(decoded.displayName).isEqualTo("Reece")
        assertThat(decoded.publicKey).isEqualTo(pk32)
    }

    @Test fun `encode produces version byte first`() {
        val encoded = PayloadCodec.encode("R", pk32)
        assertThat(encoded[0]).isEqualTo(PayloadCodec.PROTOCOL_VERSION)
    }

    @Test fun `encode places the pubkey in bytes 1 through 32 inclusive`() {
        val encoded = PayloadCodec.encode("R", pk32)
        assertThat(encoded.copyOfRange(1, 33)).isEqualTo(pk32)
    }

    @Test fun `empty name round-trips`() {
        val encoded = PayloadCodec.encode("", pk32)
        val decoded = PayloadCodec.decode(encoded).getOrThrow()
        assertThat(decoded.displayName).isEmpty()
    }

    @Test fun `name at 64 UTF-8 bytes encodes cleanly`() {
        val name = "a".repeat(64)
        val encoded = PayloadCodec.encode(name, pk32)
        val decoded = PayloadCodec.decode(encoded).getOrThrow()
        assertThat(decoded.displayName).isEqualTo(name)
    }

    @Test fun `name over 64 UTF-8 bytes throws IllegalArgumentException on encode`() {
        val tooLong = "a".repeat(65)
        try {
            PayloadCodec.encode(tooLong, pk32)
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test fun `pubkey not 32 bytes throws IllegalArgumentException on encode`() {
        try {
            PayloadCodec.encode("R", ByteArray(31))
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test fun `decode returns failure for unknown version byte`() {
        val encoded = PayloadCodec.encode("R", pk32).also { it[0] = 0x02 }
        assertThat(PayloadCodec.decode(encoded).isFailure).isTrue()
    }

    @Test fun `decode returns failure for truncated buffer`() {
        val encoded = PayloadCodec.encode("R", pk32)
        assertThat(PayloadCodec.decode(encoded.copyOfRange(0, 20)).isFailure).isTrue()
    }

    @Test fun `decode returns failure when name_len says more than remaining bytes`() {
        val encoded = PayloadCodec.encode("R", pk32)
        encoded[33] = 100  // claim name is 100 bytes but only 1 remains
        assertThat(PayloadCodec.decode(encoded).isFailure).isTrue()
    }

    @Test fun `decode returns failure for empty buffer`() {
        assertThat(PayloadCodec.decode(ByteArray(0)).isFailure).isTrue()
    }
}
