package com.earshot.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ContactsRepository(private val ds: DataStore<Preferences>) {

    private val key = stringPreferencesKey("contacts_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(SerializableContact.serializer())

    fun contacts(): Flow<List<Contact>> = ds.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        json.decodeFromString(listSerializer, raw)
            .map { it.toContact() }
            .sortedByDescending { it.pairedAt }
    }

    suspend fun findByPubKey(pk: ByteArray): Contact? =
        contacts().first().firstOrNull { it.publicKey.contentEquals(pk) }

    suspend fun findByDisplayName(name: String): Contact? =
        contacts().first().firstOrNull { it.displayName == name }

    suspend fun upsert(contact: Contact) {
        ds.edit { prefs ->
            val existing = readList(prefs).toMutableList()
            val idx = existing.indexOfFirst { it.pubKeyB64 == contact.toSerializable().pubKeyB64 }
            val ser = contact.toSerializable()
            if (idx >= 0) existing[idx] = ser else existing.add(ser)
            prefs[key] = json.encodeToString(listSerializer, existing)
        }
    }

    suspend fun clear() {
        ds.edit { it.remove(key) }
    }

    private fun readList(prefs: Preferences): List<SerializableContact> {
        val raw = prefs[key] ?: return emptyList()
        return json.decodeFromString(listSerializer, raw)
    }
}
