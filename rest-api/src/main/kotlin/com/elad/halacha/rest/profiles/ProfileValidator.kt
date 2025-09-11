package com.elad.halacha.rest.profiles

import com.elad.halacha.engine.compute.MethodRegistry

object ProfileValidator {

    fun validate(profile: Profile): ValidationResponse {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // version
        if (profile.version != 1) {
            errors += ValidationError("$.version", "unsupported_version", "Only version=1 is supported")
        }

        // key
        val keyOk = Regex("^[A-Za-z0-9_-]{1,40}$").matches(profile.key)
        if (!keyOk) {
            errors += ValidationError("$.key", "invalid_key", "Allowed: [A-Za-z0-9_-], length 1-40")
        }

        // times
        if (profile.times.isEmpty()) {
            errors += ValidationError("$.times", "empty_times", "At least one time entry is required")
        }

        // unique time ids
        val seen = mutableSetOf<String>()
        profile.times.forEachIndexed { idx, t ->
            val path = "$.times[$idx]"
            val idOk = Regex("^[A-Za-z0-9_-]{1,40}$").matches(t.id)
            if (!idOk) {
                errors += ValidationError("$path.id", "invalid_id", "Allowed: [A-Za-z0-9_-], length 1-40")
            }
            if (!seen.add(t.id)) {
                errors += ValidationError("$path.id", "duplicate_id", "Duplicate time id '${t.id}'")
            }

            // label: optional but recommend at least one language
            val he = t.label?.he
            val en = t.label?.en
            if ((he == null || he.isBlank()) && (en == null || en.isBlank())) {
                warnings += ValidationWarning("$path.label", "missing_label", "Provide 'he' or 'en' for better UX")
            }

            // target
            when (t.target.kind) {
                "EXTERNAL_NAME" -> {
                    val name = t.target.externalMethod
                    if (name.isNullOrBlank()) {
                        errors += ValidationError("$path.target.externalMethod", "missing_external_method", "Required for kind=EXTERNAL_NAME")
                    } else {
                        val found = MethodRegistry.resolve(name)
                        if (found == null) {
                            errors += ValidationError("$path.target.externalMethod", "unknown_external_method", "Method '$name' not found in registry")
                        }
                    }
                }
                "INTERNAL" -> {
                    // Non-blocking: allow unknown internal ids (future support)
                    val id = t.target.internalMethodId
                    if (id.isNullOrBlank()) {
                        // Internal target specified but missing id – warn, not error
                        warnings += ValidationWarning("$path.target.internalMethodId", "missing_internal_id", "No internalMethodId provided; will be unresolved at compute")
                    } else {
                        // Unknown internal method id – warn (not error) by default
                        warnings += ValidationWarning("$path.target.internalMethodId", "unknown_internal_method", "Internal method '$id' is not registered (yet); will be unresolved at compute")
                    }
                }
                else -> {
                    errors += ValidationError("$path.target.kind", "unsupported_kind", "Supported kinds: EXTERNAL_NAME, INTERNAL")
                }
            }
        }

        return ValidationResponse(errors.isEmpty(), errors, warnings)
    }
}
