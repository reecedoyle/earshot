package com.earshot.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.earshot.app.EarshotApp

class EarshotHceService : HostApduService() {

    private val bridge: PairingBridge?
        get() = (application as? EarshotApp)?.container?.pairingBridge

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || !HceHelpers.parseSelectAid(commandApdu)) {
            return HceHelpers.SW_NOT_FOUND
        }
        val payload = bridge?.currentPayload() ?: return HceHelpers.SW_NOT_FOUND
        bridge?.notifyRead()
        return HceHelpers.buildResponse(payload)
    }

    override fun onDeactivated(reason: Int) { /* nothing to do */ }
}
