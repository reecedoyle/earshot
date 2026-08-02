package com.earshot.app.ui.pairing

import com.earshot.app.data.Contact
import com.earshot.app.nfc.Payload

enum class Mode { AUTO, MANUAL }
enum class Progress { IDLE, IN_PROGRESS_ONE_SIDE, IN_PROGRESS_BOTH }
enum class NfcState { OK, DISABLED, HW_ABSENT, HCE_ABSENT }

sealed interface AddContactUiState {
    data class Ready(
        val mode: Mode,
        val progress: Progress,
        val nfcState: NfcState
    ) : AddContactUiState

    data class SafetyCode(
        val emojis: List<String>,
        val incoming: Payload
    ) : AddContactUiState

    data class DuplicatePubKey(
        val existing: Contact,
        val incoming: Payload
    ) : AddContactUiState

    data class DuplicateName(
        val existing: Contact,
        val incoming: Payload
    ) : AddContactUiState

    data object Saved : AddContactUiState

    data class Failed(val reason: String) : AddContactUiState
}
