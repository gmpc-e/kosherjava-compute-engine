package com.elad.halacha.rest.profiles

/** Validation results for profile JSON */
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