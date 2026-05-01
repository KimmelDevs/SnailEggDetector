package com.example.snaildetector.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * YOLOv8 TFLite inference wrapper.
 *
 * Drop your exported model at:  app/src/main/assets/best.tflite
 *
 * Export from the pipeline with:
 *   model.export(format="tflite", imgsz=640, half=True)
 *
 * ── Tensor shapes ────────────────────────────────────────────────────────────
 * Input  : [1, 640, 640, 3]   float32, values normalised to 0..1
 *
 * Output : [1, 5, 8400]       YOLOv8 TFLite native layout
 *           │   │   └─ anchor boxes  (8400 = 3 scales × 80×80 + 40×40 + 20×20)
 *           │   └───── channels      (4 bbox values + num_classes)
 *           └───────── batch size    (always 1 at inference)
 *
 *   output[0][i] = cx   (normalised, 0..1)
 *   output[1][i] = cy   (normalised, 0..1)
 *   output[2][i] = w    (normalised, 0..1)
 *   output[3][i] = h    (normalised, 0..1)
 *   output[4][i] = class-0 confidence score   ← for single-class models
 *   output[5][i] = class-1 confidence score   ← present only if nc > 1, etc.
 *
 * The original code assumed [1, numBoxes, 6] (transposed + wrong field count),
 * which caused the fatal shape-mismatch crash.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class SnailDetector(context: Context) {

    companion object {
        private const val TAG        = "SnailDetector"
        private const val MODEL_FILE = "best.tflite"
        const val  IMG_SIZE          = 640
        const val  CONF_THRESH       = 0.25f
        const val  IOU_THRESH        = 0.45f
        val CLASS_NAMES              = listOf("fushouluo")
    }

    data class Detection(
        val bbox       : RectF,   // pixel space (0..IMG_SIZE)
        val confidence : Float,
        val classId    : Int,
        val label      : String
    )

    private val interpreter: Interpreter? by lazy {
        try {
            val model = FileUtil.loadMappedFile(context, MODEL_FILE)
            Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $MODEL_FILE — did you add it to assets?", e)
            null
        }
    }

    // ── Pre-processing ────────────────────────────────────────────────────────

    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true)
        val buf = ByteBuffer
            .allocateDirect(1 * IMG_SIZE * IMG_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(IMG_SIZE * IMG_SIZE)
        scaled.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE)
        for (px in pixels) {
            buf.putFloat(((px shr 16) and 0xFF) / 255f)  // R
            buf.putFloat(((px shr  8) and 0xFF) / 255f)  // G
            buf.putFloat(( px         and 0xFF) / 255f)  // B
        }
        buf.rewind()
        scaled.recycle()
        return buf
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    fun detect(bitmap: Bitmap): List<Detection> {
        val interp = interpreter ?: return emptyList()

        val inputBuf = bitmapToInputBuffer(bitmap)

        // Read the actual output tensor shape at runtime so this code remains
        // correct even if the model is re-exported with a different image size
        // or number of classes.
        //
        // YOLOv8 TFLite export always produces: [1, numChannels, numAnchors]
        //   numChannels = 4  (bbox)  + num_classes
        //   numAnchors  = 8400 for imgsz=640
        val shape       = interp.getOutputTensor(0).shape()  // e.g. [1, 5, 8400]
        val numChannels = shape[1]                            // 5
        val numAnchors  = shape[2]                            // 8400

        // Allocate output buffer to exactly match the model's output tensor.
        val rawOutput = Array(1) { Array(numChannels) { FloatArray(numAnchors) } }
        interp.run(inputBuf, rawOutput)

        return parseOutput(rawOutput[0], numAnchors, numChannels)
    }

    // ── Output parsing ────────────────────────────────────────────────────────

    /**
     * Iterates over every anchor and converts the raw YOLOv8 output to a
     * list of Detection objects in pixel space (0..IMG_SIZE).
     *
     * Layout of [output]:
     *   output[0..3][anchorIdx]  = cx, cy, w, h  (normalised 0..1)
     *   output[4+c][anchorIdx]   = confidence score for class c
     */
    private fun parseOutput(
        output      : Array<FloatArray>,
        numAnchors  : Int,
        numChannels : Int
    ): List<Detection> {

        val numClasses = numChannels - 4          // e.g. 5 - 4 = 1
        val candidates = mutableListOf<Detection>()

        for (i in 0 until numAnchors) {
            // Bounding box — normalised centre + size
            val cx = output[0][i]
            val cy = output[1][i]
            val w  = output[2][i]
            val h  = output[3][i]

            // Find the highest-scoring class
            var bestClass = 0
            var bestScore = output[4][i]
            for (c in 1 until numClasses) {
                val score = output[4 + c][i]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }

            if (bestScore < CONF_THRESH) continue

            // Convert from normalised cx,cy,w,h → absolute pixel x1,y1,x2,y2
            val x1 = (cx - w / 2f) * IMG_SIZE
            val y1 = (cy - h / 2f) * IMG_SIZE
            val x2 = (cx + w / 2f) * IMG_SIZE
            val y2 = (cy + h / 2f) * IMG_SIZE

            candidates.add(
                Detection(
                    bbox       = RectF(x1, y1, x2, y2),
                    confidence = bestScore,
                    classId    = bestClass,
                    label      = CLASS_NAMES.getOrElse(bestClass) { "class_$bestClass" }
                )
            )
        }

        return nms(candidates)
    }

    // ── NMS ───────────────────────────────────────────────────────────────────

    private fun nms(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept   = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)
            sorted.removeAll { iou(best.bbox, it.bbox) > IOU_THRESH }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft   = maxOf(a.left,   b.left)
        val interTop    = maxOf(a.top,    b.top)
        val interRight  = minOf(a.right,  b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        val interArea = maxOf(0f, interRight - interLeft) *
                maxOf(0f, interBottom - interTop)
        if (interArea == 0f) return 0f

        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        return interArea / (aArea + bArea - interArea)
    }

    // ── Coordinate mapping ────────────────────────────────────────────────────

    /**
     * Scales a Detection from model pixel space (0..IMG_SIZE) to the
     * on-screen overlay dimensions, optionally mirroring on X for the
     * front camera.
     */
    fun scaleToOverlay(
        det      : Detection,
        overlayW : Int,
        overlayH : Int,
        mirrorX  : Boolean = false
    ): Detection {
        val scaleX = overlayW / IMG_SIZE.toFloat()
        val scaleY = overlayH / IMG_SIZE.toFloat()

        var left  = det.bbox.left   * scaleX
        var right = det.bbox.right  * scaleX
        val top   = det.bbox.top    * scaleY
        val bot   = det.bbox.bottom * scaleY

        if (mirrorX) {
            left  = overlayW - det.bbox.right * scaleX
            right = overlayW - det.bbox.left  * scaleX
        }

        return det.copy(bbox = RectF(left, top, right, bot))
    }

    fun close() { interpreter?.close() }
}