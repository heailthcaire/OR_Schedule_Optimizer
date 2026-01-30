package org.heailth.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object DateTimeParser {
    private val formatterCache = ConcurrentHashMap<String, DateTimeFormatter>()
    private val lastSuccessfulPattern = ConcurrentHashMap<String, String>() // columnName -> pattern

    private val TIME_FORMATTERS = listOf(
        "HH:mm" to DateTimeFormatter.ofPattern("HH:mm", Locale.US),
        "H:mm" to DateTimeFormatter.ofPattern("H:mm", Locale.US),
        "hh:mm a" to DateTimeFormatter.ofPattern("hh:mm a", Locale.US),
        "h:mm a" to DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        "hh:mmA" to DateTimeFormatter.ofPattern("hh:mmA", Locale.US),
        "h:mmA" to DateTimeFormatter.ofPattern("h:mmA", Locale.US),
        "ISO" to DateTimeFormatter.ISO_LOCAL_TIME
    )

    private val DATE_FORMATTERS = listOf(
        "yyyy-MM-dd" to DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US),
        "M/d/yyyy" to DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
        "MM/dd/yyyy" to DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
        "M/d/yy" to DateTimeFormatter.ofPattern("M/d/yy", Locale.US),
        "MM-dd-yyyy" to DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.US),
        "M-d-yyyy" to DateTimeFormatter.ofPattern("M-d-yyyy", Locale.US),
        "yyyyMMdd" to DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US),
        "ISO" to DateTimeFormatter.ISO_LOCAL_DATE
    )

    @JvmStatic
    fun parseTime(timeStr: String?, customPattern: String?, fieldName: String? = null): LocalTime? {
        if (timeStr.isNullOrBlank()) return null
        val normalized = timeStr.trim().replace(Regex("\\s+"), " ")

        // 1. Try last successful pattern for this field
        if (fieldName != null) {
            val lastPattern = lastSuccessfulPattern[fieldName]
            if (lastPattern != null) {
                runCatching {
                    val formatter = formatterCache.getOrPut(lastPattern) {
                        DateTimeFormatter.ofPattern(lastPattern, Locale.US)
                    }
                    return LocalTime.parse(normalized, formatter)
                }
            }
        }

        // 2. Try current custom pattern
        if (!customPattern.isNullOrBlank()) {
            runCatching {
                val formatter = formatterCache.getOrPut(customPattern) {
                    DateTimeFormatter.ofPattern(customPattern, Locale.US)
                }
                val result = LocalTime.parse(normalized, formatter)
                if (fieldName != null) lastSuccessfulPattern[fieldName] = customPattern
                return result
            }
        }

        // 3. Fallback to flexible parsing
        return parseFlexibleTime(normalized, fieldName)
    }

    @JvmStatic
    fun parseFlexibleTime(timeStr: String?, fieldName: String? = null): LocalTime? {
        if (timeStr.isNullOrBlank()) return null
        val normalized = timeStr.trim().replace(Regex("\\s+"), " ").uppercase()
        
        for (pair in TIME_FORMATTERS) {
            runCatching {
                val result = LocalTime.parse(normalized, pair.second)
                if (fieldName != null) lastSuccessfulPattern[fieldName] = pair.first
                return result
            }
        }
        return null
    }

    @JvmStatic
    fun parseDate(dateStr: String?, customPattern: String?, fieldName: String? = null): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        val normalized = dateStr.trim()

        // 1. Try last successful pattern for this field
        if (fieldName != null) {
            val lastPattern = lastSuccessfulPattern[fieldName]
            if (lastPattern != null) {
                runCatching {
                    val formatter = formatterCache.getOrPut(lastPattern) {
                        DateTimeFormatter.ofPattern(lastPattern, Locale.US)
                    }
                    return LocalDate.parse(normalized, formatter)
                }
            }
        }

        // 2. Try current custom pattern
        if (!customPattern.isNullOrBlank()) {
            runCatching {
                val formatter = formatterCache.getOrPut(customPattern) {
                    DateTimeFormatter.ofPattern(customPattern, Locale.US)
                }
                val result = LocalDate.parse(normalized, formatter)
                if (fieldName != null) lastSuccessfulPattern[fieldName] = customPattern
                return result
            }
        }

        return parseFlexibleDate(normalized, fieldName)
    }

    @JvmStatic
    fun parseFlexibleDate(dateStr: String?, fieldName: String? = null): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        val normalized = dateStr.trim()
        
        for (pair in DATE_FORMATTERS) {
            runCatching {
                val result = LocalDate.parse(normalized, pair.second)
                if (fieldName != null) lastSuccessfulPattern[fieldName] = pair.first
                return result
            }
        }
        return null
    }

    @JvmStatic
    fun parseDurationToDouble(durationStr: String?): Double? {
        if (durationStr.isNullOrBlank()) return null
        val normalized = durationStr.trim()
        return try {
            if (normalized.contains(":")) {
                val parts = normalized.split(":")
                val hours = parts[0].toDouble()
                val minutes = if (parts.size > 1) parts[1].toDouble() else 0.0
                hours + (minutes / 60.0)
            } else {
                normalized.toDouble()
            }
        } catch (e: Exception) {
            null
        }
    }
}
