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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.util.Locale

@Composable
fun HistoryScreen() {
    val repo  = remember { DetectionRepository() }
    val scope = rememberCoroutineScope()

    var detections   by remember { mutableStateOf<List<SnailDetection>>(emptyList()) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun loadHistory() {
        isLoading = true
        errorMessage = null
        try {
            detections = repo.getAll()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown error"
            android.util.Log.e("HistoryScreen", "Failed to load detections", e)
        } finally {
            isLoading = false
        }
    }

    // Load on first composition
    LaunchedEffect(Unit) {
        loadHistory()
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

                Column(horizontalAlignment = Alignment.End) {
                    if (!isLoading) {
                        Text(
                            text     = "${detections.size} record${if (detections.size != 1) "s" else ""}",
                            fontSize = 13.sp,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    IconButton(
                        onClick = { scope.launch { loadHistory() } },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh history"
                            )
                        }
                    }
                }
            }
        }

        // ── Body ──────────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && detections.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                errorMessage != null && detections.isEmpty() -> ErrorState(
                    message = errorMessage.orEmpty(),
                    onRetry = { scope.launch { loadHistory() } },
                    modifier = Modifier.align(Alignment.Center)
                )

                detections.isEmpty() -> EmptyState(
                    onRefresh = { scope.launch { loadHistory() } }
                )

                else -> LazyColumn(
                    contentPadding    = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        HistorySummaryCard(detections = detections)
                    }

                    if (errorMessage != null) {
                        item {
                            InlineErrorCard(
                                message = errorMessage.orEmpty(),
                                onRetry = { scope.launch { loadHistory() } }
                            )
                        }
                    }

                    items(detections, key = { it.id ?: it.eventId }) { det ->
                        DetectionCard(
                            detection = det,
                            onDelete  = {
                                scope.launch {
                                    val id = det.id
                                    if (id == null) {
                                        errorMessage = "This record cannot be deleted because it has no identifier."
                                        return@launch
                                    }

                                    val deleted = repo.delete(id)
                                    if (deleted) {
                                        detections = detections.filterNot { current ->
                                            (current.id ?: current.eventId) == (det.id ?: det.eventId)
                                        }
                                    } else {
                                        errorMessage = "Failed to delete the record. Please try again."
                                    }
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
    var showDetails by rememberSaveable(detection.id ?: detection.eventId) { mutableStateOf(false) }
    val source = detection.metadata?.get("source")
    val hasDetails = detection.id != null ||
        detection.createdAt != null ||
        detection.updatedAt != null ||
        detection.photoOriginalName != null ||
        detection.photoMimeType != null ||
        detection.photoPath != null ||
        detection.photoSize != null ||
        detection.metadata?.isNotEmpty() == true

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
        Column(modifier = Modifier.padding(12.dp)) {
            Row {

                // Thumbnail
                DetectionThumbnail(photoUrl = detection.photoUrl)

                Spacer(modifier = Modifier.width(14.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {

                    // Egg count badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                        HistoryTag(label = detection.platform.replaceFirstChar { it.uppercase() })

                        if (!source.isNullOrBlank()) {
                            HistoryTag(label = source.replace('_', ' '))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Date
                    Text(
                        text      = formatTimestamp(detection.capturedAt ?: detection.createdAt),
                        fontSize  = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color     = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Event ID (truncated)
                    Text(
                        text     = truncateValue(detection.eventId, maxChars = 38),
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

                    detection.photoOriginalName?.let {
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            maxLines = 1
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

            if (hasDetails) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showDetails = !showDetails },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (showDetails) "Hide details" else "Show details",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detection.id?.let { DetailRow(label = "Record ID", value = it) }
                        DetailRow(label = "Event ID", value = detection.eventId)
                        DetailRow(label = "Platform", value = detection.platform)
                        detection.capturedAt?.let { DetailRow(label = "Captured", value = formatTimestamp(it)) }
                        detection.createdAt?.let { DetailRow(label = "Created", value = formatTimestamp(it)) }
                        detection.updatedAt?.let { DetailRow(label = "Updated", value = formatTimestamp(it)) }
                        detection.photoOriginalName?.let { DetailRow(label = "Photo Name", value = it) }
                        detection.photoMimeType?.let { DetailRow(label = "Photo Type", value = it) }
                        detection.photoSize?.let { DetailRow(label = "Photo Size", value = formatSize(it)) }
                        detection.photoPath?.let { DetailRow(label = "Storage Path", value = it) }

                        if (!detection.metadata.isNullOrEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "Metadata",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )

                            detection.metadata.toSortedMap().forEach { (key, value) ->
                                DetailRow(label = key.replaceFirstChar { it.uppercase() }, value = value)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(detections: List<SnailDetection>) {
    val totalClusters = detections.sumOf { it.eggClusterCount }
    val positiveDetections = detections.count { it.eggClusterCount > 0 }
    val latestTimestamp = detections.firstOrNull()?.capturedAt ?: detections.firstOrNull()?.createdAt

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Repository History",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "Records",
                    value = detections.size.toString()
                )
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "Egg Clusters",
                    value = totalClusters.toString()
                )
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "Positive",
                    value = positiveDetections.toString()
                )
            }

            latestTimestamp?.let {
                Text(
                    text = "Latest capture: ${formatTimestamp(it)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun HistoryTag(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Unable to load history",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onRetry) {
            Text("Try again")
        }
    }
}

@Composable
private fun InlineErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
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
private fun EmptyState(onRefresh: () -> Unit) {
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
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh) {
            Text("Refresh")
        }
    }
}

// ── Formatting helpers ────────────────────────────────────────────────────────

private fun formatTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"

    val displayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a", Locale.US)

    val parsed = runCatching {
        ZonedDateTime.parse(raw).format(displayFormatter)
    }.getOrNull()
        ?: runCatching {
            OffsetDateTime.parse(raw).format(displayFormatter)
        }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(
                raw,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'", Locale.US)
            ).atOffset(ZoneOffset.UTC).format(displayFormatter)
        }.getOrNull()

    return parsed ?: raw.replace("T", " ").removeSuffix("Z")
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1_024     -> "%.1f KB".format(bytes / 1_024f)
    else               -> "$bytes B"
}

private fun truncateValue(value: String, maxChars: Int): String {
    if (value.length <= maxChars) return value
    return value.take(maxChars - 1) + "…"
}