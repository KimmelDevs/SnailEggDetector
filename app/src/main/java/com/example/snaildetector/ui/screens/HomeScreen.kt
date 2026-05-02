package com.example.snaildetector.ui.screens

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.snaildetector.data.DetectionRepository
import com.example.snaildetector.data.SnailDetection
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ── Palette ───────────────────────────────────────────────────────────────────
private val HeroBg        = Color(0xFF0A0A0A)
private val HeroDot       = Color(0xFFFFFFFF)
private val AccentRed     = Color(0xFFE74C3C)
private val SurfaceBlack  = Color(0xFF111111)
private val BorderWhite10 = Color(0x1AFFFFFF)
private val BorderWhite20 = Color(0x33FFFFFF)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextMuted     = Color(0xFF888888)
private val PulseColor    = Color(0xFF4ADE80)   // single green — only used for the pulse dot

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToDetect  : () -> Unit = {},
    onNavigateToHistory : () -> Unit = {}
) {
    val repo = remember { DetectionRepository() }

    var detections   by remember { mutableStateOf<List<SnailDetection>>(emptyList()) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Log.d("HomeScreen", "Loading detections")
        try {
            detections = repo.getAll()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown error"
            Log.e("HomeScreen", "Failed to load detections", e)
        } finally {
            isLoading = false
        }
    }

    val recent        = detections.take(3)
    val totalScans    = detections.size
    val totalEggs     = detections.count { it.eggClusterCount > 0 }
    val daysSinceLast = detections.firstOrNull()?.capturedAt?.let { raw ->
        try {
            val days = ChronoUnit.DAYS.between(ZonedDateTime.parse(raw), ZonedDateTime.now())
            when (days) {
                0L   -> "Today"
                1L   -> "1d ago"
                else -> "${days}d ago"
            }
        } catch (_: Exception) { "—" }
    } ?: "—"

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {

        // ── Hero Header ───────────────────────────────────────────────────────
        HeroHeader(
            isLoading     = isLoading,
            hasError      = errorMessage != null,
            totalScans    = totalScans,
            totalEggs     = totalEggs,
            daysSinceLast = daysSinceLast
        )

        // ── Body ──────────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = Color.White
                )

                errorMessage != null -> Text(
                    text      = "Error: $errorMessage",
                    color     = AccentRed,
                    modifier  = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center
                )

                detections.isEmpty() -> HomeEmptyState(onNavigateToDetect)

                else -> LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    item {
                        Text(
                            text       = "RECENT DETECTIONS",
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color      = TextMuted,
                            modifier   = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    items(recent, key = { it.id ?: it.eventId }) { det ->
                        HomeDetectionCard(
                            detection = det,
                            onClick   = { onNavigateToHistory() }
                        )
                    }

                    item {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceBlack)
                                .border(1.dp, BorderWhite10, RoundedCornerShape(12.dp))
                                .clickable { onNavigateToHistory() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text       = "View all ${detections.size} record${if (detections.size != 1) "s" else ""}",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = TextPrimary,
                                modifier   = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint               = TextMuted,
                                modifier           = Modifier.size(18.dp)
                            )
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ── Hero Header ───────────────────────────────────────────────────────────────

@Composable
private fun HeroHeader(
    isLoading     : Boolean,
    hasError      : Boolean,
    totalScans    : Int,
    totalEggs     : Int,
    daysSinceLast : String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeroBg)
            .statusBarsPadding()
    ) {
        // Dot-grid canvas texture
        DotGridCanvas(
            modifier    = Modifier.fillMaxWidth().height(220.dp),
            dotColor    = HeroDot,
            dotRadius   = 1.2.dp,
            spacing     = 22.dp,
            dotAlpha    = 0.06f
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 24.dp)
        ) {
            // Status row
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot()
                Spacer(Modifier.width(6.dp))
                Text(
                    text      = "SNAIL DETECTOR",
                    fontSize  = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color     = TextMuted
                )
            }

            Spacer(Modifier.height(10.dp))

            // Big greeting
            Text(
                text       = "Field\nMonitor",
                fontSize   = 40.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 44.sp,
                color      = TextPrimary
            )

            Spacer(Modifier.height(24.dp))

            // Stat tiles — only once data is ready
            if (!isLoading && !hasError) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStat(modifier = Modifier.weight(1f), label = "Scans",     value = "$totalScans")
                    HeroStat(modifier = Modifier.weight(1f), label = "With eggs", value = "$totalEggs",  accent = totalEggs > 0)
                    HeroStat(modifier = Modifier.weight(1f), label = "Last scan", value = daysSinceLast)
                }
            }
        }

        // Bottom edge line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderWhite10)
                .align(Alignment.BottomCenter)
        )
    }
}

// ── Dot-grid canvas ───────────────────────────────────────────────────────────

@Composable
private fun DotGridCanvas(
    modifier  : Modifier = Modifier,
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

// ── Pulsing status dot ────────────────────────────────────────────────────────

@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = 1.6f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue   = 0.9f,
        targetValue    = 0.3f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer ring
        Box(
            modifier = Modifier
                .size((6 * scale).dp)
                .clip(CircleShape)
                .background(PulseColor.copy(alpha = alpha * 0.4f))
        )
        // Core dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(PulseColor)
        )
    }
}

// ── Hero stat tile ────────────────────────────────────────────────────────────

@Composable
private fun HeroStat(
    modifier : Modifier = Modifier,
    label    : String,
    value    : String,
    accent   : Boolean = false
) {
    val borderColor = if (accent) AccentRed.copy(alpha = 0.7f) else BorderWhite20
    val valueColor  = if (accent) AccentRed else TextPrimary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161616))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text       = value,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Black,
                color      = valueColor
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text      = label,
                fontSize  = 10.sp,
                fontWeight = FontWeight.Medium,
                color     = TextMuted,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ── Detection card ────────────────────────────────────────────────────────────

@Composable
private fun HomeDetectionCard(
    detection : SnailDetection,
    onClick   : () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceBlack)
            .border(1.dp, BorderWhite10, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeDetectionThumbnail(photoUrl = detection.photoUrl)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {

                // Egg cluster badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (detection.eggClusterCount > 0) AccentRed
                            else Color(0xFF222222)
                        )
                        .border(
                            1.dp,
                            if (detection.eggClusterCount > 0) Color.Transparent
                            else BorderWhite10,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text       = "🥚 ${detection.eggClusterCount} cluster${if (detection.eggClusterCount != 1) "s" else ""}",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (detection.eggClusterCount > 0) Color.White else TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text       = homeFormatTimestamp(detection.capturedAt),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text     = detection.eventId.take(32) + "…",
                    fontSize = 10.sp,
                    color    = TextMuted
                )

                detection.photoSize?.let {
                    Text(
                        text     = homeFormatSize(it),
                        fontSize = 10.sp,
                        color    = TextMuted
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View history",
                tint               = TextMuted,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

// ── Thumbnail ─────────────────────────────────────────────────────────────────

@Composable
private fun HomeDetectionThumbnail(photoUrl: String?) {
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier         = Modifier
            .size(80.dp)
            .clip(shape)
            .background(Color(0xFF1A1A1A))
            .border(1.dp, BorderWhite10, shape),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null) {
            var state by remember {
                mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null))
            }

            AsyncImage(
                model              = photoUrl,
                contentDescription = "Detection photo",
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
                onState            = { state = it }
            )

            if (state is AsyncImagePainter.State.Loading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color       = Color.White
                )
            }
            if (state is AsyncImagePainter.State.Error) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint               = TextMuted,
                    modifier           = Modifier.size(32.dp)
                )
            }
        } else {
            Icon(
                Icons.Default.ImageSearch,
                contentDescription = null,
                tint               = TextMuted,
                modifier           = Modifier.size(32.dp)
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun HomeEmptyState(onNavigateToDetect: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .clickable { onNavigateToDetect() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🐌", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No detections yet",
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Head to the Detect tab to scan for snail eggs.",
            fontSize  = 14.sp,
            color     = TextMuted,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 40.dp)
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun homeFormatTimestamp(raw: String?): String {
    if (raw == null) return "—"
    return try {
        ZonedDateTime.parse(raw)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a", Locale.US))
    } catch (_: Exception) {
        raw.take(19).replace("T", "  ")
    }
}

private fun homeFormatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1_024     -> "%.1f KB".format(bytes / 1_024f)
    else               -> "$bytes B"
}