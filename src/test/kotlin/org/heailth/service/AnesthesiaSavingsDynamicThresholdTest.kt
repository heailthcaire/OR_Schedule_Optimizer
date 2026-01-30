package org.heailth.service

import org.heailth.model.CaseRecord
import org.heailth.model.ParsedRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class AnesthesiaSavingsDynamicThresholdTest {

    private val service = AnesthesiaSavingsService()

    @Test
    fun `test method 2 savings with dynamic threshold`() {
        val date = LocalDate.of(2025, 1, 1)
        
        // Scenario: 16 hours of saved time (960 minutes)
        // If threshold is 8 hours (480 mins) -> 2 FTEs
        // If threshold is 12 hours (720 mins) -> 1 FTE
        
        // Window Before: 2 rooms * 12 hours = 24 hours (1440 mins)
        // Window After: 1 room * 8 hours = 8 hours (480 mins)
        // Savings = 16 hours (960 mins)
        
        val row1 = createRow(1, "Site A", date, "OR 1", "08:00", "20:00", "Anes 1", "OR 1")
        val row2 = createRow(2, "Site A", date, "OR 2", "08:00", "20:00", "Anes 2", "OR 2")
        
        val originalCases = listOf(row1, row2)
        
        // Optimized: all cases in one room for 8 hours (just for the sake of math)
        val opt1 = createRow(1, "Site A", date, "OR 1", "08:00", "16:00", "Anes 1", "Consolidated 1")
        val opt2 = createRow(2, "Site A", date, "OR 1", "08:00", "16:00", "Anes 1", "Consolidated 1")
        val optimizedCases = listOf(opt1, opt2)
        
        val actualRooms = setOf("Site A - OR 1", "Site A - OR 2")
        
        // Test with 8-hour threshold
        val savings8 = service.calculateSavings(
            originalCases, optimizedCases, actualRooms,
            thresholdMinutes = 480
        )
        // Window reduction: 1440 - 480 = 960 mins. 960 / 480 = 2 FTEs.
        assertEquals(2, savings8.method2Savings)
        
        // Test with 12-hour threshold
        val savings12 = service.calculateSavings(
            originalCases, optimizedCases, actualRooms,
            thresholdMinutes = 720
        )
        // Window reduction: 960 mins. 960 / 720 = 1 FTE.
        assertEquals(1, savings12.method2Savings)
    }

    private fun createRow(id: Long, site: String, date: LocalDate, orName: String, start: String, end: String, anes: String, assigned: String): ParsedRow {
        val startTime = LocalTime.parse(start)
        val endTime = LocalTime.parse(end)
        val record = CaseRecord(
            id = id.toString(),
            site = site,
            date = date,
            orName = orName,
            surgeon = "Dr. Test",
            procedure = "Test Procedure",
            csvStart = startTime,
            csvEnd = endTime,
            patientIn = startTime,
            patientOut = endTime,
            anesthesiologistName = anes
        )
        return ParsedRow(
            rowNumber = id,
            record = record,
            anesthesiaStart = startTime,
            anesthesiaEnd = endTime,
            occupancyStart = startTime,
            occupancyEnd = endTime,
            durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime),
            isValid = true,
            invalidReason = null,
            isOutOfBounds = false,
            assignedRoomName = assigned
        )
    }
}
