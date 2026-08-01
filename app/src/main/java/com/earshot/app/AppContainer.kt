package com.earshot.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.earshot.app.data.ContactsRepository
import com.earshot.app.data.IdentityRepository
import com.earshot.app.nfc.PairingBridge

class AppContainer(context: Context) {
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("earshot_prefs") }
    )
    val identityRepo = IdentityRepository(dataStore)
    val contactsRepo = ContactsRepository(dataStore)
    val pairingBridge = PairingBridge()
}
