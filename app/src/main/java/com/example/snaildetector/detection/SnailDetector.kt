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
 * Export from the Colab notebook with:
 *   model.export(format="tflite", imgsz=640)
 *
 * Input  : 1 × IMG_SIZE × IMG_SIZE × 3  (float32, 0..1 normalised)
 * Output : 1 × NUM_BOXES × 6  — each row is [x1, y1, x2, y2, label, conf]
 *          coordinates are in pixel space (0..IMG_SIZE)
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

        // Output layout: [1, numBoxes, 6]
        // Each box = [x1, y1, x2, y2, label, conf]  (coords in 0..IMG_SIZE pixels)
        val outputShape = interp.getOutputTensor(0).shape()  // e.g. [1, 8400, 6]
        val numBoxes    = outputShape[1]

        val rawOutput = Array(1) { Array(numBoxes) { FloatArray(6) } }
        interp.run(inputBuf, rawOutput)

        return parseOutput(rawOutput[0])
    }

    // ── Output parsing ────────────────────────────────────────────────────────

    /**
     * Each row: [x1, y1, x2, y2, label, conf]
     * x1/y1/x2/y2 are already in pixel coords (0..IMG_SIZE) — no conversion needed.
     */
    private fun parseOutput(output: Array<FloatArray>): List<Detection> {
        val candidates = mutableListOf<Detection>()

        for (row in output) {
            val x1   = row[0]
            val y1   = row[1]
            val x2   = row[2]
            val y2   = row[3]
            val cls  = row[4].toInt()
            val conf = row[5]

            if (conf < CONF_THRESH) continue

            candidates.add(
                Detection(
                    bbox       = RectF(x1, y1, x2, y2),
                    confidence = conf,
                    classId    = cls,
                    label      = CLASS_NAMES.getOrElse(cls) { "class_$cls" }
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
