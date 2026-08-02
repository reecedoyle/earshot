package com.earshot.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.earshot.app.ui.setup.NameSetupScreen

@Composable
fun EarshotNavHost(startAt: Route, hostActivity: ComponentActivity) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startAt.path) {
        composable(Route.NameSetup.path) {
            NameSetupScreen(onDone = {
                nav.navigate(Route.Home.path) {
                    popUpTo(Route.NameSetup.path) { inclusive = true }
                }
            })
        }
        composable(Route.Home.path)       { Text("Home stub — replaced in Task 15") }
        composable(Route.Settings.path)   { Text("Settings stub — replaced in Task 14") }
        composable(Route.Diagnostic.path) { Text("Diagnostic stub — replaced in Task 14") }
        composable(Route.AddContact.path) { Text("AddContact stub — replaced in Task 16") }
    }
}
