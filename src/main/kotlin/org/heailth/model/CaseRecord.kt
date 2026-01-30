package org.heailth.model

import java.time.LocalDate
import java.time.LocalTime

@JvmRecord
data class CaseRecord(
    val id: String,
    val site: String,
    val date: LocalDate,
    val orName: String,
    val surgeon: String?,
    val procedure: String?,
    val csvStart: LocalTime,
    val csvEnd: LocalTime,
    val patientIn: LocalTime,
    val patientOut: LocalTime,
    val anesthesiologistName: String?
)

enum class ProcessingMode {
    FULL,
    VALIDATE_ONLY,
    NORMALIZE_ONLY,
    DIAGNOSTIC,
    CREATE_CFO_REPORT
}
