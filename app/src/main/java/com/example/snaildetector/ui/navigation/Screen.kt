package com.example.snaildetector.ui.navigation

sealed class Screen(val route: String) {
    // Splash
    object Splash : Screen("splash")

    // Auth flow
    object Login  : Screen("login")
    object SignUp : Screen("signup")

    // Main (bottom nav) tabs
    object Home    : Screen("home")
    object Detect  : Screen("detect")
    object History : Screen("history")
    object Profile : Screen("profile")

    // Detail — eventId passed as a path segment
    object DetectionDetail : Screen("detection/{eventId}") {
        fun createRoute(eventId: String) = "detection/$eventId"
    }
}