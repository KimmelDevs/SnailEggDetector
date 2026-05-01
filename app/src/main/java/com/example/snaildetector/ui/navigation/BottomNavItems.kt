package com.example.snaildetector.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen
)

val bottomNavItems = listOf(
    BottomNavItem("Home",    Icons.Filled.Home,          Screen.Home),
    BottomNavItem("Detect",  Icons.Filled.Search,        Screen.Detect),
    BottomNavItem("History", Icons.Filled.History,       Screen.History),
    BottomNavItem("Profile", Icons.Filled.AccountCircle, Screen.Profile),
)
