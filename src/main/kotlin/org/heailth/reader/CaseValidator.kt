package org.heailth.reader

import org.heailth.config.ConfigLoader
import org.heailth.model.CaseRecord
import org.heailth.model.ParsedRow
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class CaseValidator(private val config: ConfigLoader) {
    private val seenCaseIds = mutableMapOf<String, CaseRecord>()

    fun validate(rowNum: Long, record: CaseRecord?, unparseableField: String?): ParsedRow {
        if (unparseableField != null) {
            return ParsedRow(rowNum, null, null, null, null, null, 0, false, "Invalid format or unparseable value for: $unparseableField", false)
        }

        if (record == null) {
            return ParsedRow(rowNum, null, null, null, null, null, 0, false, "Unknown parsing error", false)
        }

        // Column-specific skip values check
        val skipChecks = mapOf(
            config.getAnesthesiologistColumn() to record.anesthesiologistName,
            config.getSurgeonColumn() to record.surgeon,
            config.getProcedureColumn() to record.procedure,
            config.getSiteColumn() to record.site,
            config.getOrColumn() to record.orName
        )

        for ((col, value) in skipChecks) {
            val specificSkips = config.columnSkipValues[col]
            if (specificSkips != null && value != null && specificSkips.contains(value.trim().lowercase())) {
                return ParsedRow(rowNum, record, null, null, null, null, 0, false, "Ignored value for $col: $value", false)
            }
        }

        // Duplicate Case ID check
        if (seenCaseIds.containsKey(record.id)) {
            val original = seenCaseIds[record.id]!!
            if (original != record) {
                return ParsedRow(rowNum, record, null, null, null, null, 0, false, "Duplicate Case_ID with different data", false)
            }
            return ParsedRow(rowNum, record, null, null, null, null, 0, false, "Duplicate Case_ID", false)
        }
        seenCaseIds[record.id] = record
        
        // Procedure-specific padding check
        val procPadding = record.procedure?.uppercase()?.let { proc ->
            config.procedurePaddings[proc]
        }

        val startPad = procPadding?.first ?: config.startTimePad
        val anesthesiaStart = record.csvStart.minusMinutes(startPad.toLong())

        val endPad = procPadding?.second ?: config.endTimePad
        val anesthesiaEnd = record.csvEnd.plusMinutes(endPad.toLong())

        // CFO Evolution Phase 1: Use Physical Occupancy for Stacking/Packing
        val occupancyStart = record.patientIn.minusMinutes(startPad.toLong())
        val occupancyEnd = record.patientOut.plusMinutes(endPad.toLong())

        var valid = true
        var invalidReason: String? = null
        val duration = ChronoUnit.MINUTES.between(occupancyStart, occupancyEnd)

        if (duration < 0) { // occupancyEnd before occupancyStart (cross-midnight)
            valid = false
            invalidReason = "Cross-midnight duration (ignored per requirement)"
        } else if (duration == 0L) {
            valid = false
            invalidReason = "Zero duration"
        }

        if (valid && duration > 1440) { // Should not happen with LocalTime but for safety
            valid = false
            invalidReason = "Duration exceeds 24 hours"
        }

        if (!valid) {
            logger.info("Row {}: Invalid case. Reason: {}. Start: {}, End: {}", rowNum, invalidReason, occupancyStart, occupancyEnd)
        }

        // Check if outside absolute boundaries
        var outOfBounds = false
        val absStart = config.absoluteStartTime
        val absEnd = config.absoluteEndTime
        
        if (absStart != null && absEnd != null) {
            if (occupancyStart.isBefore(absStart) || occupancyEnd.isAfter(absEnd)) {
                outOfBounds = true
            }
        }

        return ParsedRow(rowNum, record, anesthesiaStart, anesthesiaEnd, occupancyStart, occupancyEnd, duration, valid, invalidReason, outOfBounds)
    }

    fun reset() {
        seenCaseIds.clear()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CaseValidator::class.java)
    }
}
