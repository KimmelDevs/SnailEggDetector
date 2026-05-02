package com.example.snaildetector.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.snaildetector.data.DetectionRepository
import com.example.snaildetector.detection.SnailDetectionOverlay
import com.example.snaildetector.detection.SnailDetector
import kotlinx.coroutines.launch

private const val TAG = "DetectScreen"

// ── Detect ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalGetImage::class)
@Composable
fun DetectScreen() {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()

    val detector = remember { SnailDetector(context) }
    val repo     = remember { DetectionRepository() }
    DisposableEffect(Unit) { onDispose { detector.close() } }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var cameraFacing   by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var detectionText  by remember { mutableStateOf("Scanning…") }
    var eggCount       by remember { mutableIntStateOf(0) }
    var isSaving       by remember { mutableStateOf(false) }
    var lastSavedCount by remember { mutableIntStateOf(-1) }
    var lastSaveTimeMs by remember { mutableLongStateOf(0L) }
    var overlayRef     by remember { mutableStateOf<SnailDetectionOverlay?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackMessage   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackMessage) {
        snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackMessage = null
        }
    }

    // Auto-save whenever eggs are detected
    LaunchedEffect(eggCount) {
        val now           = System.currentTimeMillis()
        val cooldownMs    = 10_000L
        val sinceLastSave = now - lastSaveTimeMs

        Log.d(TAG, "eggCount changed → $eggCount | isSaving=$isSaving " +
                "| lastSavedCount=$lastSavedCount | sinceLastSave=${sinceLastSave}ms")

        if (eggCount <= 0) {
            Log.d(TAG, "Skip save: no detections (eggCount=$eggCount)")
            lastSavedCount = -1
            return@LaunchedEffect
        }
        if (isSaving) {
            Log.d(TAG, "Skip save: already saving")
            return@LaunchedEffect
        }
        if (eggCount == lastSavedCount) {
            Log.d(TAG, "Skip save: count unchanged (eggCount=$eggCount == lastSavedCount)")
            return@LaunchedEffect
        }
        if (sinceLastSave <= cooldownMs) {
            Log.d(TAG, "Skip save: cooldown active (${sinceLastSave}ms < ${cooldownMs}ms)")
            return@LaunchedEffect
        }

        val frame = overlayRef?.captureFrame()
        if (frame == null) {
            Log.w(TAG, "Skip save: captureFrame() returned null — overlay not ready?")
            return@LaunchedEffect
        }

        Log.i(TAG, "Starting save — eggCount=$eggCount frame=${frame.width}x${frame.height}")
        isSaving       = true
        lastSavedCount = eggCount
        lastSaveTimeMs = now

        scope.launch {
            val saved = repo.save(
                frame    = frame,
                eggCount = eggCount,
                metadata = mapOf("source" to "auto_detect")
            )
            if (saved != null) {
                Log.i(TAG, "Save succeeded — id=${saved.id} eventId=${saved.eventId}")
                snackMessage = "📸 Saved: $eggCount cluster(s) detected"
            } else {
                Log.e(TAG, "Save returned null — see DetectionRepository logs above for cause")
                snackMessage = "❌ Auto-save failed — check connection"
            }
            isSaving = false
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            if (hasCameraPermission) {

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory  = {
                        SnailDetectionOverlay(
                            lifecycleOwner    = lifecycleOwner,
                            context           = it,
                            detector          = detector,
                            onDetectionResult = { dets ->
                                eggCount      = dets.size
                                detectionText = if (dets.isEmpty()) "No eggs detected"
                                else "🥚 ${dets.size} cluster(s) detected"
                            }
                        ).also { ov -> overlayRef = ov }
                    },
                    update = { it.switchCamera(cameraFacing) }
                )

                // Status chip
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape           = RoundedCornerShape(24.dp),
                        color           = if (eggCount > 0) Color(0xCCE74C3C) else Color(0xCC000000),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text       = detectionText,
                                color      = Color.White,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isSaving) {
                                Spacer(modifier = Modifier.width(10.dp))
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(14.dp),
                                    color       = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                // Camera switch — top right
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick  = {
                            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK)
                                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Cameraswitch,
                            contentDescription = "Switch camera",
                            tint               = Color.White,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }

                // Title pill — top center
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text       = "🐌  Snail Egg Detector",
                            color      = Color.White,
                            fontSize   = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

            } else {

                Column(
                    modifier            = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = null,
                        tint               = Color(0xFF1AA3CC),
                        modifier           = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Camera permission required",
                        color      = Color.White,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Needed to scan for snail egg clusters in real time.",
                        color     = Color.Gray,
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF1AA3CC))
                    ) { Text("Grant permission", color = Color.White) }
                }
            }
        }
    }
}