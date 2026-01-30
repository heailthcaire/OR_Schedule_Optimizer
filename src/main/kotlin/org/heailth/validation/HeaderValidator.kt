package org.heailth.validation
    
import org.heailth.config.ConfigLoader
import org.slf4j.LoggerFactory

class HeaderValidator(private val config: ConfigLoader) {
    private val logger = LoggerFactory.getLogger(HeaderValidator::class.java)

    companion object {
        private val CONTROL_CHARS = Regex("[\\p{Cntrl}\\p{Cf}]")
        private val BOMS = listOf("\ufeff", "\ufffe", "\u200b", "\u200c", "\u200d")
    }

    data class HeaderValidationResult(
        val requiredToActual: Map<String, String>,
        val missingHeaders: List<String>,
        val actualHeaders: List<String>
    )

    fun validateAndMap(headerMap: Map<String, Int>, requiredColumns: List<String>? = null): HeaderValidationResult {
        val required = requiredColumns ?: config.csvRequiredColumns
        val missing = mutableListOf<String>()
        val actualHeaders = headerMap.keys.map { cleanForDisplay(it) }
        
        // Normalize actual headers from CSV
        val normalizedToActual = headerMap.keys.associateBy { 
            val norm = normalize(it)
            logger.debug("Header: '{}' -> Normalized: '{}'", it, norm)
            norm
        }

        val requiredToActual = mutableMapOf<String, String>()

        for (field in required) {
            val normalizedRequired = normalize(field)
            if (normalizedToActual.containsKey(normalizedRequired)) {
                requiredToActual[field] = normalizedToActual[normalizedRequired]!!
            } else {
                logger.debug("Missing required field: '{}' (normalized: '{}')", field, normalizedRequired)
                missing.add(field)
            }
        }

        return HeaderValidationResult(requiredToActual, missing, actualHeaders)
    }

    private fun cleanForDisplay(s: String): String {
        return s.trim().replace(CONTROL_CHARS, "")
    }

    private fun normalize(s: String?): String {
        if (s == null) return ""
        var normalized = s.trim()
        
        // Comprehensive BOM and zero-width space removal
        for (bom in BOMS) {
            if (normalized.startsWith(bom)) {
                normalized = normalized.substring(bom.length)
            }
        }
        
        // Remove any non-printable or control characters that might be lingering
        normalized = normalized.replace(CONTROL_CHARS, "")
        
        val result = normalized.lowercase().replace(" ", "").replace("_", "")
        return result
    }
}
