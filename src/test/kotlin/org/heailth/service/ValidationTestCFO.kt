package org.heailth.service

import org.heailth.config.ConfigLoader
import org.heailth.model.*
import org.heailth.validation.BaseValidationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime

class ValidationTestCFO : BaseValidationTest() {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun testStrategicPromptGeneration() {
        val configPath = createTempConfig()
        val config = ConfigLoader(configPath)
        
        val date = LocalDate.now()
        
        // Setup some mock data
        val site1 = "Site 1"
        val site2 = "Site 2"
        
        // Case at site 1 (Hub)
        val case1 = createParsedRow(site1, date, "Room A", "08:00", "10:00")
        // Case at site 2 (Satellite)
        val case2 = createParsedRow(site2, date, "Room B", "09:00", "11:00")
        
        val dayResult1 = DayGroupResult(
            GroupKey(site1, date, "REGIONAL_HUB"),
            listOf(case1),
            emptyList(),
            1, 1, 0, "OK"
        )
        
        val dayResult2 = DayGroupResult(
            GroupKey(site2, date, "REGIONAL_HUB"),
            listOf(case2),
            emptyList(),
            1, 1, 0, "OK"
        )
        
        val dayResults = listOf(dayResult1, dayResult2)
        
        val financialService = FinancialMetricsService(config)
        val statsService = StatisticsService(financialService)
        
        val allRows = listOf(case1, case2)
        val siteStats = statsService.calculateSiteStatistics(allRows)
        val globalStats = statsService.calculateGlobalStatistics(allRows, siteStats)
        
        val promptService = StrategicPromptService()
        val prompt = promptService.generateStrategicPrompt(globalStats, siteStats, dayResults, config)
        
        // Assertions
        assertTrue(prompt.contains("### COPY-PASTE PROMPT START"))
        assertTrue(prompt.contains("### STRATEGIC DATA PAYLOAD"))
        assertTrue(prompt.contains("Site 1"))
        assertTrue(prompt.contains("Site 2"))
        assertTrue(prompt.contains("REGIONAL_HUB"))
        assertTrue(prompt.contains("Absorption Potential"))
        assertTrue(prompt.contains("### COPY-PASTE PROMPT END"))
        
        println("Generated Prompt Sample:\n${prompt.take(500)}...")
    }

    @Test
    fun testAbsorptionLogicInPrompt() {
        val configPath = createTempConfig()
        val config = ConfigLoader(configPath)
        val date = LocalDate.now()
        
        // Hub has plenty of room
        val hubCase = createParsedRow("Site 1", date, "Room A", "08:00", "09:00")
        // Satellite has small volume that fits in Hub's unused minutes
        val satCase = createParsedRow("Site 2", date, "Room B", "10:00", "11:00")
        
        val hubResult = DayGroupResult(
            GroupKey("Site 1", date, "REGIONAL_HUB"),
            listOf(hubCase),
            emptyList(),
            1, 1, 0, "OK"
        )
        val satResult = DayGroupResult(
            GroupKey("Site 2", date, "REGIONAL_HUB"),
            listOf(satCase),
            emptyList(),
            1, 1, 0, "OK"
        )
        
        val dayResults = listOf(hubResult, satResult)
        
        val financialService = FinancialMetricsService(config)
        val statsService = StatisticsService(financialService)
        val siteStats = statsService.calculateSiteStatistics(listOf(hubCase, satCase))
        val globalStats = statsService.calculateGlobalStatistics(listOf(hubCase, satCase), siteStats)
        
        val promptService = StrategicPromptService()
        val prompt = promptService.generateStrategicPrompt(globalStats, siteStats, dayResults, config)
        
        // Since sat volume (60 min) fits in Hub unused (480 - 60 = 420 min)
        assertTrue(prompt.contains("can be absorbed by the Hub 100% of the time"))
        assertTrue(prompt.contains("High Priority: Move all volume to Hub"))
    }

    @Test
    fun testVerificationSamplePreparation() {
        val configPath = createTempConfig()
        val config = ConfigLoader(configPath)
        val date = LocalDate.now()
        
        // Site-day with 1 room saved
        val case1 = createParsedRow("Site 1", date, "Room 1", "08:00", "11:00") // 3 hours
        val case2 = createParsedRow("Site 1", date, "Room 2", "12:00", "13:00") // 1 hour
        
        // Simulate optimization where both cases are moved to Consolidated Room A
        case1.assignedRoomName = "Site 1 - Consolidated Room A"
        case2.assignedRoomName = "Site 1 - Consolidated Room A"
        
        val dayResult = DayGroupResult(
            GroupKey("Site 1", date),
            listOf(case1, case2),
            emptyList(),
            2, 1, 1, "OK"
        )
        
        val service = ReportDataService()
        val context = service.prepareContext(
            config, emptyList(), listOf(dayResult), emptyList(),
            1, 2, date.toString(), date.toString(), 1, 2, emptyList(),
            StatisticsResult("Global", emptyMap(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, emptyMap(), emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0),
            0, AnesthesiaSavings(0, 0, 0, 0)
        )
        
        val sample = context["verificationSample"] as CFOVerificationSample?
        assertNotNull(sample)
        assertEquals("Site 1", sample?.siteName)
        assertEquals(2, sample?.baselineRooms)
        assertEquals(1, sample?.roomsSaved)
        
        // Room 2 has less volume, so it should be the one eliminated
        val eliminated = sample?.roomConsolidationDetails?.find { it.wasEliminated }
        assertNotNull(eliminated)
        assertEquals("Room 2", eliminated?.originalRoomName)
    }

    @Test
    fun testDecoupledFinancialCalculation() {
        val configPath = createTempConfig()
        val config = ConfigLoader(configPath)
        val date = LocalDate.now()

        // 2 OR rooms saved, but only 1 Anesthesia provider saved (Method 1)
        // Room 1: 3 hours, Room 2: 1 hour -> Move both to Room 3.
        // Room 1 and Room 2 are eliminated (2 OR-days saved).
        // But if Room 1 had Dr. A and Room 2 had Dr. A, then only 0 providers saved? No.
        // Let's say Room 1 had Dr. A, Room 2 had Dr. B.
        // Dr. A moved to Room 3. Dr. B is saved. (1 Anes saved).
        
        val case1 = createParsedRow("Site 1", date, "Room 1", "08:00", "11:00", "Dr. Anes")
        val case2 = createParsedRow("Site 1", date, "Room 2", "12:00", "13:00", "Dr. B")
        
        // Simulation: Move both to Room 3. Room 1 & 2 are "saved".
        // Provider Dr. Anes moves to Room 3. Dr. B is out of work.
        val anesSavings = AnesthesiaSavings(
            method1Savings = 1, // Dr. B saved
            method2Savings = 0,
            method3Savings = 0,
            totalAnesthesiaSavings = 1
        )
        
        val dayResult = DayGroupResult(
            GroupKey("Site 1", date),
            listOf(case1, case2),
            emptyList(),
            2, 1, 2, "OK", anesSavings
        )

        val service = ReportDataService()
        // OR Rate = 1500, Anes Rate = 3500 (from createTempConfig)
        // OR Savings = 2 * 1500 * 12 = 36,000
        // Anes Savings = 1 * 3500 * 12 = 42,000
        // Total = 78,000
        
        val context = service.prepareContext(
            config, emptyList(), listOf(dayResult), emptyList(),
            2, 4, date.toString(), date.toString(), 1, 4, emptyList(),
            StatisticsResult("Global", emptyMap(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, emptyMap(), emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0),
            2, anesSavings
        )
        
        val annualOpportunity = context["annualOpportunity"] as Double
        assertEquals(78000.0, annualOpportunity, 0.1)
    }

    private fun createTempConfig(): String {
        val configFile = File(tempDir.toFile(), "app.properties")
        configFile.writeText("""
            csv.path = dummy.csv
            report.path = report.html
            id.column = Case_ID
            date.column = Date
            site.column = Site
            or.column = OR_Room
            surgeon.column = Surgeon
            procedure.column = Procedure
            csv.required.columns = Case_ID,Date,Site,OR_Room,Surgeon,Procedure,Anesthesia_Start,Anesthesia_End,Patient_In,Patient_Out
            anesthesia.start.time.column = Anesthesia_Start
            anesthesia.end.time.column = Anesthesia_End
            or.occupancy.start.time.column = Patient_In
            or.occupancy.end.time.column = Patient_Out
            bin.capacity.minutes = 480
            date.format = yyyy-MM-dd
            time.format = HH:mm
            cost.per.or.day = 1500
            financials.include.anesthesiologist = true
            financials.anesthesiologist.rate = 3500
            cluster.REGIONAL_HUB = Site 1, Site 2
            modeling.anes.blocks.saved = true
            modeling.anes.fte.efficiency = true
            modeling.anes.absorption = true
            reporting.generate.ai.prompt = true
        """.trimIndent())
        return configFile.absolutePath
    }

    private fun createParsedRow(site: String, date: LocalDate, room: String, start: String, end: String, provider: String = "Dr. Anes"): ParsedRow {
        val s = LocalTime.parse(start)
        val e = LocalTime.parse(end)
        val record = CaseRecord("C1", site, date, room, "Surgeon", "Proc", s, e, s, e, provider)
        val dur = java.time.Duration.between(s, e).toMinutes()
        return ParsedRow(1, record, s, e, s, e, dur, true, null, false)
    }
}
