package com.example.snaildetector.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.snaildetector.supabase
import com.example.snaildetector.ui.screens.DetectScreen
import com.example.snaildetector.ui.screens.DetectionDetailScreen
import com.example.snaildetector.ui.screens.HistoryScreen
import com.example.snaildetector.ui.screens.HomeScreen
import com.example.snaildetector.ui.screens.LoginScreen
import com.example.snaildetector.ui.screens.SignUpScreen
import com.example.snaildetector.ui.screens.ProfileScreen
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.SessionStatus

@Composable
fun AppNavGraph() {
    val navController      = rememberNavController()
    val navBackStack       by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStack?.destination

    // ── Auth state listener ───────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        supabase.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
                is SessionStatus.NotAuthenticated -> {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
                else -> Unit
            }
        }
    }

    val mainRoutes    = bottomNavItems.map { it.screen.route }
    val showBottomBar = currentDestination?.route in mainRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon     = { Icon(item.icon, contentDescription = item.label) },
                            label    = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true,
                            onClick  = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Login.route,
            modifier         = Modifier.padding(innerPadding)
        ) {

            // ── Auth screens ──────────────────────────────────────────────────
            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) },
                    onLoginSuccess     = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.SignUp.route) {
                SignUpScreen(
                    onNavigateToLogin = { navController.popBackStack() },
                    onSignUpSuccess   = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // ── Main tabs ─────────────────────────────────────────────────────
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToDetect  = {
                        navController.navigate(Screen.Detect.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }

            composable(Screen.Detect.route) {
                DetectScreen()
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    onDetectionClick = { eventId ->
                        navController.navigate(Screen.DetectionDetail.createRoute(eventId))
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // ── Detail screen ─────────────────────────────────────────────────
            composable(Screen.DetectionDetail.route) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                DetectionDetailScreen(
                    eventId = eventId,
                    onBack  = { navController.popBackStack() }
                )
            }
        }
    }
}