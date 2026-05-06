package com.example.snaildetector.detection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.widget.FrameLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@SuppressLint("ViewConstructor")
class SnailDetectionOverlay(
    private val lifecycleOwner : LifecycleOwner,
    context                    : Context,
    private val detector       : SnailDetector,
    val onDetectionResult      : (List<SnailDetector.Detection>) -> Unit = {}
) : FrameLayout(context) {

    companion object { private const val TAG = "SnailDetectionOverlay" }

    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private var isProcessing = false

    @Volatile private var latestFrame: Bitmap? = null

    fun captureFrame(): Bitmap? {
        val bmp = latestFrame ?: return null
        return bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false)
    }

    private val tracker = DetectionTracker(
        confirmFrames  = 1,
        maxMissFrames  = 2,
        smoothAlpha    = 0.6f,
        iouMatchThresh = 0.3f
    )

    private var imageRotationMatrix = Matrix()
    private var rotationInitialized = false

    private lateinit var previewView     : PreviewView
    private lateinit var boundingBoxView : BoundingBoxView

    @Volatile private var currentDetections: List<SnailDetector.Detection> = emptyList()

    init {
        attachViews()
    }

    // ── Camera setup ──────────────────────────────────────────────────────────

    private fun attachViews() {
        previewView     = PreviewView(context)
        boundingBoxView = BoundingBoxView(context)

        addView(previewView,     LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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

    @ExperimentalGetImage
    private val frameAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
        if (isProcessing) { imageProxy.close(); return@Analyzer }
        isProcessing = true

        // FIX: read overlay dimensions directly at frame time.
        // doOnLayout was a race — early frames saw 0x0 and produced zero-sized
        // boxes that the tracker never confirmed, so nothing ever appeared.
        val w = width.takeIf  { it > 0 } ?: imageProxy.width
        val h = height.takeIf { it > 0 } ?: imageProxy.height

        var frame = Bitmap.createBitmap(
            imageProxy.image!!.width,
            imageProxy.image!!.height,
            Bitmap.Config.ARGB_8888
        )
        frame.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

        if (!rotationInitialized) {
            imageRotationMatrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }
            rotationInitialized = true
        }
        frame = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, imageRotationMatrix, false)

        latestFrame = frame

        val isFront = (cameraFacing == CameraSelector.LENS_FACING_FRONT)

        // Capture an immutable snapshot before handing off to the coroutine.
        // Without this copy, the analyzer thread can advance to the next frame
        // and overwrite / recycle `frame` while TFLite is still reading its
        // pixel buffer — causing the SIGSEGV in libtensorflowlite_jni.so.
        val frameCopy = frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, false)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val rawDetections = detector.detect(frameCopy)

                val scaled = rawDetections.map {
                    detector.scaleToOverlay(it, w, h, mirrorX = isFront)
                }

                val stable = tracker.update(scaled)

                withContext(Dispatchers.Main) {
                    currentDetections = stable
                    onDetectionResult(stable)
                    boundingBoxView.invalidate()
                    isProcessing = false
                }
            } finally {
                // Release the per-inference copy now that TFLite is done with it.
                frameCopy.recycle()
            }
        }

        imageProxy.close()
    }

    // ── Bounding-box overlay ──────────────────────────────────────────────────

    inner class BoundingBoxView(context: Context) : android.view.View(context) {

        private val fillPaint = Paint().apply {
            color = Color.argb(60, 231, 76, 60)
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
            color          = Color.WHITE
            textSize       = 32f
            isFakeBoldText = true
            typeface       = android.graphics.Typeface.DEFAULT_BOLD
        }
        private val confPaint = Paint().apply {
            color    = Color.WHITE
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        init {
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            currentDetections.forEach { det ->
                val box = det.bbox

                canvas.drawRoundRect(box, 16f, 16f, fillPaint)
                canvas.drawRoundRect(box, 16f, 16f, strokePaint)

                val text   = "${det.label.uppercase()} ${(det.confidence * 100).toInt()}%"
                val bounds = android.graphics.Rect()
                labelPaint.getTextBounds(text, 0, text.length, bounds)

                val labelX = box.left
                var labelY = box.top - 10f

                if (labelY - bounds.height() - 8f < 0f) {
                    labelY = box.bottom + bounds.height() + 10f
                }

                canvas.drawRect(
                    labelX - 6f,
                    labelY - bounds.height() - 8f,
                    labelX + bounds.width() + 12f,
                    labelY + 6f,
                    labelBgPaint
                )
                canvas.drawText(text, labelX, labelY, labelPaint)

                val confText = "${(det.confidence * 100).toInt()}%"
                canvas.drawText(confText, box.right - 56f, box.bottom - 8f, confPaint)
            }
        }
    }
}