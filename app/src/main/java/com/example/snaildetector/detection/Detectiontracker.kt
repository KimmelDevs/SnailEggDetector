package com.example.snaildetector.detection

import android.graphics.RectF

/**
 * Temporal smoothing tracker for YOLOv8 detections.
 *
 * Fixes vs original:
 *  1. everConfirmed flag — hitStreak resets to 0 on miss so the hold-on
 *     branch could never fire; everConfirmed survives the reset.
 *  2. confirmFrames lowered to 1 so the very first good frame shows a box
 *     immediately (raise back to 2-3 if you get false positives).
 */
class DetectionTracker(
    private val confirmFrames  : Int   = 1,
    private val maxMissFrames  : Int   = 5,
    private val smoothAlpha    : Float = 0.4f,
    private val iouMatchThresh : Float = 0.3f
) {

    private data class TrackedBox(
        var left          : Float,
        var top           : Float,
        var right         : Float,
        var bottom        : Float,
        var confidence    : Float,
        val classId       : Int,
        val label         : String,
        var hitStreak     : Int     = 1,
        var missStreak    : Int     = 0,
        // Survives hitStreak reset so hold-on phase works correctly
        var everConfirmed : Boolean = false
    ) {
        fun toDetection() = SnailDetector.Detection(
            bbox       = RectF(left, top, right, bottom),
            confidence = confidence,
            classId    = classId,
            label      = label
        )
    }

    private val tracked = mutableListOf<TrackedBox>()

    fun update(detections: List<SnailDetector.Detection>): List<SnailDetector.Detection> {

        val matched  = mutableSetOf<Int>()
        val newBoxes = mutableListOf<SnailDetector.Detection>()

        // Step 1: match incoming detections to tracked boxes
        for (det in detections) {
            val bestIdx = bestMatch(det.bbox)
            if (bestIdx >= 0) {
                matched.add(bestIdx)
                val t = tracked[bestIdx]
                t.left       = ema(t.left,       det.bbox.left,   smoothAlpha)
                t.top        = ema(t.top,         det.bbox.top,    smoothAlpha)
                t.right      = ema(t.right,       det.bbox.right,  smoothAlpha)
                t.bottom     = ema(t.bottom,      det.bbox.bottom, smoothAlpha)
                t.confidence = ema(t.confidence,  det.confidence,  smoothAlpha)
                t.hitStreak++
                t.missStreak = 0
                if (t.hitStreak >= confirmFrames) t.everConfirmed = true
            } else {
                newBoxes.add(det)
            }
        }

        // Step 2: increment miss counter for unmatched boxes
        for ((idx, t) in tracked.withIndex()) {
            if (idx !in matched) {
                t.missStreak++
                t.hitStreak = 0
                // NOTE: do NOT reset everConfirmed — it is permanent
            }
        }

        // Step 3: promote new detections
        for (det in newBoxes) {
            val box = TrackedBox(
                left       = det.bbox.left,
                top        = det.bbox.top,
                right      = det.bbox.right,
                bottom     = det.bbox.bottom,
                confidence = det.confidence,
                classId    = det.classId,
                label      = det.label
            )
            // If confirmFrames == 1, mark confirmed immediately on creation
            if (confirmFrames <= 1) box.everConfirmed = true
            tracked.add(box)
        }

        // Step 4: remove boxes missing too long
        tracked.removeAll { it.missStreak > maxMissFrames }

        // Step 5: show confirmed boxes + hold-on boxes
        // FIX: use everConfirmed instead of hitStreak for the hold-on check,
        // because hitStreak is reset to 0 when a box is missed.
        return tracked
            .filter { it.hitStreak >= confirmFrames || (it.everConfirmed && it.missStreak in 1..maxMissFrames) }
            .map { it.toDetection() }
    }

    fun reset() { tracked.clear() }

    private fun bestMatch(bbox: RectF): Int {
        var bestIdx   = -1
        var bestScore = -1f

        val bw = bbox.width()
        val bh = bbox.height()
        // Allow matching if center moves up to 1.5x the box diagonal between frames
        val maxDist = 1.5f * Math.sqrt((bw * bw + bh * bh).toDouble()).toFloat()

        for ((i, t) in tracked.withIndex()) {
            if (t.missStreak > maxMissFrames) continue

            val tRect = RectF(t.left, t.top, t.right, t.bottom)

            // Primary: IoU overlap
            val iouScore = iou(bbox, tRect)
            if (iouScore >= iouMatchThresh) {
                if (iouScore > bestScore) { bestScore = iouScore; bestIdx = i }
                continue
            }

            // Fallback: center-distance (catches camera-pan where boxes don't overlap)
            val dist = centerDist(bbox, tRect)
            if (dist < maxDist) {
                // Normalise to 0..1 so it competes fairly with iou scores
                val distScore = 1f - (dist / maxDist)
                if (distScore > bestScore) { bestScore = distScore; bestIdx = i }
            }
        }
        return bestIdx
    }

    private fun centerDist(a: RectF, b: RectF): Float {
        val dx = (a.left + a.right) / 2f - (b.left + b.right) / 2f
        val dy = (a.top + a.bottom) / 2f - (b.top + b.bottom) / 2f
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun ema(old: Float, new: Float, alpha: Float) = alpha * new + (1f - alpha) * old

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft   = maxOf(a.left,   b.left)
        val interTop    = maxOf(a.top,    b.top)
        val interRight  = minOf(a.right,  b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        val interArea   = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        if (interArea == 0f) return 0f
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        return interArea / (aArea + bArea - interArea)
    }
}