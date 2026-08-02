package com.earshot.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle

class NfcReader(private val activity: Activity) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var callback: NfcAdapter.ReaderCallback? = null

    fun start(onPayload: (Payload) -> Unit, onError: (Throwable) -> Unit) {
        val nfc = adapter ?: run {
            onError(IllegalStateException("no NFC adapter"))
            return
        }
        val cb = NfcAdapter.ReaderCallback { tag: Tag ->
            try {
                val payload = readOnce(tag)
                onPayload(payload)
            } catch (t: Throwable) {
                onError(t)
            }
        }
        callback = cb
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        nfc.enableReaderMode(activity, cb, flags, extras)
    }

    fun stop() {
        val nfc = adapter ?: return
        nfc.disableReaderMode(activity)
        callback = null
    }

    private fun readOnce(tag: Tag): Payload {
        val iso = IsoDep.get(tag) ?: error("tag is not IsoDep-capable")
        iso.connect()
        try {
            // SELECT AID
            val select = ByteArray(6 + HceHelpers.AID_BYTES.size)
            select[0] = 0x00
            select[1] = 0xA4.toByte()
            select[2] = 0x04
            select[3] = 0x00
            select[4] = HceHelpers.AID_BYTES.size.toByte()
            System.arraycopy(HceHelpers.AID_BYTES, 0, select, 5, HceHelpers.AID_BYTES.size)
            select[5 + HceHelpers.AID_BYTES.size] = 0x00  // Le
            val response = iso.transceive(select)
            require(response.size >= 2) { "response too short: ${response.size}" }
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF
            require(sw1 == 0x90 && sw2 == 0x00) {
                "peer returned SW %02X%02X".format(sw1, sw2)
            }
            val body = response.copyOfRange(0, response.size - 2)
            return PayloadCodec.decode(body).getOrThrow()
        } finally {
            runCatching { iso.close() }
        }
    }
}
