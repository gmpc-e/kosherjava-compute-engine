package com.elad.halacha.engine.model

import java.time.Instant

data class ComputeRequest(
    val method: ComputeMethod,
    val dateIso: String,     // yyyy-MM-dd
    val lat: Double,
    val lon: Double,
    val elevationMeters: Double? = 0.0,
    val tz: String           // e.g., "Asia/Jerusalem"
)

enum class ComputeMethod {
    SUNRISE, SUNSET, SEA_LEVEL_SUNSET
}

data class ComputeResult(
    val method: ComputeMethod,
    val input: ComputeRequest,
    val utc: String?,        // ISO-8601 or null if not computable
    val local: String?,      // ISO-8601 in requested tz
    val instant: Instant?    // raw for debugging
)