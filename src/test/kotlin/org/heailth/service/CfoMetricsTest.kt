package org.heailth.service

import org.heailth.config.ConfigLoader
import org.heailth.model.CaseRecord
import org.heailth.model.ParsedRow
import org.heailth.validation.BaseValidationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.io.File

class CfoMetricsTest : BaseValidationTest() {

    @Test
    fun `test cfo metrics calculation`() {
        val propsFile = File("target/test-cfo-metrics.properties")
        val content = """
            bin.capacity.minutes = 480
            cost.per.or.day = 2400
            csv.path = dummy.csv
            report.path = dummy.html
            id.column = Case_ID
            date.column = Date
            site.column = Site
            or.column = OR_Room
            surgeon.column = Surgeon_Name
            procedure.column = Procedure_Type
            csv.required.columns = Case_ID,Date,Site,OR_Room,Surgeon_Name,Procedure_Type,Anesthesia_Start_Time,Anesthesia_End_Time,Patient_In_Room_Time,Patient_Out_Room_Time
            anesthesia.start.time.column = Anesthesia_Start_Time
            anesthesia.end.time.column = Anesthesia_End_Time
            or.occupancy.start.time.column = Patient_In_Room_Time
            or.occupancy.end.time.column = Patient_Out_Room_Time
            date.format = M/d/yyyy
            time.format = hh:mm a
        """.trimIndent()
        propsFile.writeText(content)
        val config = ConfigLoader(propsFile.path, null, null, null, null, null)
        val financialService = FinancialMetricsService(config)
        
        val date = LocalDate.of(2025, 1, 1)
        
        // Case 1: 08:00 - 10:00 (120 min) - In Prime Time
        val row1 = createRow(1, "Site A", date, "OR 1", "08:00", "10:00")
        // Case 2: 11:00 - 13:00 (120 min) - In Prime Time
        val row2 = createRow(2, "Site A", date, "OR 1", "11:00", "13:00")
        
        val rows = listOf(row1, row2)
        
        // Prime Time (07:00 - 15:00) = 8 hours = 480 min
        // Used in Prime Time = 120 + 120 = 240 min
        // Util = 240 / 480 = 50%
        val primeTimeUtil = financialService.calculatePrimeTimeUtilization(rows)
        assertEquals(50.0, primeTimeUtil, 0.1)
        
        // FCOTS: First case in OR 1 started at 08:00. Target is 07:30. So it's LATE.
        // FCOTS % = 0%
        val fcots = financialService.calculateFCOTS(rows)
        assertEquals(0.0, fcots, 0.1)
        
        // TOT: Between Case 1 (out 10:00) and Case 2 (in 11:00) = 60 min
        val tot = financialService.calculateAverageTOT(rows)
        assertEquals(60.0, tot, 0.1)
        
        // Labor Yield: Anesthesia duration 120 + 120 = 240. Total block 480. 50%.
        val laborYield = financialService.calculateLaborYield(rows)
        assertEquals(50.0, laborYield, 0.1)

        // Contribution Margin: 
        // Cost per hour = 2400 / 8 = 300
        // Revenue per hour (default) = 5000
        // Margin = 5000 - 300 = 4700
        val margin = financialService.calculateContributionMargin(rows)
        assertEquals(4700.0, margin, 0.1)

        // Test with Anesthesia Costs
        val propsWithAnes = File("target/test-cfo-anes.properties")
        val contentAnes = """
            bin.capacity.minutes = 480
            cost.per.or.day = 2400
            financials.include.anesthesiologist = true
            financials.anesthesiologist.rate = 1600
            csv.path = dummy.csv
            report.path = dummy.html
            id.column = Case_ID
            date.column = Date
            site.column = Site
            or.column = OR_Room
            surgeon.column = Surgeon_Name
            procedure.column = Procedure_Type
            csv.required.columns = Case_ID,Date,Site,OR_Room,Surgeon_Name,Procedure_Type,Anesthesia_Start_Time,Anesthesia_End_Time,Patient_In_Room_Time,Patient_Out_Room_Time
            anesthesia.start.time.column = Anesthesia_Start_Time
            anesthesia.end.time.column = Anesthesia_End_Time
            or.occupancy.start.time.column = Patient_In_Room_Time
            or.occupancy.end.time.column = Patient_Out_Room_Time
            date.format = M/d/yyyy
            time.format = hh:mm a
        """.trimIndent()
        propsWithAnes.writeText(contentAnes)
        val configAnes = ConfigLoader(propsWithAnes.path, null, null, null, null, null)
        val financialServiceAnes = FinancialMetricsService(configAnes)
        
        // Bundled cost = 2400 + 1600 = 4000
        // Cost per hour = 4000 / 8 = 500
        // Revenue = 5000
        // Margin = 5000 - 500 = 4500
        val marginAnes = financialServiceAnes.calculateContributionMargin(rows)
        assertEquals(4500.0, marginAnes, 0.1)
        
        propsFile.delete()
        propsWithAnes.delete()
    }

    private fun createRow(id: Long, site: String, date: LocalDate, orName: String, start: String, end: String): ParsedRow {
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
            anesthesiologistName = "Dr. Anes"
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
            isOutOfBounds = false
        )
    }
}
