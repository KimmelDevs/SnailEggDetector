package com.example.snaildetector.ui.navigation

sealed class Screen(val route: String) {
    // Auth flow
    object Login  : Screen("login")
    object SignUp : Screen("signup")

    // Main (bottom nav) tabs
    object Home     : Screen("home")
    object Detect   : Screen("detect")
    object History  : Screen("history")
    object Profile  : Screen("profile")
}
