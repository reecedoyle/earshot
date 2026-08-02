package com.earshot.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.earshot.app.diag.DiagnosticScreen
import com.earshot.app.ui.home.HomeScreen
import com.earshot.app.ui.settings.SettingsScreen
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
        composable(Route.Home.path) {
            HomeScreen(
                onAdd = { nav.navigate(Route.AddContact.path) },
                onOpenSettings = { nav.navigate(Route.Settings.path) }
            )
        }
        composable(Route.Settings.path)   { SettingsScreen(onOpenDiagnostic = { nav.navigate(Route.Diagnostic.path) }) }
        composable(Route.Diagnostic.path) { DiagnosticScreen() }
        composable(Route.AddContact.path) { Text("AddContact stub — replaced in Task 16") }
    }
}
