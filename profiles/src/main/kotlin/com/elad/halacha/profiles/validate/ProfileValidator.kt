package com.elad.halacha.profiles.validate

import com.elad.halacha.profiles.api.*

object ProfileValidator {

    fun validate(p: Profile): ValidationResponse {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        if (p.version <= 0) {
            errors += ValidationError("$.version", "invalid_version", "version must be > 0")
        }
        if (p.key.isBlank()) {
            errors += ValidationError("$.key", "missing_key", "key is required")
        }
        if (p.displayName.isBlank()) {
            errors += ValidationError("$.displayName", "missing_display_name", "displayName is required")
        }
        if (p.times.isEmpty()) {
            errors += ValidationError("$.times", "empty_times", "times must be a non-empty array")
        }

        p.times.forEachIndexed { idx, t ->
            if (t.id.isBlank()) {
                errors += ValidationError("$.times[$idx].id", "missing_id", "id is required")
            }
            when (t.target.kind) {
                "EXTERNAL_NAME" -> {
                    if (t.target.externalMethod.isNullOrBlank()) {
                        errors += ValidationError("$.times[$idx].target.externalMethod", "missing_external_method", "externalMethod is required")
                    }
                }
                "INTERNAL" -> {
                    if (t.target.internalMethodId.isNullOrBlank()) {
                        warnings += ValidationWarning("$.times[$idx].target.internalMethodId", "missing_internal_method_id", "Internal method id missing; will be unresolved at compute")
                    }
                }
                else -> {
                    errors += ValidationError("$.times[$idx].target.kind", "unsupported_kind", "kind must be EXTERNAL_NAME or INTERNAL")
                }
            }
        }

        return ValidationResponse(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}