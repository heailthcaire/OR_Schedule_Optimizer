package org.heailth.model

import java.time.LocalTime

data class ParsedRow(
    val rowNumber: Long,
    val record: CaseRecord?,
    val anesthesiaStart: LocalTime?,
    val anesthesiaEnd: LocalTime?,
    val occupancyStart: LocalTime?,
    val occupancyEnd: LocalTime?,
    val durationMinutes: Long,
    val isValid: Boolean,
    val invalidReason: String?,
    val isOutOfBounds: Boolean,
    var assignedRoomName: String? = null
) {
    // For Java compatibility as the original was a class with manual constructor
    constructor(
        rowNumber: Long,
        record: CaseRecord?,
        anesthesiaStart: LocalTime?,
        anesthesiaEnd: LocalTime?,
        occupancyStart: LocalTime?,
        occupancyEnd: LocalTime?,
        durationMinutes: Long,
        isValid: Boolean,
        invalidReason: String?,
        isOutOfBounds: Boolean
    ) : this(rowNumber, record, anesthesiaStart, anesthesiaEnd, occupancyStart, occupancyEnd, durationMinutes, isValid, invalidReason, isOutOfBounds, null)

    fun getAnesthesiologist(): String? = record?.anesthesiologistName
}
