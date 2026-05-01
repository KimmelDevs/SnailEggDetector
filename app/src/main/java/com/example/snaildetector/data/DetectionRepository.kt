package com.example.snaildetector.data

import android.graphics.Bitmap
import android.util.Log
import com.example.snaildetector.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.days

/**
 * Handles saving a snail detection event:
 *   1. Compress the frame bitmap → JPEG bytes
 *   2. Upload to Supabase Storage  (bucket: snail-detected)
 *   3. Get a signed URL valid for 1 year
 *   4. Insert a row into snaildetections
 *
 * Usage:
 *   val repo = DetectionRepository()
 *   repo.save(bitmap, eggCount, metadata)
 */
class DetectionRepository {

    companion object {
        private const val TAG           = "DetectionRepository"
        private const val BUCKET        = "snail-detected"
        private val SIGNED_URL_TTL = 365.days
        private const val JPEG_QUALITY  = 80
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Save a detection event. Call from a coroutine (suspend).
     *
     * @param frame      The camera frame bitmap at the moment of detection
     * @param eggCount   Number of egg clusters detected
     * @param metadata   Any extra key-value pairs you want to store (optional)
     * @return           The saved [SnailDetection] row, or null on failure
     */
    suspend fun save(
        frame    : Bitmap,
        eggCount : Int,
        metadata : Map<String, String>? = null
    ): SnailDetection? {
        return try {
            val uid = supabase.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("User not logged in")

            val now       = Date()
            val timestamp = isoTimestamp(now)
            val eventId   = "det-${timestamp}-android-${shortId()}"
            val fileName  = "snail-detection-${timestamp}.jpg"
            val filePath  = "${dateFolder(now)}/$eventId-$fileName"

            // 1 — Compress bitmap → JPEG bytes
            val jpegBytes = compressBitmap(frame)

            // 2 — Upload to Supabase Storage
            supabase.storage[BUCKET].upload(
                path        = filePath,
                data        = jpegBytes,
                upsert      = false
            )

            // 3 — Get a signed URL
            val signedUrl = supabase.storage[BUCKET]
                .createSignedUrl(filePath, SIGNED_URL_TTL)

            // 4 — Build and insert the row
            val detection = SnailDetection(
                userId            = uid,
                eventId           = eventId,
                capturedAt        = timestamp,
                eggClusterCount   = eggCount,
                platform          = "android",
                metadata          = metadata,
                bucket            = BUCKET,
                photoPath         = filePath,
                photoOriginalName = fileName,
                photoMimeType     = "image/jpeg",
                photoSize         = jpegBytes.size.toLong(),
                photoUrl          = signedUrl,
            )

            supabase.postgrest["snaildetections"].insert(detection)

            // Re-fetch to get server-assigned id / timestamps
            val saved = supabase.postgrest["snaildetections"]
                .select { filter { eq("event_id", eventId) } }
                .decodeSingle<SnailDetection>()

            Log.d(TAG, "Saved detection: ${saved.id}")
            saved

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save detection", e)
            null
        }
    }

    /**
     * Fetch all detections for the current user, newest first.
     */
    suspend fun getAll(): List<SnailDetection> {
        return try {
            supabase.postgrest["snaildetections"]
                .select {
                    order("captured_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<SnailDetection>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch detections", e)
            emptyList()
        }
    }

    /**
     * Delete a detection row (storage file is kept — add cleanup if needed).
     */
    suspend fun delete(id: String) {
        try {
            supabase.postgrest["snaildetections"]
                .delete { filter { eq("id", id) } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete detection $id", e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }

    private fun isoTimestamp(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'", Locale.US).format(date)

    /** yyyy/MM/dd folder structure in the bucket */
    private fun dateFolder(date: Date): String =
        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(date)

    /** 8-char random suffix for the event ID */
    private fun shortId(): String =
        UUID.randomUUID().toString().replace("-", "").take(8)
}