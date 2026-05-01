package com.example.snaildetector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snaildetector.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// ── Data model matching the snailtrackers table ───────────────────────────────

@Serializable
data class SnailTracker(
    val id   : String,
    val name : String
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(onLogout: () -> Unit) {

    var trackerName  by remember { mutableStateOf<String?>(null) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDialog   by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Load name from snailtrackers on first composition
    LaunchedEffect(Unit) {
        try {
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@LaunchedEffect

            // Try to fetch existing row
            val rows = supabase.postgrest["snailtrackers"]
                .select { filter { eq("id", uid) } }
                .decodeList<SnailTracker>()

            if (rows.isNotEmpty()) {
                trackerName = rows.first().name
            } else {
                // First login — upsert a row using the name from auth metadata
                val authName = supabase.auth.currentUserOrNull()
                    ?.userMetadata
                    ?.get("full_name")
                    ?.toString()
                    ?.trim('"')    // Supabase wraps string values in quotes
                    ?: "Snail Tracker"

                supabase.postgrest["snailtrackers"]
                    .upsert(mapOf("id" to uid, "name" to authName))

                trackerName = authName
            }
        } catch (e: Exception) {
            errorMessage = "Could not load profile: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // ── Logout confirmation dialog ─────────────────────────────────────────────
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon             = { Icon(Icons.Default.Logout, contentDescription = null) },
            title            = { Text("Log out?") },
            text             = { Text("You'll need to sign in again to use the app.") },
            confirmButton    = {
                TextButton(onClick = {
                    showDialog = false
                    scope.launch {
                        try { supabase.auth.signOut() } catch (_: Exception) {}
                        onLogout()
                    }
                }) { Text("Log out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton    = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator()

            errorMessage != null -> Text(
                text  = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )

            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {

                // Avatar circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector        = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier           = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Name
                Text(
                    text       = trackerName ?: "",
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text     = "🐌 Snail Tracker",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Log out button
                OutlinedButton(
                    onClick  = { showDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        // tint the border red too
                    )
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log out", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}