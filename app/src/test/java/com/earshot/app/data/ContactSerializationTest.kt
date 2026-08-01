package com.earshot.app.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class ContactSerializationTest {

    private val json = Json { prettyPrint = false }

    @Test fun `Contact round-trips through SerializableContact`() {
        val pk = ByteArray(32) { (it * 7).toByte() }
        val original = Contact(publicKey = pk, displayName = "Rowan", pairedAt = 1_700_000_000_000L)
        val restored = original.toSerializable().toContact()
        assertThat(restored.displayName).isEqualTo(original.displayName)
        assertThat(restored.pairedAt).isEqualTo(original.pairedAt)
        assertThat(restored.publicKey).isEqualTo(original.publicKey)
    }

    @Test fun `list of SerializableContact JSON round-trips`() {
        val list = listOf(
            SerializableContact(pubKeyB64 = "AAA=", displayName = "A", pairedAt = 1),
            SerializableContact(pubKeyB64 = "AQI=", displayName = "B", pairedAt = 2)
        )
        val encoded = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SerializableContact.serializer()), list)
        val decoded = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SerializableContact.serializer()), encoded)
        assertThat(decoded).isEqualTo(list)
    }

    @Test fun `Contact equals and hashCode account for pubkey by content`() {
        val pk = ByteArray(32) { 1 }
        val a = Contact(publicKey = pk.copyOf(), displayName = "x", pairedAt = 0)
        val b = Contact(publicKey = pk.copyOf(), displayName = "x", pairedAt = 0)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }
}
