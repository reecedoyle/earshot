package com.earshot.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.earshot.app.EarshotApp
import com.earshot.app.data.Contact
import com.earshot.app.data.LocalIdentity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: EarshotApp) : ViewModel() {
    val identity: StateFlow<LocalIdentity?> =
        app.container.identityRepo.identity()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val contacts: StateFlow<List<Contact>> =
        app.container.contactsRepo.contacts()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
