package com.earshot.app.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafetyCodeTest {

    private val a = ByteArray(32) { 0x11 }
    private val b = ByteArray(32) { 0x22 }
    private val c = ByteArray(32) { 0x33 }

    @Test fun `alphabet has exactly 64 distinct entries`() {
        assertThat(SafetyCode.ALPHABET).hasSize(64)
        assertThat(SafetyCode.ALPHABET.toSet()).hasSize(64)
    }

    @Test fun `derive returns 6 emoji from the alphabet`() {
        val code = SafetyCode.derive(a, b)
        assertThat(code).hasSize(6)
        assertThat(SafetyCode.ALPHABET).containsAtLeastElementsIn(code)
    }

    @Test fun `derive is symmetric across both pubkey orderings`() {
        assertThat(SafetyCode.derive(a, b)).isEqualTo(SafetyCode.derive(b, a))
    }

    @Test fun `derive is deterministic on repeat`() {
        assertThat(SafetyCode.derive(a, b)).isEqualTo(SafetyCode.derive(a, b))
    }

    @Test fun `distinct pubkey pairs produce distinct codes`() {
        val ab = SafetyCode.derive(a, b)
        val ac = SafetyCode.derive(a, c)
        assertThat(ab).isNotEqualTo(ac)
    }

    @Test fun `rejects non-32-byte pubkey`() {
        try {
            SafetyCode.derive(ByteArray(16), b)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
