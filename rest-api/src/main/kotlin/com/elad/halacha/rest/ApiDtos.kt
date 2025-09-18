package com.elad.halacha.rest

import com.elad.halacha.rest.profiles.Labels
import com.elad.halacha.rest.profiles.ValidationWarning

/**
 * Resolution metadata for a computed item.
 * Module.kt uses different fields depending on kind:
 *  - EXTERNAL_NAME: kind, externalMethod, owner
 *  - INTERNAL: kind, internalMethodId, owner, status
 */
data class Resolution(
    val kind: String,
    val externalMethod: String? = null,
    val internalMethodId: String? = null,
    val owner: String? = null,
    val status: String? = null
)

/** Minimal info about the profile (Module.kt passes only key + displayName). */
data class MinimalProfileInfo(
    val key: String,
    val displayName: String,
    val labels: Labels? = null
)

/** Geo input echoed back in the response (Module.kt passes tz here too). */
data class GeoInput(
    val lat: Double,
    val lon: Double,
    val elev: Double? = null,
    val tz: String
)

/** Overall request input echoed in compute responses. */
data class ProfileComputeInput(
    val dateIso: String,
    val geo: GeoInput
)

/** One computed row in the profile compute response. */
data class ProfileComputeItem(
    val id: String,
    val label: Labels? = null,
    val resolution: Resolution,
    val utc: String? = null,
    val local: String? = null,
    val instant: String? = null
)

/** Top-level response for /profiles/{key}/compute and /profiles/compute (POST). */
data class ProfileComputeResponse(
    val profile: MinimalProfileInfo,
    val input: ProfileComputeInput,
    val results: List<ProfileComputeItem>,
    val warnings: List<ValidationWarning> = emptyList()
)