package org.heailth.validation

import org.apache.commons.csv.CSVRecord
import org.apache.commons.validator.GenericValidator
import org.apache.commons.validator.routines.EmailValidator
import org.apache.commons.validator.routines.UrlValidator
import org.heailth.config.ConfigLoader
import org.heailth.model.ValidationOutcome
import org.heailth.model.ValidationReport
import org.heailth.util.DateTimeParser
import org.slf4j.LoggerFactory

class RowValidator(
    private val config: ConfigLoader,
    private val normalizer: ColumnNormalizer,
    private val pluggableValidators: List<ColumnValidator>
) {
    private val logger = LoggerFactory.getLogger(RowValidator::class.java)
    private val emailValidator = EmailValidator.getInstance()
    private val urlValidator = UrlValidator.getInstance()

    data class ValidationContext(
        val dates: MutableMap<String, java.time.LocalDate> = mutableMapOf(),
        val times: MutableMap<String, java.time.LocalTime> = mutableMapOf()
    )

    fun validate(record: CSVRecord, requiredToActual: Map<String, String>, report: ValidationReport, isJsonOutput: Boolean = false): ValidationOutcome {
        val normalizedData = mutableMapOf<String, String>()
        val vContext = ValidationContext()
        var corrected = false

        // 1. Required Field Presence & Normalization
        for ((requiredField, actualHeader) in requiredToActual) {
            val rawValue = record[actualHeader]
            val trimmedValue = rawValue?.trim() ?: ""

            if (GenericValidator.isBlankOrNull(trimmedValue) || config.invalidRequiredValues.contains(trimmedValue.lowercase())) {
                report.incrementRequiredFieldNull(requiredField)
                val skipReason = "Rows missing or containing invalid required data: $requiredField (value: '$trimmedValue')"
                if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Rejecting row: {}", skipReason)
                return ValidationOutcome(false, false, null, skipReason)
            }

            // --- Schema-driven validation ---
            val constraint = config.columnConstraints[requiredField]
            if (constraint != null) {
                // Type Validation
                when (constraint.type?.lowercase()) {
                    "integer" -> if (!GenericValidator.isInt(trimmedValue)) {
                        val reason = "$requiredField must be an integer: $trimmedValue"
                        if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Rejecting row: {}", reason)
                        return ValidationOutcome(false, false, null, reason)
                    }
                    "numeric" -> if (DateTimeParser.parseDurationToDouble(trimmedValue) == null) {
                        val reason = "$requiredField must be numeric (e.g. 1.5 or hh:mm): $trimmedValue"
                        if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Rejecting row: {}", reason)
                        return ValidationOutcome(false, false, null, reason)
                    }
                    "email" -> if (!emailValidator.isValid(trimmedValue)) {
                        val reason = "Invalid email format in $requiredField"
                        if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Rejecting row: {}", reason)
                        return ValidationOutcome(false, false, null, reason)
                    }
                    "url" -> if (!urlValidator.isValid(trimmedValue)) {
                        val reason = "Invalid URL format in $requiredField"
                        if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Rejecting row: {}", reason)
                        return ValidationOutcome(false, false, null, reason)
                    }
                    "date" -> {
                        val fmt = config.getDateFormat()
                        if (!GenericValidator.isDate(trimmedValue, fmt, true) && DateTimeParser.parseFlexibleDate(trimmedValue, requiredField) == null) {
                            val reason = "Invalid date format in $requiredField (value: $trimmedValue, expected: $fmt)"
                            if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Rejecting row: {}", reason)
                            return ValidationOutcome(false, false, null, reason)
                        }
                    }
                    "regex" -> if (constraint.pattern != null && !GenericValidator.matchRegexp(trimmedValue, constraint.pattern)) {
                        val reason = "$requiredField does not match required pattern"
                        if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Rejecting row: {}", reason)
                        return ValidationOutcome(false, false, null, reason)
                    }
                }

                // Length Validation
                if (constraint.maxLength != null && !GenericValidator.maxLength(trimmedValue, constraint.maxLength))
                    return ValidationOutcome(false, false, null, "$requiredField exceeds max length ${constraint.maxLength}")
                
                if (constraint.minLength != null && !GenericValidator.minLength(trimmedValue, constraint.minLength))
                    return ValidationOutcome(false, false, null, "$requiredField is shorter than min length ${constraint.minLength}")

                // Range Validation (Numeric)
                if (constraint.minValue != null || constraint.maxValue != null) {
                    val doubleVal = DateTimeParser.parseDurationToDouble(trimmedValue)
                    if (doubleVal != null) {
                        if (constraint.minValue != null && doubleVal < constraint.minValue)
                            return ValidationOutcome(false, false, null, "$requiredField value $doubleVal < min ${constraint.minValue}")
                        if (constraint.maxValue != null && doubleVal > constraint.maxValue)
                            return ValidationOutcome(false, false, null, "$requiredField value $doubleVal > max ${constraint.maxValue}")
                    }
                }

            }
            // --- End Schema-driven validation ---

            // Column-specific skip values
            val specificSkips = config.columnSkipValues[requiredField]
            if (specificSkips != null && specificSkips.contains(trimmedValue.lowercase())) {
                return ValidationOutcome(false, false, null, "Ignored value for $requiredField: $trimmedValue")
            }

            // Normalization
            val normalizedValue = normalizer.normalize(requiredField, rawValue)
            if (normalizedValue != rawValue.trim()) corrected = true
            
            normalizedData[requiredField] = normalizedValue ?: ""
        }

        // 2. Format Validation & Auto-correction
        if (!validateFormats(normalizedData, vContext, report, isJsonOutput)) {
            val reason = "Format violation"
            if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Rejecting row: {}", reason)
            return ValidationOutcome(false, false, null, reason)
        }

        // 3. Logical Consistency (Zero duration / Cross-midnight / Outliers)
        val durationError = validateDurations(normalizedData, vContext)
        if (durationError != null) {
            if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Rejecting row: {}", durationError)
            return ValidationOutcome(false, false, null, durationError)
        }

        // Standardize output formats for Clean File
        standardizeOutputFormats(normalizedData, vContext)

        // 4. Pluggable Validators (e.g., ICD-10)
        for (validator in pluggableValidators) {
            for (field in normalizedData.keys) {
                if (validator.isApplicable(field)) {
                    val original = normalizedData[field]!!
                    val validated = validator.validateAndNormalize(original, normalizedData)
                    
                    if (validated == null) {
                        return ValidationOutcome(false, false, null, "Custom validation failed: $field")
                    }
                    
                    if (validator.wasCorrected(original, validated)) {
                        corrected = true
                    }
                    normalizedData[field] = validated
                }
            }
        }

        return ValidationOutcome(true, corrected, normalizedData, null)
    }

    private fun standardizeOutputFormats(data: MutableMap<String, String>, vContext: ValidationContext) {
        val dateFormat = config.getDateFormat()
        val timeFormat = config.getTimeFormat()

        vContext.dates.forEach { (field, date) ->
            data[field] = date.format(java.time.format.DateTimeFormatter.ofPattern(dateFormat, java.util.Locale.US))
        }

        vContext.times.forEach { (field, time) ->
            data[field] = time.format(java.time.format.DateTimeFormatter.ofPattern(timeFormat, java.util.Locale.US))
        }
    }

    private fun validateDurations(data: MutableMap<String, String>, vContext: ValidationContext): String? {
        val patientInCol = config.getPatientInColumn()
        val patientOutCol = config.getPatientOutColumn()
        
        val pIn = vContext.times[patientInCol]
        val pOut = vContext.times[patientOutCol]
        
        if (pIn != null && pOut != null) {
            val duration = java.time.temporal.ChronoUnit.MINUTES.between(pIn, pOut)
            if (duration < 0) {
                return "Cross-midnight duration"
            }
            val minMinutes = config.getMinProcedureDurationMinutes()
            if (duration < minMinutes) {
                return "Procedures under min duration (<${minMinutes}m)"
            }
            val maxHours = config.getMaxProcedureDurationHours()
            if (duration > maxHours * 60) {
                return "Procedures over max duration (>${maxHours}h)"
            }
        }
        return null
    }

    private fun validateFormats(data: MutableMap<String, String>, vContext: ValidationContext, report: ValidationReport, isJsonOutput: Boolean = false): Boolean {
        // Date
        val dateField = if (data.containsKey(config.getCrnaDateColumn())) config.getCrnaDateColumn() else config.getDateColumn()
        val dateVal = data[dateField]
        val dateFormat = config.getDateFormat()
        if (dateVal != null) {
            val corrected = normalizer.autoCorrectDateTime(dateField, dateVal, dateFormat)
            val parsedDate = DateTimeParser.parseDate(corrected, dateFormat, dateField)
            if (parsedDate == null) {
                report.incrementFormatViolation(dateField)
                return false
            }
            vContext.dates[dateField] = parsedDate
            if (corrected != null && corrected != dateVal) data[dateField] = corrected
        }

        // Times (only for OR data)
        if (data.containsKey(config.getStartTimeField()) && data.containsKey(config.getEndTimeField())) {
            val timeFields = arrayOf(
                config.getStartTimeField(),
                config.getEndTimeField(),
                config.getPatientInColumn(),
                config.getPatientOutColumn()
            )
            val timeFormat = config.getTimeFormat()
            for (tf in timeFields) {
                val valStr = data[tf]
                if (valStr != null) {
                    val corrected = normalizer.autoCorrectDateTime(tf, valStr, timeFormat)
                    val parsedTime = DateTimeParser.parseTime(corrected, timeFormat, tf)
                    if (parsedTime == null) {
                        report.incrementFormatViolation(tf)
                        return false
                    }
                    vContext.times[tf] = parsedTime
                    if (corrected != null && corrected != valStr) data[tf] = corrected
                }
            }
        }

        return true
    }
}
