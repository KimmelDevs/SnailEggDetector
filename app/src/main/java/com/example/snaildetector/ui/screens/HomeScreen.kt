package com.example.snaildetector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snaildetector.data.DetectionRepository
import kotlinx.coroutines.launch

// ── Palette (matches existing theme) ─────────────────────────────────────────
private val DarkBg         = Color(0xFF0D1117)
private val CardBg         = Color(0xFF161B22)
private val PanelBg        = Color(0xFF1A2E1E)
private val Teal           = Color(0xFF1AA3CC)
private val GreenAccent    = Color(0xFF4ADE80)
private val GreenMuted     = Color(0xFF86EFAC)
private val GreenDim       = Color(0xFF14532D)
private val RedAccent      = Color(0xFFFCA5A5)
private val RedDim         = Color(0xFF7F1D1D)
private val TextPrimary    = Color(0xFFF0FDF4)
private val TextMuted      = Color(0xFF6B7280)
private val BorderSubtle   = Color(0xFF1F2937)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToDetect: () -> Unit  = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val repo  = remember { DetectionRepository() }
    val scope = rememberCoroutineScope()

    var totalScans    by remember { mutableIntStateOf(0) }
    var detectionCount by remember { mutableIntStateOf(0) }
    var daysSinceLast by remember { mutableStateOf<String>("—") }
    var isLoading     by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val all = repo.getAll()
                totalScans     = all.size
                detectionCount = all.count { it.eggClusterCount > 0 }
                val latest = all.firstOrNull()
                daysSinceLast = if (latest != null) {
                    val ts = latest.capturedAt ?: return@launch
                    val then = java.time.ZonedDateTime.parse(ts)
                    val days = java.time.temporal.ChronoUnit.DAYS.between(then, java.time.ZonedDateTime.now())
                    when (days) {
                        0L   -> "Today"
                        1L   -> "1d"
                        else -> "${days}d"
                    }
                } else "—"
            } catch (_: Exception) { /* silently degrade */ }
            finally { isLoading = false }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
    ) {

        // ── Header panel ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0A3D2E), DarkBg),
                        start  = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end    = androidx.compose.ui.geometry.Offset(0f, 600f)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 20.dp)
        ) {
            Column {
                Text(
                    text       = "Snail Egg Detector",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color      = GreenAccent,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text       = "Good morning,",
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
                Text(
                    text       = "Tracker",
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = GreenAccent
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Stats panel
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GreenAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text     = "System ready",
                                fontSize = 12.sp,
                                color    = GreenMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(
                                modifier = Modifier.weight(1f),
                                value    = if (isLoading) "…" else "$totalScans",
                                label    = "Total scans"
                            )
                            StatTile(
                                modifier  = Modifier.weight(1f),
                                value     = if (isLoading) "…" else "$detectionCount",
                                label     = "Detections",
                                valueColor = if (detectionCount > 0) RedAccent else TextPrimary
                            )
                            StatTile(
                                modifier = Modifier.weight(1f),
                                value    = if (isLoading) "…" else daysSinceLast,
                                label    = "Last scan"
                            )
                        }
                    }
                }
            }
        }

        // ── Body ──────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // CTA — start scanning
            Surface(
                shape  = RoundedCornerShape(16.dp),
                color  = PanelBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDetect() }
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF16A34A)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Search,
                            contentDescription = null,
                            tint               = TextPrimary,
                            modifier           = Modifier
                                .size(44.dp)
                                .padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start scanning", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Point camera at foliage", fontSize = 12.sp, color = GreenMuted)
                    }
                    Icon(
                        imageVector        = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint               = GreenAccent,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }

            // Section label
            Text(
                text      = "Recent detections",
                fontSize  = 11.sp,
                color     = TextMuted,
                letterSpacing = 1.5.sp,
                modifier  = Modifier.padding(top = 4.dp)
            )

            // Recent detection cards from repo
            if (isLoading) {
                Box(
                    modifier         = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color       = GreenAccent,
                        strokeWidth = 2.dp,
                        modifier    = Modifier.size(24.dp)
                    )
                }
            } else {
                val recents = remember(totalScans) { emptyList<Unit>() } // placeholder — wire repo.getAll() here
                // Show "no history" nudge if empty
                if (totalScans == 0) {
                    EmptyHistoryNudge(onNavigateToDetect)
                } else {
                    // View all button
                    ViewAllButton(onClick = onNavigateToHistory)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Components ────────────────────────────────────────────────────────────────

@Composable
private fun StatTile(
    modifier    : Modifier = Modifier,
    value       : String,
    label       : String,
    valueColor  : Color = TextPrimary
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GreenAccent.copy(alpha = 0.08f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Text(
                text       = value,
                fontSize   = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color      = valueColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text      = label.uppercase(),
                fontSize  = 9.sp,
                color     = GreenMuted,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun EmptyHistoryNudge(onNavigateToDetect: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = Color(0xFF0F1D12),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToDetect() }
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🐌", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text       = "No detections yet",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text     = "Tap Detect to scan for snail egg clusters",
                fontSize = 12.sp,
                color    = TextMuted
            )
        }
    }
}

@Composable
private fun ViewAllButton(onClick: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = Color(0xFF0F1D12),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = GreenAccent.copy(alpha = 0.08f)
            ) {
                Text(
                    text     = "+",
                    fontSize = 20.sp,
                    color    = GreenAccent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text       = "View all history",
                fontSize   = 13.sp,
                color      = GreenAccent,
                modifier   = Modifier.weight(1f)
            )
            Icon(
                imageVector        = Icons.Default.ChevronRight,
                contentDescription = null,
                tint               = GreenAccent,
                modifier           = Modifier.size(16.dp)
            )
        }
    }
}