package com.earshot.app.ui.pairing

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earshot.app.EarshotApp

@Composable
fun AddContactScreen(
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    activityHost: () -> Activity?
) {
    val context = LocalContext.current
    val app = context.applicationContext as EarshotApp
    val vm: AddContactViewModel = viewModel(factory = addContactVmFactory(app, activityHost))
    val state by vm.uiState.collectAsState()

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                vm.refreshNfcState()
            }
        }
        val filter = android.content.IntentFilter(android.nfc.NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(state) {
        if (state is AddContactUiState.Saved) onSaved()
    }

    when (val s = state) {
        is AddContactUiState.Ready -> ReadyBody(s, vm)
        is AddContactUiState.SafetyCode -> SafetyCodeBody(s, vm, onCancel = onCancelled)
        is AddContactUiState.DuplicatePubKey -> DuplicatePubKeyDialog(s, vm)
        is AddContactUiState.DuplicateName   -> DuplicateNameDialog(s, vm)
        AddContactUiState.Saved -> { /* handled by LaunchedEffect */ }
        is AddContactUiState.Failed -> FailedBody(s.reason, vm, onCancelled)
    }
}

@Composable
private fun ReadyBody(s: AddContactUiState.Ready, vm: AddContactViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NfcStateBanner(s.nfcState)
        TabRow(selectedTabIndex = if (s.mode == Mode.AUTO) 0 else 1) {
            Tab(selected = s.mode == Mode.AUTO, onClick = { vm.setMode(Mode.AUTO) }) {
                Text("Auto", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = s.mode == Mode.MANUAL, onClick = { vm.setMode(Mode.MANUAL) }) {
                Text("Manual", modifier = Modifier.padding(12.dp))
            }
        }
        if (s.mode == Mode.AUTO) {
            Text(
                "Hold phones together — back-to-back, near the top.",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
            Text("Not working? Try Manual.", fontSize = 12.sp)
            Button(
                onClick = { vm.startPairing() },
                enabled = s.nfcState == NfcState.OK,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start pairing") }
        } else {
            Text("Manual pairing", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Hold phones together. Tap Send on one side while your friend taps Receive on theirs. Then swap.",
                fontSize = 12.sp
            )
            Button(onClick = { vm.manualSend() },    enabled = s.nfcState == NfcState.OK, modifier = Modifier.fillMaxWidth()) { Text("Send my contact") }
            OutlinedButton(onClick = { vm.manualReceive() }, enabled = s.nfcState == NfcState.OK, modifier = Modifier.fillMaxWidth()) { Text("Receive contact") }
        }
    }
}

@Composable
private fun FailedBody(reason: String, vm: AddContactViewModel, onCancelled: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(reason, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.retry() }) { Text("Retry") }
            OutlinedButton(onClick = onCancelled) { Text("Cancel") }
        }
    }
}

private fun addContactVmFactory(app: EarshotApp, activityHost: () -> Activity?) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            AddContactViewModel(app, activityHost) as T
    }

@Composable
private fun SafetyCodeBody(
    s: AddContactUiState.SafetyCode,
    vm: AddContactViewModel,
    onCancel: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            "Check this code matches on their phone",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            s.emojis.forEach { emoji ->
                Text(emoji, fontSize = 40.sp)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Pairing with ${s.incoming.displayName}", fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { vm.onSafetyConfirmed() }) { Text("Confirm") }
        }
    }
}
@Composable
private fun DuplicatePubKeyDialog(s: AddContactUiState.DuplicatePubKey, vm: AddContactViewModel) {
    AlertDialog(
        onDismissRequest = { vm.onDuplicateCancelled() },
        title = { Text("Update existing contact?") },
        text = {
            Text(
                "You've already paired with ${s.existing.displayName}. " +
                "Their name is now ${s.incoming.displayName}."
            )
        },
        confirmButton = {
            Button(onClick = { vm.onDuplicateConfirmed() }) { Text("Update") }
        },
        dismissButton = {
            OutlinedButton(onClick = { vm.onDuplicateCancelled() }) { Text("Cancel") }
        }
    )
}
@Composable
private fun DuplicateNameDialog(s: AddContactUiState.DuplicateName, vm: AddContactViewModel) {
    AlertDialog(
        onDismissRequest = { vm.onDuplicateCancelled() },
        title = { Text("Save as new contact?") },
        text = {
            Text(
                "You already have a contact called \"${s.existing.displayName}\" with a different key. " +
                "Save this new one too?"
            )
        },
        confirmButton = {
            Button(onClick = { vm.onDuplicateConfirmed() }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = { vm.onDuplicateCancelled() }) { Text("Cancel") }
        }
    )
}
