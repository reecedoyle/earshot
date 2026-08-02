package com.earshot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.earshot.app.ui.EarshotNavHost
import com.earshot.app.ui.Route

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    var startAt by remember { mutableStateOf<Route?>(null) }
                    val container = (application as EarshotApp).container
                    LaunchedEffect(Unit) {
                        val hasId = container.identityRepo.hasIdentity()
                        startAt = if (hasId) Route.Home else Route.NameSetup
                    }
                    startAt?.let { EarshotNavHost(startAt = it, hostActivity = this@MainActivity) }
                }
            }
        }
    }
}
