package com.elad.halacha.rest.profiles

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

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
    val kind: String,
    val externalMethod: String? = null,
    val internalMethodId: String? = null,
    val params: Map<String, Any?>? = null
)