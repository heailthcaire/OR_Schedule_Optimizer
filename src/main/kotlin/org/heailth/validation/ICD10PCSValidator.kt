package org.heailth.validation

import org.heailth.config.ConfigLoader

class ICD10PCSValidator(config: ConfigLoader) : ColumnValidator {
    private val enabled = config.isIcd10ValidationEnabled
    private val targetColumn = config.getProcedureColumn()
    private val referenceSet = mutableSetOf<String>() // Placeholder for actual loading

    companion object {
        private val NON_ALPHANUMERIC = Regex("[^A-Z0-9]")
        private val STRUCTURAL_REGEX = Regex("^[A-Z0-9]{7}$")
    }

    override fun isApplicable(columnName: String): Boolean {
        return enabled && columnName.equals(targetColumn, ignoreCase = true)
    }

    override fun validateAndNormalize(value: String, context: Map<String, String>): String? {
        if (value.isBlank()) return null

        // 1. Normalize
        val normalized = value.trim().uppercase().replace(NON_ALPHANUMERIC, "")

        // 2. Validate basic structure (7 chars, alphanumeric)
        if (normalized.length == 7 && normalized.matches(STRUCTURAL_REGEX)) {
            // If reference set is not empty, check existence
            if (referenceSet.isEmpty() || referenceSet.contains(normalized)) {
                return normalized
            }
        }

        // 3. Attempt limited corrections (O -> 0, I -> 1)
        val corrected = normalized
            .replace('O', '0')
            .replace('I', '1')

        if (corrected.length == 7 && corrected.matches(STRUCTURAL_REGEX)) {
            if (referenceSet.isEmpty() || referenceSet.contains(corrected)) {
                return corrected
            }
        }

        return null // Invalid and uncorrectable
    }
}
