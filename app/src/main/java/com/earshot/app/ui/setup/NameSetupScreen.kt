package com.earshot.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earshot.app.EarshotApp
import androidx.compose.ui.platform.LocalContext

@Composable
fun NameSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EarshotApp
    val vm: NameSetupViewModel = viewModel(factory = viewModelFactory(app))
    val name by vm.name.collectAsState()
    val submitting by vm.submitting.collectAsState()
    val done by vm.done.collectAsState()

    LaunchedEffect(done) { if (done) onDone() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Earshot", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "What should we call you? This is the name shown when you pair with someone.",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = vm::onNameChanged,
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !submitting
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::submit,
            enabled = vm.canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (submitting) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text("Continue")
        }
    }
}

private fun viewModelFactory(app: EarshotApp) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            NameSetupViewModel(app) as T
    }
