package com.earshot.app.nfc

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingControllerTest {

    private val myPayload = byteArrayOf(1, 2, 3)
    private val theirPayload = Payload("Rowan", ByteArray(32) { 9 })

    private class Fake {
        val roles = mutableListOf<PairingRole?>()
        var complete: Payload? = null
        var timedOut = false
    }

    private fun build(scope: CoroutineScope): Pair<PairingController, Fake> {
        val f = Fake()
        val c = PairingController(
            myPayload = myPayload,
            onRoleChange = { f.roles += it },
            onComplete = { f.complete = it },
            onTimeout = { f.timedOut = true },
            scope = scope,
            clock = { 0L },
            periodMs = 100L..100L
        )
        return c to f
    }

    @Test fun `manual send then read succeeded then swap and hce was read completes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startManualSend()
        assertThat(f.roles.last()).isEqualTo(PairingRole.HCE)

        c.onEvent(PairingEvent.HceWasRead)  // one direction done
        // wasReadByPeer flipped, but role and phase have NOT changed — user must swap
        assertThat(c.phase.value).isEqualTo(PairingPhase.MANUAL_SENDING)
        assertThat(f.roles.last()).isEqualTo(PairingRole.HCE)

        c.startManualReceive()  // user taps Receive
        assertThat(c.phase.value).isEqualTo(PairingPhase.MANUAL_RECEIVING)
        assertThat(f.roles.last()).isEqualTo(PairingRole.READER)

        c.onEvent(PairingEvent.ReaderReadSucceeded(theirPayload))
        assertThat(c.phase.value).isEqualTo(PairingPhase.COMPLETE)
        assertThat(f.complete).isEqualTo(theirPayload)
    }

    @Test fun `auto mode alternates roles on the timer`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        assertThat(c.phase.value).isEqualTo(PairingPhase.AUTO_PHASE_1)
        val firstRole = f.roles.last()
        advanceTimeBy(150L)
        val secondRole = f.roles.last()
        assertThat(firstRole).isNotNull()
        assertThat(secondRole).isNotNull()
        assertThat(secondRole).isNotEqualTo(firstRole)
    }

    @Test fun `auto phase 1 exits to phase 2 with HCE role when reader read succeeds first`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        c.onEvent(PairingEvent.ReaderReadSucceeded(theirPayload))
        assertThat(c.phase.value).isEqualTo(PairingPhase.AUTO_PHASE_2)
        assertThat(f.roles.last()).isEqualTo(PairingRole.HCE)

        c.onEvent(PairingEvent.HceWasRead)
        assertThat(c.phase.value).isEqualTo(PairingPhase.COMPLETE)
        assertThat(f.complete).isEqualTo(theirPayload)
    }

    @Test fun `auto phase 1 exits to phase 2 with READER role when hce was read first`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        c.onEvent(PairingEvent.HceWasRead)
        assertThat(c.phase.value).isEqualTo(PairingPhase.AUTO_PHASE_2)
        assertThat(f.roles.last()).isEqualTo(PairingRole.READER)

        c.onEvent(PairingEvent.ReaderReadSucceeded(theirPayload))
        assertThat(c.phase.value).isEqualTo(PairingPhase.COMPLETE)
    }

    @Test fun `timeout fires after 30 seconds without completion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        advanceTimeBy(30_000L + 100L)
        assertThat(c.phase.value).isEqualTo(PairingPhase.TIMED_OUT)
        assertThat(f.timedOut).isTrue()
    }

    @Test fun `cancel disables role and moves to IDLE`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        c.cancel()
        assertThat(c.phase.value).isEqualTo(PairingPhase.IDLE)
        assertThat(f.roles.last()).isNull()
    }
}
