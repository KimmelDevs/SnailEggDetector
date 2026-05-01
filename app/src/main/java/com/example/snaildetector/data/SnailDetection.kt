package com.example.snaildetector.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SnailDetection(
    val id                : String?  = null,
    @SerialName("user_id")
    val userId            : String?  = null,
    @SerialName("event_id")
    val eventId           : String,
    @SerialName("captured_at")
    val capturedAt        : String?  = null,
    @SerialName("egg_cluster_count")
    val eggClusterCount   : Int      = 0,
    val platform          : String   = "android",
    val metadata          : Map<String, String>? = null,
    val bucket            : String   = "snail-detected",
    @SerialName("photo_path")
    val photoPath         : String?  = null,
    @SerialName("photo_original_name")
    val photoOriginalName : String?  = null,
    @SerialName("photo_mime_type")
    val photoMimeType     : String?  = null,
    @SerialName("photo_size")
    val photoSize         : Long?    = null,
    @SerialName("photo_url")
    val photoUrl          : String?  = null,
    @SerialName("created_at")
    val createdAt         : String?  = null,
    @SerialName("updated_at")
    val updatedAt         : String?  = null,
)