package org.heailth.service

import org.heailth.config.ConfigLoader
import org.heailth.model.CaseRecord
import org.heailth.model.CrnaRecord
import org.heailth.model.ParsedRow
import org.heailth.validation.BaseValidationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class CrnaOverstaffingTest : BaseValidationTest() {

    @Test
    fun `test crna overstaffing with productivity factor`() {
        val propsFile = File("target/test-crna-productivity.properties")
        val content = """
            crna.productivity.factor = 0.80
            financials.crna.rate = 2000
            id.column = Case_ID
            date.column = Date
            site.column = Site
            or.column = OR_Room
            surgeon.column = Surgeon_Name
            procedure.column = Procedure_Type
            csv.path = dummy.csv
            report.path = dummy.html
            bin.capacity.minutes = 480
            date.format = M/d/yyyy
            time.format = hh:mm a
            anesthesia.start.time.column = Anesthesia_Start_Time
            anesthesia.end.time.column = Anesthesia_End_Time
            or.occupancy.start.time.column = Patient_In_Room_Time
            or.occupancy.end.time.column = Patient_Out_Room_Time
            csv.required.columns = Case_ID,Date,Site,OR_Room,Surgeon_Name,Procedure_Type,Anesthesia_Start_Time,Anesthesia_End_Time,Patient_In_Room_Time,Patient_Out_Room_Time
        """.trimIndent()
        propsFile.writeText(content)
        
        val config = ConfigLoader(propsFile.path, null, null, null, null, null)
        val financialService = FinancialMetricsService(config)
        val statsService = StatisticsService(financialService)
        
        val date = LocalDate.of(2025, 1, 1)
        val site = "TestSite"
        
        // Demand: 1 case, 120 minutes = 2.0 hours
        val caseRow = createRow(1, site, date, "OR 1", "08:00", "10:00")
        val validRows = listOf(caseRow)
        
        // Supply: 1 CRNA, 10 hours
        val crnaRow = CrnaRecord(region = "Standalone: $site", name = "CRNA1", date = date, durationHours = 10.0)
        val crnaRows = listOf(crnaRow)
        
        // Logic:
        // Scheduled = 10.0
        // Effective = 10.0 * 0.80 = 8.0
        // Required = 120 / 60 = 2.0
        // Extra = 8.0 - 2.0 = 6.0
        // Scheduled CRNA Cost = 8.0 * 150 = 1200.0 (using default 150 since not in content)
        
        val result = statsService.calculateGlobalStaffing(validRows, crnaRows, config)
        
        assertEquals(10.0, result["totalCrnaHours"] as Double, 0.01)
        assertEquals(8.0, result["scheduledCrnaHours"] as Double, 0.01)
        assertEquals(2.0, result["requiredCrnaHours"] as Double, 0.01)
        assertEquals(6.0, result["extraCrnaHours"] as Double, 0.01)
        assertEquals(1200.0, result["crnaCostSaved"] as Double, 0.01)
    }

    @Test
    fun `test requiredCrnaHours excludes padding`() {
        val propsFile = File("target/test-crna-padding.properties")
        val content = """
            crna.productivity.factor = 1.0
            start.time.pad.minutes = 10
            end.time.pad.minutes = 10
            id.column = Case_ID
            date.column = Date
            site.column = Site
            or.column = OR_Room
            surgeon.column = Surgeon_Name
            procedure.column = Procedure_Type
            csv.path = dummy.csv
            report.path = dummy.html
            bin.capacity.minutes = 480
            date.format = M/d/yyyy
            time.format = hh:mm a
            anesthesia.start.time.column = Anesthesia_Start_Time
            anesthesia.end.time.column = Anesthesia_End_Time
            or.occupancy.start.time.column = Patient_In_Room_Time
            or.occupancy.end.time.column = Patient_Out_Room_Time
            csv.required.columns = Case_ID,Date,Site,OR_Room,Surgeon_Name,Procedure_Type,Anesthesia_Start_Time,Anesthesia_End_Time,Patient_In_Room_Time,Patient_Out_Room_Time
        """.trimIndent()
        propsFile.writeText(content)
        
        val config = ConfigLoader(propsFile.path, null, null, null, null, null)
        val financialService = FinancialMetricsService(config)
        val statsService = StatisticsService(financialService)
        
        val date = LocalDate.of(2025, 1, 1)
        val site = "TestSite"
        
        // Demand: 1 case, 08:00 - 10:00 = 120 minutes = 2.0 hours
        // CaseValidator will add 10m start and 10m end padding -> durationMinutes = 140
        val caseRow = createRow(1, site, date, "OR 1", "08:00", "10:00")
        assertEquals(140L, caseRow.durationMinutes) // Verification of padding
        
        val validRows = listOf(caseRow)
        // Supply: 1 CRNA, 1 hour (minimal supply to ensure it's included in totals)
        val crnaRows = listOf(CrnaRecord(region = "Standalone: $site", name = "CRNA1", date = date, durationHours = 1.0))
        
        val result = statsService.calculateGlobalStaffing(validRows, crnaRows, config)
        
        // requiredCrnaHours should be 2.0 (120/60), NOT 2.33 (140/60)
        assertEquals(2.0, result["requiredCrnaHours"] as Double, 0.01)
        
        propsFile.delete()
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
        // Use CaseValidator to ensure padding logic is applied correctly
        val validator = org.heailth.reader.CaseValidator(ConfigLoader("src/main/resources/app.properties"))
        return validator.validate(id, record, null)
    }
}
