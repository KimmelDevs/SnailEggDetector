package com.example.snaildetector.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snaildetector.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Palette (mirrors HomeScreen) ──────────────────────────────────────────────
private val PBg           = Color(0xFF0A0A0A)
private val PSurface      = Color(0xFF111111)
private val PBorder10     = Color(0x1AFFFFFF)
private val PBorder20     = Color(0x33FFFFFF)
private val PTextPrimary  = Color(0xFFFFFFFF)
private val PTextMuted    = Color(0xFF888888)
private val PAccentRed    = Color(0xFFE74C3C)

// ── Data model ────────────────────────────────────────────────────────────────

@Serializable
data class SnailTracker(
    @SerialName("id")   val id   : String,
    @SerialName("name") val name : String
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(onLogout: () -> Unit) {

    var trackerName  by remember { mutableStateOf<String?>(null) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDialog   by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val uid = supabase.auth.currentUserOrNull()?.id ?: return@LaunchedEffect

            val rows = supabase.from("snailtrackers")
                .select { filter { eq("id", uid) } }
                .decodeList<SnailTracker>()

            if (rows.isNotEmpty()) {
                trackerName = rows.first().name
            } else {
                val authName = supabase.auth.currentUserOrNull()
                    ?.userMetadata
                    ?.get("full_name")
                    ?.toString()
                    ?.trim('"')
                    ?: "Snail Tracker"

                supabase.from("snailtrackers")
                    .upsert(SnailTracker(id = uid, name = authName))

                trackerName = authName
            }
        } catch (e: Exception) {
            errorMessage = "Could not load profile: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // ── Logout dialog ─────────────────────────────────────────────────────────
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor   = Color(0xFF161616),
            iconContentColor = PAccentRed,
            titleContentColor = PTextPrimary,
            textContentColor  = PTextMuted,
            icon  = { Icon(Icons.Default.Logout, contentDescription = null) },
            title = { Text("Log out?", fontWeight = FontWeight.Bold) },
            text  = { Text("You'll need to sign in again to use the app.") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    scope.launch {
                        try { supabase.auth.signOut() } catch (_: Exception) {}
                        onLogout()
                    }
                }) {
                    Text(
                        "Log out",
                        color      = PAccentRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = PTextMuted)
                }
            }
        )
    }

    // ── Root ──────────────────────────────────────────────────────────────────
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(PBg),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator(color = PTextPrimary)

            errorMessage != null -> Text(
                text      = errorMessage!!,
                color     = PAccentRed,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(24.dp)
            )

            else -> ProfileContent(
                name       = trackerName ?: "",
                onLogout   = { showDialog = true }
            )
        }
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun ProfileContent(
    name     : String,
    onLogout : () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top zone with dot-grid ─────────────────────────────────────────
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color(0xFF0D0D0D))
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            // Dot-grid texture (same helper pattern as HomeScreen)
            ProfileDotGrid(
                modifier  = Modifier.fillMaxSize(),
                dotColor  = Color.White,
                dotRadius = 1.2.dp,
                spacing   = 22.dp,
                dotAlpha  = 0.05f
            )

            // Avatar + name stacked over the texture
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MonogramAvatar(name = name, size = 96.dp)

                Spacer(Modifier.height(16.dp))

                Text(
                    text       = name,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Black,
                    color      = PTextPrimary,
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Role pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A0A0A))
                        .border(1.dp, PAccentRed.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text       = "🐌  Snail Tracker",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = PAccentRed
                    )
                }
            }

            // Bottom divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PBorder10)
                    .align(Alignment.BottomCenter)
            )
        }

        // ── Info cards ─────────────────────────────────────────────────────
        Spacer(Modifier.height(28.dp))

        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileInfoRow(label = "ROLE",   value = "Field Researcher")
            ProfileInfoRow(label = "STATUS", value = "Active")
            ProfileInfoRow(label = "ACCESS", value = "Standard")
        }

        Spacer(Modifier.weight(1f))

        // ── Logout button ──────────────────────────────────────────────────
        Button(
            onClick  = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .height(52.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PAccentRed,
                contentColor   = Color.White
            )
        ) {
            Icon(
                Icons.Default.Logout,
                contentDescription = null,
                modifier           = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Log Out",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Monogram avatar ───────────────────────────────────────────────────────────

@Composable
private fun MonogramAvatar(name: String, size: Dp) {
    val initials = name
        .trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it[0].uppercaseChar().toString() }
        .ifEmpty { "?" }

    Box(contentAlignment = Alignment.Center) {
        // Outer border ring
        Box(
            modifier = Modifier
                .size(size + 6.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(1.5.dp, PBorder20, CircleShape)
        )
        // Avatar circle
        Box(
            modifier         = Modifier
                .size(size)
                .clip(CircleShape)
                .background(PSurface)
                .border(1.dp, PBorder10, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = initials,
                fontSize   = (size.value * 0.35f).sp,
                fontWeight = FontWeight.Black,
                color      = PTextPrimary
            )
        }
    }
}

// ── Info row card ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PSurface)
            .border(1.dp, PBorder10, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text          = label,
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color         = PTextMuted
        )
        Text(
            text       = value,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = PTextPrimary
        )
    }
}

// ── Dot-grid canvas (local copy — or extract to a shared util) ────────────────

@Composable
private fun ProfileDotGrid(
    modifier  : Modifier,
    dotColor  : Color,
    dotRadius : Dp,
    spacing   : Dp,
    dotAlpha  : Float
) {
    Canvas(modifier = modifier) {
        val radiusPx  = dotRadius.toPx()
        val spacingPx = spacing.toPx()
        val color     = dotColor.copy(alpha = dotAlpha)
        var y = spacingPx / 2f
        while (y < size.height) {
            var x = spacingPx / 2f
            while (x < size.width) {
                drawCircle(color = color, radius = radiusPx, center = Offset(x, y))
                x += spacingPx
            }
            y += spacingPx
        }
    }
}