package com.earshot.app.nfc

import java.util.concurrent.atomic.AtomicReference

class PairingBridge {
    private val payloadRef = AtomicReference<ByteArray?>(null)
    private val callbackRef = AtomicReference<(() -> Unit)?>(null)

    fun setPayload(payload: ByteArray?) { payloadRef.set(payload) }
    fun currentPayload(): ByteArray? = payloadRef.get()

    fun onRead(callback: (() -> Unit)?) { callbackRef.set(callback) }
    fun notifyRead() { callbackRef.get()?.invoke() }
}
