package com.example.snaildetector.data

import android.graphics.Bitmap
import android.util.Log
import com.example.snaildetector.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.UploadData
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.days

class DetectionRepository {

    companion object {
        private const val TAG          = "DetectionRepository"
        private const val BUCKET       = "snail-detected"
        private val SIGNED_URL_TTL     = 365.days
        private const val JPEG_QUALITY = 80
    }

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun save(
        frame    : Bitmap,
        eggCount : Int,
        metadata : Map<String, String>? = null
    ): SnailDetection? {
        return try {

            // ── Auth check ────────────────────────────────────────────────────
            val uid = supabase.auth.currentUserOrNull()?.id
            if (uid == null) {
                Log.e(TAG, "Save failed: user not logged in")
                return null
            }

            val now       = Date()
            val timestamp = isoTimestamp(now)
            val eventId   = "det-${timestamp}-android-${shortId()}"
            val fileName  = "snail-detection-${timestamp}.jpg"
            val filePath  = "${dateFolder(now)}/$eventId-$fileName"

            // ── 1. Compress bitmap → JPEG bytes ───────────────────────────────
            val jpegBytes = compressBitmap(frame)
            Log.d(TAG, "Compressed frame: ${jpegBytes.size} bytes → $filePath")

            // ── 2. Upload to Supabase Storage ─────────────────────────────────
            // FIX: pass contentType so Supabase doesn't reject the upload.
            // Without it the SDK sends no Content-Type header and the storage
            // API returns a 415 / network-looking error on some versions.
            try {
                supabase.storage[BUCKET].upload(
                    path   = filePath,
                    data   = jpegBytes,
                    upsert = false,
                )
                Log.d(TAG, "Upload OK: $filePath")
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed — bucket='$BUCKET' path='$filePath' " +
                        "error=${e::class.simpleName}: ${e.message}", e)
                return null
            }

            // ── 3. Get a signed URL ───────────────────────────────────────────
            val signedUrl = try {
                supabase.storage[BUCKET].createSignedUrl(filePath, SIGNED_URL_TTL)
                    .also { Log.d(TAG, "Signed URL: $it") }
            } catch (e: Exception) {
                Log.e(TAG, "createSignedUrl failed: ${e.message}", e)
                // Non-fatal — save the row without a URL rather than aborting
                null
            }

            // ── 4. Insert the row ─────────────────────────────────────────────
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

            try {
                supabase.from("snaildetections").insert(detection)
                Log.d(TAG, "Row inserted for eventId=$eventId")
            } catch (e: Exception) {
                Log.e(TAG, "DB insert failed: ${e.message}", e)
                return null
            }

            // ── 5. Re-fetch to get server-assigned id / timestamps ────────────
            val saved = try {
                supabase.from("snaildetections")
                    .select { filter { eq("event_id", eventId) } }
                    .decodeSingle<SnailDetection>()
                    .also { Log.d(TAG, "Saved detection id=${it.id}") }
            } catch (e: Exception) {
                Log.e(TAG, "Re-fetch failed (row was saved): ${e.message}", e)
                // Return the local object rather than null — row was inserted
                detection
            }

            saved

        } catch (e: Exception) {
            // Catch-all — log the full stack trace so you can see the real error
            Log.e(TAG, "Unexpected error in save(): ${e::class.simpleName}: ${e.message}", e)
            null
        }
    }

    // ── Fetch all detections for the current user ─────────────────────────────

    suspend fun getAll(): List<SnailDetection> {
        val uid = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User not logged in")
        return supabase.from("snaildetections")
            .select {
                filter { eq("user_id", uid) }
                order("captured_at", Order.DESCENDING)
            }
            .decodeList<SnailDetection>()
    }

    // ── Delete a row (storage file is kept) ──────────────────────────────────

    suspend fun delete(id: String): Boolean {
        return try {
            supabase.from("snaildetections")
                .delete { filter { eq("id", id) } }
            Log.d(TAG, "Deleted detection id=$id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete detection $id: ${e.message}", e)
            false
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }

    // FIX: the original timestamp used colons in HH:mm:ss which are illegal
    // in file paths on some systems and cause Supabase Storage to reject the
    // upload with a path validation error (looks like a network error in logs).
    // Changed separator to hyphens: HH-mm-ss → safe on all platforms.
    private fun isoTimestamp(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).format(date)

    private fun dateFolder(date: Date): String =
        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(date)

    private fun shortId(): String =
        UUID.randomUUID().toString().replace("-", "").take(8)
}