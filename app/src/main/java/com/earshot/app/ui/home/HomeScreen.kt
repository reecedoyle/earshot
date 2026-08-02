package com.earshot.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earshot.app.EarshotApp
import com.earshot.app.data.Contact
import com.earshot.app.data.fingerprint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val app = LocalContext.current.applicationContext as EarshotApp
    val vm: HomeViewModel = viewModel(factory = homeVmFactory(app))
    val identity by vm.identity.collectAsState()
    val contacts by vm.contacts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Earshot") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add contact")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            val id = identity
            if (id != null) {
                Text(
                    text = "You are: ${id.displayName} · ${id.publicKey.fingerprint()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            if (contacts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No contacts yet — tap + to pair with someone nearby.", fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(contacts, key = { it.publicKey.contentHashCode() }) { c ->
                        ContactRow(c)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(c: Contact) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(c.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(c.publicKey.fingerprint(), fontSize = 12.sp)
    }
}

private fun homeVmFactory(app: EarshotApp) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(app) as T
    }
