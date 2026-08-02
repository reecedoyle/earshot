package com.earshot.app.nfc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class PairingRole { READER, HCE }
enum class PairingPhase {
    IDLE, MANUAL_SENDING, MANUAL_RECEIVING,
    AUTO_PHASE_1, AUTO_PHASE_2, COMPLETE, TIMED_OUT
}

sealed interface PairingEvent {
    data class ReaderReadSucceeded(val payload: Payload) : PairingEvent
    data object HceWasRead : PairingEvent
    data object Timeout : PairingEvent
    data object Cancelled : PairingEvent
}

class PairingController(
    private val myPayload: ByteArray,
    private val onRoleChange: (PairingRole?) -> Unit,
    private val onComplete: (Payload) -> Unit,
    private val onTimeout: () -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val periodMs: LongRange = 250L..350L
) {
    private val _phase = MutableStateFlow(PairingPhase.IDLE)
    val phase: StateFlow<PairingPhase> = _phase

    private var currentRole: PairingRole? = null
    private var alternationJob: Job? = null
    private var timeoutJob: Job? = null

    private var wasReadByPeer: Boolean = false
    private var readFromPeer: Payload? = null

    fun startAuto() {
        reset()
        _phase.value = PairingPhase.AUTO_PHASE_1
        setRole(if (Random.nextBoolean()) PairingRole.READER else PairingRole.HCE)
        alternationJob = scope.launch {
            while (_phase.value == PairingPhase.AUTO_PHASE_1) {
                delay(Random.nextLong(periodMs.first, periodMs.last + 1))
                if (_phase.value != PairingPhase.AUTO_PHASE_1) break
                flipRole()
            }
        }
        armTimeout()
    }

    fun startManualSend() {
        _phase.value = PairingPhase.MANUAL_SENDING
        setRole(PairingRole.HCE)
    }

    fun startManualReceive() {
        _phase.value = PairingPhase.MANUAL_RECEIVING
        setRole(PairingRole.READER)
    }

    fun onEvent(event: PairingEvent) {
        when (event) {
            is PairingEvent.ReaderReadSucceeded -> {
                readFromPeer = event.payload
                progress()
            }
            PairingEvent.HceWasRead -> {
                wasReadByPeer = true
                progress()
            }
            PairingEvent.Timeout -> {
                _phase.value = PairingPhase.TIMED_OUT
                setRole(null)
                onTimeout()
            }
            PairingEvent.Cancelled -> cancel()
        }
    }

    fun cancel() {
        alternationJob?.cancel()
        timeoutJob?.cancel()
        alternationJob = null
        timeoutJob = null
        setRole(null)
        _phase.value = PairingPhase.IDLE
        wasReadByPeer = false
        readFromPeer = null
    }

    private fun progress() {
        val phaseNow = _phase.value
        val bothDone = readFromPeer != null && wasReadByPeer

        if (bothDone) {
            alternationJob?.cancel()
            timeoutJob?.cancel()
            setRole(null)
            _phase.value = PairingPhase.COMPLETE
            onComplete(readFromPeer!!)
            return
        }

        when (phaseNow) {
            PairingPhase.AUTO_PHASE_1 -> {
                alternationJob?.cancel()
                alternationJob = null
                _phase.value = PairingPhase.AUTO_PHASE_2
                // Deterministic swap:
                //  - if we JUST read, we now serve (HCE)
                //  - if we JUST got read, we now read (READER)
                setRole(
                    if (readFromPeer != null) PairingRole.HCE else PairingRole.READER
                )
            }
            PairingPhase.MANUAL_SENDING -> Unit    // wait for user to tap Receive
            PairingPhase.MANUAL_RECEIVING -> Unit  // wait for user to tap Send
            else -> Unit
        }
    }

    private fun flipRole() {
        setRole(if (currentRole == PairingRole.READER) PairingRole.HCE else PairingRole.READER)
    }

    private fun setRole(role: PairingRole?) {
        if (role != currentRole) {
            currentRole = role
            onRoleChange(role)
        }
    }

    private fun armTimeout() {
        timeoutJob = scope.launch {
            delay(30_000L)
            if (_phase.value == PairingPhase.AUTO_PHASE_1 || _phase.value == PairingPhase.AUTO_PHASE_2) {
                onEvent(PairingEvent.Timeout)
            }
        }
    }

    private fun reset() {
        alternationJob?.cancel()
        timeoutJob?.cancel()
        alternationJob = null
        timeoutJob = null
        wasReadByPeer = false
        readFromPeer = null
    }
}
