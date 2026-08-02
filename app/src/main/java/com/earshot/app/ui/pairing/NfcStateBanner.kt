package com.earshot.app.ui.pairing

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun NfcStateBanner(state: NfcState) {
    val context = LocalContext.current
    val (msg, cta) = when (state) {
        NfcState.OK -> return
        NfcState.DISABLED -> "NFC is off. Enable it in Settings." to "Open NFC settings"
        NfcState.HW_ABSENT -> "This device doesn't support NFC." to null
        NfcState.HCE_ABSENT -> "This device supports NFC but not HCE." to null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFE0B2))
            .padding(12.dp)
    ) {
        Text(msg, color = Color(0xFF5D2E00))
        if (cta != null) {
            Spacer(Modifier.height(4.dp))
            Row {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }) { Text(cta) }
            }
        }
    }
}
