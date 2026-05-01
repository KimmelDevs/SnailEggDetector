package com.example.snaildetector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ── Home ──────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen() {
    BlankPlaceholder(label = "Home")
}

// ── Detect ────────────────────────────────────────────────────────────────────

@Composable
fun DetectScreen() {
    BlankPlaceholder(label = "Detect")
}

// ── History ───────────────────────────────────────────────────────────────────

@Composable
fun HistoryScreen() {
    BlankPlaceholder(label = "History")
}

// ── Profile ───────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen() {
    BlankPlaceholder(label = "Profile")
}

// ── Internal helper ───────────────────────────────────────────────────────────

@Composable
private fun BlankPlaceholder(label: String) {
    Box(
        modifier          = Modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text  = "Coming soon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
