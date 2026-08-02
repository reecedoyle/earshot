package com.earshot.app.ui.pairing

import android.app.Activity
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.earshot.app.EarshotApp
import com.earshot.app.crypto.SafetyCode
import com.earshot.app.data.Contact
import com.earshot.app.data.ContactsRepository
import com.earshot.app.data.IdentityRepository
import com.earshot.app.data.LocalIdentity
import com.earshot.app.nfc.NfcReader
import com.earshot.app.nfc.PairingBridge
import com.earshot.app.nfc.PairingController
import com.earshot.app.nfc.PairingEvent
import com.earshot.app.nfc.PairingRole
import com.earshot.app.nfc.Payload
import com.earshot.app.nfc.PayloadCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddContactViewModel(
    private val app: EarshotApp,
    private val activityHost: () -> Activity?
) : ViewModel() {

    private val identityRepo: IdentityRepository = app.container.identityRepo
    private val contactsRepo: ContactsRepository = app.container.contactsRepo
    private val bridge: PairingBridge = app.container.pairingBridge

    private val _uiState = MutableStateFlow<AddContactUiState>(
        AddContactUiState.Ready(Mode.AUTO, Progress.IDLE, computeNfcState())
    )
    val uiState: StateFlow<AddContactUiState> = _uiState

    private var mode: Mode = Mode.AUTO
    private var identity: LocalIdentity? = null
    private var incoming: Payload? = null
    private lateinit var controller: PairingController
    private var reader: NfcReader? = null

    init {
        viewModelScope.launch {
            val id = identityRepo.identity()
                .filterNotNull()
                .first()
            identity = id
            configureController()
            bridge.setPayload(PayloadCodec.encode(id.displayName, id.publicKey))
            bridge.onRead { controller.onEvent(PairingEvent.HceWasRead) }
        }
    }

    fun setMode(mode: Mode) {
        this.mode = mode
        cancel()
        _uiState.value = AddContactUiState.Ready(mode, Progress.IDLE, computeNfcState())
    }

    fun startPairing() {
        if (mode == Mode.AUTO) controller.startAuto()
        else _uiState.value = AddContactUiState.Ready(mode, Progress.IDLE, computeNfcState())
    }

    fun manualSend() { controller.startManualSend() }
    fun manualReceive() { controller.startManualReceive() }

    fun onSafetyConfirmed() {
        val inc = incoming ?: return
        viewModelScope.launch {
            val dupPk = contactsRepo.findByPubKey(inc.publicKey)
            if (dupPk != null) {
                _uiState.value = AddContactUiState.DuplicatePubKey(existing = dupPk, incoming = inc)
                return@launch
            }
            val dupName = contactsRepo.findByDisplayName(inc.displayName)
            if (dupName != null) {
                _uiState.value = AddContactUiState.DuplicateName(existing = dupName, incoming = inc)
                return@launch
            }
            persist(inc)
        }
    }

    fun onDuplicateConfirmed() {
        val inc = incoming ?: return
        viewModelScope.launch { persist(inc) }
    }

    fun onDuplicateCancelled() {
        incoming = null
        _uiState.value = AddContactUiState.Ready(mode, Progress.IDLE, computeNfcState())
    }

    fun cancel() {
        if (::controller.isInitialized) controller.cancel()
        reader?.stop()
        reader = null
        incoming = null
    }

    fun retry() {
        cancel()
        _uiState.value = AddContactUiState.Ready(mode, Progress.IDLE, computeNfcState())
    }

    override fun onCleared() {
        super.onCleared()
        cancel()
        bridge.setPayload(null)
        bridge.onRead(null)
    }

    private suspend fun persist(inc: Payload) {
        val me = identity ?: return
        if (inc.publicKey.contentEquals(me.publicKey)) {
            _uiState.value = AddContactUiState.Failed("You can't pair with your own phone.")
            return
        }
        contactsRepo.upsert(
            Contact(
                publicKey = inc.publicKey,
                displayName = inc.displayName,
                pairedAt = System.currentTimeMillis()
            )
        )
        _uiState.value = AddContactUiState.Saved
    }

    private fun configureController() {
        controller = PairingController(
            myPayload = PayloadCodec.encode(identity!!.displayName, identity!!.publicKey),
            onRoleChange = { role -> handleRoleChange(role) },
            onComplete = { theirs ->
                incoming = theirs
                val me = identity!!.publicKey
                val code = SafetyCode.derive(me, theirs.publicKey)
                _uiState.value = AddContactUiState.SafetyCode(code, theirs)
            },
            onTimeout = {
                _uiState.value = AddContactUiState.Failed("Pairing timed out.")
            },
            scope = viewModelScope
        )
    }

    private fun handleRoleChange(role: PairingRole?) {
        val activity = activityHost()
        when (role) {
            PairingRole.READER -> {
                reader?.stop()
                if (activity != null) {
                    reader = NfcReader(activity).also {
                        it.start(
                            onPayload = { p ->
                                controller.onEvent(PairingEvent.ReaderReadSucceeded(p))
                            },
                            onError = { /* auto retries via alternation; manual will surface via timeout */ }
                        )
                    }
                }
            }
            PairingRole.HCE -> {
                reader?.stop()
                reader = null
                // HCE is always live via the service + bridge; nothing else to enable here.
            }
            null -> {
                reader?.stop()
                reader = null
            }
        }
    }

    private fun computeNfcState(): NfcState {
        val ctx = app.applicationContext
        val pm = ctx.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC)) return NfcState.HW_ABSENT
        if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) return NfcState.HCE_ABSENT
        val adapter = NfcAdapter.getDefaultAdapter(ctx)
        return if (adapter?.isEnabled == true) NfcState.OK else NfcState.DISABLED
    }
}
