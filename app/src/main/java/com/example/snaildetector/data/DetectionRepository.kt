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
        private val SIGNED_URL_TTL     = 365.days
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
            Log.d(TAG, "STEP 3 — creating signed URL (ttl=$SIGNED_URL_TTL)")
            val signedUrl = try {
                supabase.storage[BUCKET].createSignedUrl(filePath, SIGNED_URL_TTL)
                    .also { Log.i(TAG, "STEP 3 OK — signedUrl=$it") }
            } catch (e: Exception) {
                Log.w(TAG, "STEP 3 FAIL — createSignedUrl exception (non-fatal, continuing): ${e.message}")
                null
            }

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
                photoUrl          = signedUrl,
            )
            Log.d(TAG, "STEP 4 — inserting row: userId=$uid eventId=$eventId " +
                    "eggCount=$eggCount photoSize=${jpegBytes.size} photoUrl=${signedUrl?.take(60)}")
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

            Log.i(TAG, "save() complete — returning id=${saved.id}")
            saved

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
        return supabase.from("snaildetections")
            .select {
                filter { eq("user_id", uid) }
                order("captured_at", Order.DESCENDING)
            }
            .decodeList<SnailDetection>()
            .also { Log.d(TAG, "getAll() returned ${it.size} rows") }
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