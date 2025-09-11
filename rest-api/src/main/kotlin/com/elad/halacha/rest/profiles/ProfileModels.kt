package com.elad.halacha.rest.profiles

data class Profile(
    val version: Int,
    val key: String,
    val displayName: String? = null,
    val labels: Labels? = null,
    val times: List<ProfileTime>
)

data class Labels(
    val he: String? = null,
    val en: String? = null
)

data class ProfileTime(
    val id: String,
    val alias: String? = null,      // purely informative / UI hint
    val label: Labels? = null,      // at least one of he/en recommended
    val target: Target
)

data class Target(
    val kind: String,               // "EXTERNAL_NAME" | "INTERNAL"
    val externalMethod: String? = null,
    val internalMethodId: String? = null
)

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

data class ProfileComputeResponse(
    val profile: MinimalProfileInfo,
    val input: ProfileComputeInput,
    val results: List<ProfileComputeItem>,
    val warnings: List<ValidationWarning> = emptyList()
)

data class MinimalProfileInfo(
    val key: String,
    val displayName: String? = null
)

data class ProfileComputeInput(
    val dateIso: String,
    val geo: GeoInput
)

data class GeoInput(
    val lat: Double,
    val lon: Double,
    val elevationMeters: Double,
    val tz: String
)

data class ProfileComputeItem(
    val id: String,
    val label: Labels? = null,
    val resolution: Resolution,
    val utc: String?,
    val local: String?,
    val instant: String?
)

data class Resolution(
    val kind: String,                 // "EXTERNAL_NAME" | "INTERNAL"
    val externalMethod: String? = null,
    val internalMethodId: String? = null,
    val owner: String? = null,        // e.g., ZmanimCalendar / AstronomicalCalendar / ComplexZmanimCalendar / INTERNAL
    val status: String? = null        // e.g., "unresolved" for INTERNAL not implemented
)
