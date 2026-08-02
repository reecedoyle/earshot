package com.earshot.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.earshot.app.EarshotApp
import com.earshot.app.data.IdentityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NameSetupViewModel(app: EarshotApp) : ViewModel() {
    private val repo: IdentityRepository = app.container.identityRepo

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    fun onNameChanged(new: String) {
        if (new.length <= 80) _name.value = new  // soft cap slightly above 64 UTF-8 hard limit
    }

    val canSubmit: Boolean
        get() = _name.value.trim().isNotEmpty() &&
                !_submitting.value

    fun submit() {
        if (!canSubmit) return
        _submitting.value = true
        viewModelScope.launch {
            try {
                repo.createIdentity(_name.value)
                _done.value = true
            } catch (e: IllegalArgumentException) {
                _submitting.value = false
            }
        }
    }
}
