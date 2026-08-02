package com.earshot.app.ui

sealed class Route(val path: String) {
    data object NameSetup  : Route("name-setup")
    data object Home       : Route("home")
    data object Settings   : Route("settings")
    data object Diagnostic : Route("diagnostic")
    data object AddContact : Route("add-contact")
}
