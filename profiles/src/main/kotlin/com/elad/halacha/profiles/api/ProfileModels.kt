package com.elad.halacha.profiles.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// ---- Profile JSON model (shared by engine + demo) ----

data class Profile(
    val version: Int,
    val key: String,
    val displayName: String,
    val labels: Labels? = null,
    val times: List<ProfileTime>
)

data class Labels(
    val en: String? = null,
    val he: String? = null
)

data class ProfileTime(
    val id: String,
    val label: Labels? = null,
    val target: Target
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Target(
    val kind: String,                        // "EXTERNAL_NAME" | "INTERNAL"
    val externalMethod: String? = null,      // when kind = EXTERNAL_NAME
    val internalMethodId: String? = null,    // when kind = INTERNAL
    val params: Map<String, Any?>? = null
)

// ---- Validation DTOs (engine-local; mirrors REST’s semantics) ----

data class ValidationResponse(
    val valid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList()
)

data class ValidationError(
    val path: String,
    val code: String,
    val message: String
)

data class ValidationWarning(
    val path: String,
    val code: String,
    val message: String
)

// ---- Compute response DTOs for in-process calls ----

data class MinimalProfileInfo(
    val key: String,
    val displayName: String,
    val labels: Labels? = null
)

data class GeoInput(
    val lat: Double,
    val lon: Double,
    val elev: Double,
    val tz: String
)

data class ProfileComputeInput(
    val dateIso: String,   // yyyy-MM-dd
    val geo: GeoInput
)

data class Resolution(
    val kind: String,                      // "EXTERNAL_NAME" | "INTERNAL"
    val externalMethod: String? = null,    // when kind = EXTERNAL_NAME
    val internalMethodId: String? = null,  // when kind = INTERNAL
    val owner: String? = null,             // class/provider of external method or "INTERNAL"
    val status: String? = null             // e.g., "ok" | "unresolved" | "missing_id" | "error"
)

data class ProfileComputeItem(
    val id: String,
    val label: Labels? = null,
    val resolution: Resolution,
    val utc: String? = null,
    val local: String? = null,
    val instant: String? = null
)

data class ProfileComputeResponse(
    val profile: MinimalProfileInfo,
    val input: ProfileComputeInput,
    val results: List<ProfileComputeItem>,
    val warnings: List<ValidationWarning> = emptyList()
)