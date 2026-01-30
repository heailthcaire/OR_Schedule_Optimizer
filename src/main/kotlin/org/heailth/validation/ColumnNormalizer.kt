package org.heailth.validation

import org.heailth.config.ConfigLoader
import java.util.regex.Pattern

class ColumnNormalizer(private val config: ConfigLoader) {
    private val normalizationRules = mutableMapOf<String, Pattern>()

    companion object {
        private val TIME_24H = Regex("^\\d:\\d{2}$")
        private val TIME_12H = Regex("^\\d:\\d{2}\\s*(AM|PM|am|pm)$")
    }

    init {
        val procRegex = config.getProperty("procedure.normalize.regex", "")
        if (procRegex.isNotBlank()) {
            normalizationRules[config.getProcedureColumn().lowercase()] = Pattern.compile(procRegex)
        }
    }

    fun normalize(columnName: String, value: String?): String? {
        if (value == null) return null
        
        val p = normalizationRules[columnName.lowercase()]
        if (p != null) {
            return p.matcher(value).replaceAll("").trim()
        }
        
        return value.trim()
    }
    
    fun autoCorrectDateTime(fieldName: String, value: String?, pattern: String): String? {
        if (!config.isAutoCorrectionEnabled || value.isNullOrBlank()) return value

        var corrected = value.trim()
        
        // Zero-padding for time if needed (e.g., 8:30 -> 08:30)
        if (fieldName.lowercase().contains("time")) {
            // 24h format padding: 8:30 -> 08:30
            if (corrected.matches(TIME_24H)) {
                corrected = "0$corrected"
            }
            // 12h format padding: 8:30 AM -> 08:30 AM
            else if (corrected.matches(TIME_12H)) {
                corrected = "0$corrected"
            }
        }

        return corrected
    }
}
