package com.example.snaildetector.detection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Drop-in View that:
 *  1. Shows a live CameraX preview
 *  2. Runs SnailDetector on every frame (background thread)
 *  3. Draws bounding boxes on a transparent SurfaceView overlay
 *
 * Usage in Compose:
 *   AndroidView(
 *       modifier = Modifier.fillMaxSize(),
 *       factory  = { SnailDetectionOverlay(lifecycleOwner, it, detector) },
 *       update   = { it.switchCamera(facing) }
 *   )
 */
@ExperimentalGetImage
@SuppressLint("ViewConstructor")
class SnailDetectionOverlay(
    private val lifecycleOwner : LifecycleOwner,
    context                    : Context,
    private val detector       : SnailDetector,
    /** Called on the main thread after each frame is processed. */
    val onDetectionResult      : (List<SnailDetector.Detection>) -> Unit = {}
) : FrameLayout(context) {

    companion object { private const val TAG = "SnailDetectionOverlay" }

    private var overlayW = 0
    private var overlayH = 0

    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private var isProcessing = false

    // Temporal smoothing — stabilises the count across frames
    private val tracker = DetectionTracker(
        confirmFrames  = 2,    // must be seen 2 frames in a row before showing
        maxMissFrames  = 5,    // keep showing for 5 frames after it disappears
        smoothAlpha    = 0.4f, // EMA weight: higher = snappier but jitterier
        iouMatchThresh = 0.3f  // IoU needed to link a new box to a tracked one
    )

    // Matrix to rotate the raw camera frame to upright orientation
    private var imageRotationMatrix = Matrix()
    private var rotationInitialized = false

    private lateinit var previewView      : PreviewView
    private lateinit var boundingBoxView  : BoundingBoxView

    // Latest scaled detections — read by BoundingBoxView.onDraw()
    @Volatile private var currentDetections: List<SnailDetector.Detection> = emptyList()

    init {
        doOnLayout {
            overlayW = measuredWidth
            overlayH = measuredHeight
        }
        attachViews()
    }

    // ── Camera setup ──────────────────────────────────────────────────────────

    private fun attachViews() {
        previewView     = PreviewView(context)
        boundingBoxView = BoundingBoxView(context)

        addView(previewView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(boundingBoxView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        startCamera(cameraFacing)
    }

    fun switchCamera(facing: Int) {
        if (facing == cameraFacing) return
        cameraFacing        = facing
        rotationInitialized = false
        tracker.reset()
        removeView(previewView)
        removeView(boundingBoxView)
        attachViews()
    }

    private fun startCamera(facing: Int) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyzer) }

            val selector = CameraSelector.Builder().requireLensFacing(facing).build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ── Frame analyser ────────────────────────────────────────────────────────

    @androidx.camera.core.ExperimentalGetImage
    private val frameAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
        if (isProcessing) { imageProxy.close(); return@Analyzer }
        isProcessing = true

        // Convert RGBA_8888 ImageProxy → Bitmap
        var frame = Bitmap.createBitmap(
            imageProxy.image!!.width,
            imageProxy.image!!.height,
            Bitmap.Config.ARGB_8888
        )
        frame.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

        // Rotate to upright
        if (!rotationInitialized) {
            imageRotationMatrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }
            rotationInitialized = true
        }
        frame = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, imageRotationMatrix, false)

        val isFront = (cameraFacing == CameraSelector.LENS_FACING_FRONT)

        CoroutineScope(Dispatchers.Default).launch {
            // Run YOLOv8 inference
            val rawDetections = detector.detect(frame)

            // Scale from model space → overlay screen space
            val scaled = rawDetections.map {
                detector.scaleToOverlay(it, overlayW, overlayH, mirrorX = isFront)
            }

            // Stabilise across frames — prevents count flickering
            val stable = tracker.update(scaled)

            withContext(Dispatchers.Main) {
                currentDetections = stable
                onDetectionResult(stable)
                boundingBoxView.invalidate()
                isProcessing = false
            }
        }

        imageProxy.close()
    }

    // ── Bounding-box overlay ──────────────────────────────────────────────────

    inner class BoundingBoxView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

        // Box styles — red fill + red stroke, matching the notebook's #e74c3c
        private val fillPaint = Paint().apply {
            color = Color.argb(60, 231, 76, 60)   // #e74c3c at ~24% opacity
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint().apply {
            color       = Color.parseColor("#e74c3c")
            style       = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val labelBgPaint = Paint().apply {
            color = Color.parseColor("#DD000000")
            style = Paint.Style.FILL
        }
        private val labelPaint = Paint().apply {
            color       = Color.WHITE
            textSize    = 32f
            isFakeBoldText = true
            typeface    = android.graphics.Typeface.DEFAULT_BOLD
        }
        private val confPaint = Paint().apply {
            color    = Color.WHITE
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        init {
            holder.addCallback(this)
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)
            setWillNotDraw(false)
        }

        override fun surfaceCreated(h: SurfaceHolder)                              {}
        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {}
        override fun surfaceDestroyed(h: SurfaceHolder)                            {}

        override fun onDraw(canvas: Canvas) {
            currentDetections.forEach { det ->
                val box = det.bbox

                // Filled rounded rect + border
                canvas.drawRoundRect(box, 16f, 16f, fillPaint)
                canvas.drawRoundRect(box, 16f, 16f, strokePaint)

                // Label: "fushouluo 87%"
                val text   = "${det.label.uppercase()} ${(det.confidence * 100).toInt()}%"
                val bounds = android.graphics.Rect()
                labelPaint.getTextBounds(text, 0, text.length, bounds)

                val labelX = box.left
                var labelY = box.top - 10f

                // Flip label below box if it would go off-screen
                if (labelY - bounds.height() - 8f < 0f) {
                    labelY = box.bottom + bounds.height() + 10f
                }

                // Dark background pill behind label
                canvas.drawRect(
                    labelX - 6f,
                    labelY - bounds.height() - 8f,
                    labelX + bounds.width() + 12f,
                    labelY + 6f,
                    labelBgPaint
                )
                canvas.drawText(text, labelX, labelY, labelPaint)

                // Confidence readout inside bottom-right of box
                val confText = "${(det.confidence * 100).toInt()}%"
                canvas.drawText(confText, box.right - 56f, box.bottom - 8f, confPaint)
            }
        }
    }
}