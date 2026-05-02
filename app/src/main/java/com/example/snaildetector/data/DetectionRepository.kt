package com.example.snaildetector.data

import android.graphics.Bitmap
import android.util.Log
import com.example.snaildetector.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.days

class DetectionRepository {

    companion object {
        private const val TAG          = "DetectionRepo"
        private const val BUCKET       = "snail-detected"
        private const val JPEG_QUALITY = 80
    }

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun save(
        frame    : Bitmap,
        eggCount : Int,
        metadata : Map<String, String>? = null
    ): SnailDetection? {
        Log.i(TAG, "save() called — eggCount=$eggCount bitmap=${frame.width}x${frame.height} recycled=${frame.isRecycled}")
        return try {

            // ── Auth check ────────────────────────────────────────────────────
            val user = supabase.auth.currentUserOrNull()
            Log.d(TAG, "Auth state — user=${user?.id} email=${user?.email} role=${user?.role}")
            val uid = user?.id
            if (uid == null) {
                Log.e(TAG, "ABORT: user not logged in (currentUserOrNull returned null)")
                return null
            }

            val now          = Date()
            val dbTimestamp  = isoTimestampDb(now)
            val fileTimestamp = isoTimestampFile(now)

            val eventId      = "det-${fileTimestamp}-android-${shortId()}"
            val fileName     = "snail-detection-${fileTimestamp}.jpg"
            val filePath  = "${dateFolder(now)}/$eventId-$fileName"
            Log.d(TAG, "Identifiers — eventId=$eventId filePath=$filePath dbTimestamp=$dbTimestamp")

            // ── 1. Compress bitmap → JPEG bytes ───────────────────────────────
            val jpegBytes = compressBitmap(frame)
            Log.d(TAG, "STEP 1 — compressed ${frame.width}x${frame.height} → ${jpegBytes.size} bytes (quality=$JPEG_QUALITY)")

            // ── 2. Upload to Supabase Storage ─────────────────────────────────
            Log.d(TAG, "STEP 2 — uploading to bucket='$BUCKET' path='$filePath'")
            try {
                supabase.storage[BUCKET].upload(
                    path   = filePath,
                    data   = jpegBytes,
                    upsert = false,
                )
                Log.i(TAG, "STEP 2 OK — upload succeeded: $filePath")
            } catch (e: Exception) {
                Log.e(TAG, "STEP 2 FAIL — upload exception: ${e::class.qualifiedName}", e)
                Log.e(TAG, "  message  : ${e.message}")
                Log.e(TAG, "  cause    : ${e.cause?.message}")
                return null
            }

            // ── 3. Get a signed URL ───────────────────────────────────────────
            val photoUrl = createPhotoUrl(
                bucket = BUCKET,
                path = filePath,
                context = "STEP 3"
            )

            // ── 4. Insert DB row ──────────────────────────────────────────────
            val detection = SnailDetection(
                userId            = uid,
                eventId           = eventId,
                capturedAt        = dbTimestamp,
                eggClusterCount   = eggCount,
                platform          = "android",
                metadata          = metadata,
                bucket            = BUCKET,
                photoPath         = filePath,
                photoOriginalName = fileName,
                photoMimeType     = "image/jpeg",
                photoSize         = jpegBytes.size.toLong(),
                photoUrl          = photoUrl,
            )
            Log.d(TAG, "STEP 4 — inserting row: userId=$uid eventId=$eventId " +
                    "eggCount=$eggCount photoSize=${jpegBytes.size} photoUrl=${photoUrl.take(60)}")
            try {
                supabase.from("snaildetections").insert(detection)
                Log.i(TAG, "STEP 4 OK — row inserted for eventId=$eventId")
            } catch (e: Exception) {
                Log.e(TAG, "STEP 4 FAIL — DB insert exception: ${e::class.qualifiedName}", e)
                Log.e(TAG, "  message  : ${e.message}")
                Log.e(TAG, "  cause    : ${e.cause?.message}")
                return null
            }

            // ── 5. Re-fetch to get server-assigned id / timestamps ────────────
            Log.d(TAG, "STEP 5 — re-fetching row for eventId=$eventId")
            val saved = try {
                supabase.from("snaildetections")
                    .select { filter { eq("event_id", eventId) } }
                    .decodeSingle<SnailDetection>()
                    .also { Log.i(TAG, "STEP 5 OK — fetched id=${it.id} capturedAt=${it.capturedAt}") }
            } catch (e: Exception) {
                Log.w(TAG, "STEP 5 FAIL — re-fetch failed (row WAS saved, returning local copy): ${e.message}")
                detection
            }

            val hydrated = hydratePhotoUrl(saved, "STEP 5")

            Log.i(TAG, "save() complete — returning id=${hydrated.id}")
            hydrated

        } catch (e: Exception) {
            Log.e(TAG, "save() UNEXPECTED exception: ${e::class.qualifiedName}: ${e.message}", e)
            null
        }
    }

    // ── Fetch all detections for the current user ─────────────────────────────

    suspend fun getAll(): List<SnailDetection> {
        val uid = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User not logged in")
        Log.d(TAG, "getAll() for uid=$uid")
        val rows = supabase.from("snaildetections")
            .select {
                filter { eq("user_id", uid) }
                order("captured_at", Order.DESCENDING)
            }
            .decodeList<SnailDetection>()
            .also { Log.d(TAG, "getAll() returned ${it.size} rows") }

        val hydrated = ArrayList<SnailDetection>(rows.size)
        for (row in rows) {
            hydrated += hydratePhotoUrl(row, "getAll()")
        }
        return hydrated
    }

    // ── Delete a row ──────────────────────────────────────────────────────────

    suspend fun delete(id: String): Boolean {
        Log.d(TAG, "delete() id=$id")
        return try {
            supabase.from("snaildetections")
                .delete { filter { eq("id", id) } }
            Log.i(TAG, "delete() OK — id=$id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "delete() FAIL — id=$id: ${e.message}", e)
            false
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }

    private suspend fun hydratePhotoUrl(
        detection: SnailDetection,
        context: String
    ): SnailDetection {
        if (!detection.photoUrl.isNullOrBlank()) {
            return detection
        }

        val photoPath = detection.photoPath ?: return detection
        val bucket = detection.bucket.ifBlank { BUCKET }
        val photoUrl = createPhotoUrl(
            bucket = bucket,
            path = photoPath,
            context = "$context hydratePhotoUrl"
        )

        backfillPhotoUrl(detection, photoUrl, context)
        return detection.copy(photoUrl = photoUrl)
    }

    private suspend fun backfillPhotoUrl(
        detection: SnailDetection,
        photoUrl: String,
        context: String
    ) {
        if (!detection.photoUrl.isNullOrBlank()) {
            return
        }

        val recordId = detection.id ?: return
        try {
            supabase.from("snaildetections").update(
                {
                    set("photo_url", photoUrl)
                }
            ) {
                filter { eq("id", recordId) }
            }
            Log.d(TAG, "$context backfillPhotoUrl OK — id=$recordId")
        } catch (e: Exception) {
            Log.w(TAG, "$context backfillPhotoUrl FAIL — id=$recordId message=${e.message}")
        }
    }

    private fun createPhotoUrl(
        bucket: String,
        path: String,
        context: String
    ): String {
        val url = supabase.storage[bucket].publicUrl(path)
        Log.i(TAG, "$context OK — publicUrl=${url.take(80)}")
        return url
    }

    // Proper ISO 8601 with colons + UTC suffix — accepted by Postgres timestamptz
    private fun isoTimestampDb(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(date)

    // Hyphenated time — safe for use in file/folder names
    private fun isoTimestampFile(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).format(date)

    private fun dateFolder(date: Date): String =
        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(date)

    private fun shortId(): String =
        UUID.randomUUID().toString().replace("-", "").take(8)
}