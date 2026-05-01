package com.example.snaildetector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.snaildetector.data.DetectionRepository
import com.example.snaildetector.data.SnailDetection
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen() {
    val repo  = remember { DetectionRepository() }
    val scope = rememberCoroutineScope()

    var detections   by remember { mutableStateOf<List<SnailDetection>>(emptyList()) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            detections = repo.getAll()
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────────
        Surface(shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Detection History",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f)
                )
                if (!isLoading) {
                    Text(
                        text     = "${detections.size} record${if (detections.size != 1) "s" else ""}",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // ── Body ──────────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                errorMessage != null -> Text(
                    text      = "Error: $errorMessage",
                    color     = MaterialTheme.colorScheme.error,
                    modifier  = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center
                )

                detections.isEmpty() -> EmptyState()

                else -> LazyColumn(
                    contentPadding    = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(detections, key = { it.id ?: it.eventId }) { det ->
                        DetectionCard(
                            detection = det,
                            onDelete  = {
                                scope.launch {
                                    det.id?.let { repo.delete(it) }
                                    detections = detections.filter { it.id != det.id }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Detection card ────────────────────────────────────────────────────────────

@Composable
private fun DetectionCard(
    detection : SnailDetection,
    onDelete  : () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title            = { Text("Delete record?") },
            text             = { Text("This will remove the detection entry. The photo in storage will be kept.") },
            confirmButton    = {
                TextButton(onClick = { showConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {

            // Thumbnail
            DetectionThumbnail(photoUrl = detection.photoUrl)

            Spacer(modifier = Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {

                // Egg count badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (detection.eggClusterCount > 0)
                            Color(0xFFE74C3C) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text     = "🥚 ${detection.eggClusterCount} cluster${if (detection.eggClusterCount != 1) "s" else ""}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color    = if (detection.eggClusterCount > 0) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Date
                Text(
                    text      = formatTimestamp(detection.capturedAt),
                    fontSize  = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color     = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Event ID (truncated)
                Text(
                    text     = detection.eventId.take(32) + "…",
                    fontSize = 11.sp,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                // Photo size
                detection.photoSize?.let {
                    Text(
                        text     = formatSize(it),
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            // Delete button
            IconButton(onClick = { showConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}

// ── Thumbnail ─────────────────────────────────────────────────────────────────

@Composable
private fun DetectionThumbnail(photoUrl: String?) {
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier         = Modifier
            .size(80.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null) {
            var state by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null)) }

            AsyncImage(
                model             = photoUrl,
                contentDescription = "Detection photo",
                contentScale      = ContentScale.Crop,
                modifier          = Modifier.fillMaxSize(),
                onState           = { state = it }
            )

            if (state is AsyncImagePainter.State.Loading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            if (state is AsyncImagePainter.State.Error) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(32.dp)
                )
            }
        } else {
            Icon(
                Icons.Default.ImageSearch,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(32.dp)
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🐌", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No detections yet",
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Head to the Detect tab to scan for snail eggs.",
            fontSize  = 14.sp,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 40.dp)
        )
    }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

private fun formatTimestamp(raw: String?): String {
    if (raw == null) return "—"
    return try {
        val zdt = ZonedDateTime.parse(raw)
        zdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a", Locale.US))
    } catch (_: Exception) {
        raw.take(19).replace("T", "  ")
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1_024     -> "%.1f KB".format(bytes / 1_024f)
    else               -> "$bytes B"
}