package com.example.snaildetector.detection

import android.graphics.RectF

/**
 * Temporal smoothing tracker for YOLOv8 detections.
 *
 * Problem it solves
 * ─────────────────
 * YOLOv8 NMS scores fluctuate slightly frame-to-frame, so eggs near the
 * confidence threshold appear and disappear — the count jumps around even
 * though all eggs are physically still in the frame.
 *
 * How it works
 * ────────────
 * Every incoming raw detection is matched to an existing tracked box via IoU.
 * Each tracked box has two counters:
 *   • hitStreak  — how many consecutive frames it has been matched
 *   • missStreak — how many consecutive frames it has NOT been matched
 *
 * A box is only shown to the UI once hitStreak >= CONFIRM_FRAMES (debounce).
 * A box keeps being shown until missStreak > MAX_MISS_FRAMES (hold-on).
 * While a box is alive, its position and confidence are smoothed with EMA
 * so the rectangle doesn't jitter.
 *
 * Tuning
 * ──────
 * CONFIRM_FRAMES   raise → less false positives, slower to appear
 * MAX_MISS_FRAMES  raise → boxes "stick" longer when briefly missed
 * SMOOTH_ALPHA     raise → snappier but jitterier position tracking
 * IOU_MATCH_THRESH raise → stricter box-matching (use lower if eggs are small)
 */
class DetectionTracker(
    private val confirmFrames  : Int   = 2,    // frames seen before showing
    private val maxMissFrames  : Int   = 5,    // frames missed before hiding
    private val smoothAlpha    : Float = 0.4f, // EMA weight for position/conf
    private val iouMatchThresh : Float = 0.3f  // min IoU to link new→tracked box
) {

    // ── Internal tracked state ────────────────────────────────────────────────

    private data class TrackedBox(
        // Smoothed position (pixel space, same coords as SnailDetector output)
        var left       : Float,
        var top        : Float,
        var right      : Float,
        var bottom     : Float,
        var confidence : Float,
        val classId    : Int,
        val label      : String,

        var hitStreak  : Int = 1,
        var missStreak : Int = 0
    ) {
        fun toDetection() = SnailDetector.Detection(
            bbox       = RectF(left, top, right, bottom),
            confidence = confidence,
            classId    = classId,
            label      = label
        )
    }

    private val tracked = mutableListOf<TrackedBox>()
    private var nextId  = 0   // unused beyond debug logging if you need it

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Feed the raw per-frame detections (already in overlay pixel space).
     * Returns the stabilised list that should actually be drawn.
     */
    fun update(detections: List<SnailDetector.Detection>): List<SnailDetector.Detection> {

        // ── Step 1: match incoming detections to tracked boxes ────────────────
        val matched   = mutableSetOf<Int>()   // indices into [tracked]
        val newBoxes  = mutableListOf<SnailDetector.Detection>()

        for (det in detections) {
            val bestIdx = bestMatch(det.bbox)

            if (bestIdx >= 0) {
                matched.add(bestIdx)
                val t = tracked[bestIdx]

                // EMA-smooth the position and confidence
                t.left       = ema(t.left,       det.bbox.left,   smoothAlpha)
                t.top        = ema(t.top,         det.bbox.top,    smoothAlpha)
                t.right      = ema(t.right,       det.bbox.right,  smoothAlpha)
                t.bottom     = ema(t.bottom,      det.bbox.bottom, smoothAlpha)
                t.confidence = ema(t.confidence,  det.confidence,  smoothAlpha)

                t.hitStreak++
                t.missStreak = 0
            } else {
                newBoxes.add(det)
            }
        }

        // ── Step 2: increment miss counter for unmatched tracked boxes ─────────
        for ((idx, t) in tracked.withIndex()) {
            if (idx !in matched) {
                t.missStreak++
                t.hitStreak = 0
            }
        }

        // ── Step 3: promote new detections to tracked ─────────────────────────
        for (det in newBoxes) {
            tracked.add(
                TrackedBox(
                    left       = det.bbox.left,
                    top        = det.bbox.top,
                    right      = det.bbox.right,
                    bottom     = det.bbox.bottom,
                    confidence = det.confidence,
                    classId    = det.classId,
                    label      = det.label
                )
            )
        }

        // ── Step 4: remove boxes that have been missing too long ──────────────
        tracked.removeAll { it.missStreak > maxMissFrames }

        // ── Step 5: return only confirmed boxes ───────────────────────────────
        // A box is confirmed once it has been seen CONFIRM_FRAMES times in a row,
        // OR if it is currently missing but was confirmed before (hold-on phase).
        return tracked
            .filter { it.hitStreak >= confirmFrames || it.missStreak in 1..maxMissFrames && it.hitStreak >= confirmFrames }
            .map { it.toDetection() }
    }

    /** Reset all state (call when camera switches or Activity restarts). */
    fun reset() {
        tracked.clear()
        nextId = 0
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns index of the tracked box with highest IoU above threshold, or -1. */
    private fun bestMatch(bbox: RectF): Int {
        var bestIdx   = -1
        var bestScore = iouMatchThresh

        for ((i, t) in tracked.withIndex()) {
            if (t.missStreak > maxMissFrames) continue
            val score = iou(bbox, RectF(t.left, t.top, t.right, t.bottom))
            if (score > bestScore) {
                bestScore = score
                bestIdx   = i
            }
        }
        return bestIdx
    }

    private fun ema(old: Float, new: Float, alpha: Float) =
        alpha * new + (1f - alpha) * old

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
}