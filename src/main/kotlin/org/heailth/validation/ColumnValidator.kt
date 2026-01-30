package org.heailth.validation

interface ColumnValidator {
    /**
     * @return corrected value if possible, null if invalid/skipped
     */
    fun validateAndNormalize(value: String, context: Map<String, String>): String?
    
    fun isApplicable(columnName: String): Boolean
    
    fun wasCorrected(original: String?, normalized: String?): Boolean {
        if (original == null || normalized == null) return false
        return original != normalized
    }
}
