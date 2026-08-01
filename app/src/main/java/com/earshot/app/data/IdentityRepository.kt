package com.earshot.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.earshot.app.crypto.SodiumHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64

class IdentityRepository(private val ds: DataStore<Preferences>) {

    private val nameKey = stringPreferencesKey("my_display_name")
    private val pubKey  = stringPreferencesKey("my_public_key")
    private val privKey = stringPreferencesKey("my_private_key")

    fun identity(): Flow<LocalIdentity?> = ds.data.map { prefs ->
        val name = prefs[nameKey] ?: return@map null
        val pub = prefs[pubKey]?.let { Base64.getDecoder().decode(it) } ?: return@map null
        val priv = prefs[privKey]?.let { Base64.getDecoder().decode(it) } ?: return@map null
        LocalIdentity(name, pub, priv)
    }

    suspend fun hasIdentity(): Boolean = identity().first() != null

    suspend fun createIdentity(displayName: String): LocalIdentity {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "display name must not be blank" }
        require(trimmed.toByteArray(Charsets.UTF_8).size <= 64) {
            "display name must be <= 64 UTF-8 bytes"
        }
        val kp = SodiumHolder.lazySodium.cryptoBoxKeypair()
        val identity = LocalIdentity(
            displayName = trimmed,
            publicKey = kp.publicKey.asBytes,
            privateKey = kp.secretKey.asBytes
        )
        ds.edit { prefs ->
            prefs[nameKey] = identity.displayName
            prefs[pubKey]  = Base64.getEncoder().withoutPadding().encodeToString(identity.publicKey)
            prefs[privKey] = Base64.getEncoder().withoutPadding().encodeToString(identity.privateKey)
        }
        return identity
    }
}
