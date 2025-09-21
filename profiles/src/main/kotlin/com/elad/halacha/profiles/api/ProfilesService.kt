package com.elad.halacha.profiles.api

interface ProfilesService {
    /** List available profiles on the engine’s classpath. */
    fun listProfiles(): List<ProfileSummary>

    /** Fetch the full profile JSON by key. */
    fun getProfile(key: String): Profile?

    /**
     * Compute a stored profile (by key) at the given date and geo.
     * The engine resolves internal/external methods and returns computed times.
     */
    fun computeProfile(key: String, input: ProfileComputeInput): ProfileComputeResponse
}

/** Lightweight summary for list screens. */
data class ProfileSummary(
    val key: String,
    val displayName: String,
    val labels: Labels? = null
)