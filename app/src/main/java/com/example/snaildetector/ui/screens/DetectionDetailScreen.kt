package com.example.snaildetector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.snaildetector.data.DetectionRepository
import com.example.snaildetector.data.SnailDetection
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionDetailScreen(
    eventId : String,
    onBack  : () -> Unit
) {
    val repo = remember { DetectionRepository() }
    var detection    by remember { mutableStateOf<SnailDetection?>(null) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(eventId) {
        isLoading = true
        try {
            // Reuse getAll and find the matching record — avoids adding a new
            // repo method just for this screen
            detection = repo.getAll().firstOrNull { it.eventId == eventId }
            if (detection == null) errorMessage = "Record not found"
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown error"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title         = { Text("Detection Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                errorMessage != null -> Text(
                    text     = errorMessage!!,
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )

                detection != null -> DetailContent(detection = detection!!)
            }
        }
    }
}

@Composable
private fun DetailContent(detection: SnailDetection) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Full-width photo ──────────────────────────────────────────────────
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (detection.photoUrl != null) {
                var state by remember {
                    mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null))
                }
                AsyncImage(
                    model              = detection.photoUrl,
                    contentDescription = "Detection photo",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                    onState            = { state = it }
                )
                if (state is AsyncImagePainter.State.Loading) {
                    CircularProgressIndicator()
                }
                if (state is AsyncImagePainter.State.Error) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier           = Modifier.size(56.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text("No photo available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Details ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Egg count — big badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (detection.eggClusterCount > 0) Color(0xFFE74C3C)
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text       = "🥚  ${detection.eggClusterCount} egg cluster${if (detection.eggClusterCount != 1) "s" else ""} detected",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (detection.eggClusterCount > 0) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            HorizontalDivider()

            // Info rows
            InfoRow("Captured",  formatTimestamp(detection.capturedAt))
            InfoRow("Platform",  detection.platform.replaceFirstChar { it.uppercase() })
            InfoRow("Event ID",  detection.eventId)
            detection.id?.let          { InfoRow("Record ID",   it) }
            detection.photoPath?.let   { InfoRow("Storage path", it) }
            detection.photoSize?.let   { InfoRow("Photo size",   formatSize(it)) }
            detection.photoMimeType?.let { InfoRow("Format",     it) }

            // Metadata
            if (!detection.metadata.isNullOrEmpty()) {
                HorizontalDivider()
                Text(
                    text       = "Metadata",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                detection.metadata.forEach { (k, v) ->
                    InfoRow(k, v)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment   = Alignment.Top
    ) {
        Text(
            text       = label,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier   = Modifier.weight(0.38f)
        )
        Text(
            text     = value,
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.62f)
        )
    }
}

private fun formatTimestamp(raw: String?): String {
    if (raw == null) return "—"
    return try {
        ZonedDateTime.parse(raw)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm:ss a z", Locale.US))
    } catch (_: Exception) {
        raw.take(19).replace("T", "  ")
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1_024     -> "%.1f KB".format(bytes / 1_024f)
    else               -> "$bytes B"
}