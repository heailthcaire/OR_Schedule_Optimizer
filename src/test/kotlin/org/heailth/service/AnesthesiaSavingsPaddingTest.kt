package org.heailth.service

import org.heailth.model.CaseRecord
import org.heailth.model.ParsedRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class AnesthesiaSavingsPaddingTest {

    private val service = AnesthesiaSavingsService()

    @Test
    fun testPaddingAffectsAbsorption() {
        val date = LocalDate.now()
        
        // Dr. A has two cases with a gap
        val caseA1 = createCase("Dr. A", "08:00", "10:00", date)
        val caseA2 = createCase("Dr. A", "12:00", "14:00", date)
        
        // Dr. B has one case that fits in Dr. A's gap if no padding
        // Gap is 10:00 to 12:00 (120 mins)
        // Case B is 10:30 to 11:30 (60 mins)
        val caseB = createCase("Dr. B", "10:30", "11:30", date)
        
        val cases = listOf(caseA1, caseA2, caseB)
        
        // Scenario 1: No padding -> Dr. B should be absorbed
        val savingsNoPad = service.calculateSavings(cases, cases, setOf("Room 1"), 
            method1Enabled = false, method2Enabled = false, method3Enabled = true, anesPaddingMinutes = 0)
        
        assertEquals(1, savingsNoPad.method3Savings, "Dr. B should be absorbed with no padding")

        // Scenario 2: High padding -> Dr. B should NOT be absorbed
        // If padding is 120 mins (60 before, 60 after):
        // Case A1 end: 10:00 + 60 = 11:00
        // Case A2 start: 12:00 - 60 = 11:00
        // New gap: 11:00 to 11:00 (0 mins)
        // Case B (padded 60 before, 60 after): 09:30 to 12:30
        // Dr. B clearly cannot fit.
        val savingsWithPad = service.calculateSavings(cases, cases, setOf("Room 1"), 
            method1Enabled = false, method2Enabled = false, method3Enabled = true, anesPaddingMinutes = 120)
            
        assertEquals(0, savingsWithPad.method3Savings, "Dr. B should NOT be absorbed with 120min padding")
    }

    @Test
    fun testPaddingAffectsFTEEfficiency() {
        val date = LocalDate.now()
        
        // Scenario to test Method 2 (FTE Efficiency) reduction
        // Everyone is busy 08:00-10:00 so no one can be absorbed.
        
        val cA1 = createCase("Dr. A", "08:00", "10:00", date, orName = "Room 1")
        val cA2 = createCase("Dr. A", "15:00", "16:00", date, orName = "Room 1")
        val cB = createCase("Dr. B", "08:00", "15:00", date, orName = "Room 2")
        
        val cC1 = createCase("Dr. C", "08:00", "10:00", date, orName = "Room 3")
        val cC2 = createCase("Dr. C", "15:00", "16:00", date, orName = "Room 3")
        val cD = createCase("Dr. D", "08:00", "15:00", date, orName = "Room 4")

        val cE1 = createCase("Dr. E", "08:00", "10:00", date, orName = "Room 5")
        val cE2 = createCase("Dr. E", "14:00", "15:00", date, orName = "Room 5")
        val cF = createCase("Dr. F", "08:00", "14:00", date, orName = "Room 6")
        
        val cA1Opt = cA1.copy(); cA1Opt.assignedRoomName = "Site - Room 1"
        val cA2Opt = cA2.copy(); cA2Opt.assignedRoomName = "Site - Room 2"
        val cBOpt = cB.copy(); cBOpt.assignedRoomName = "Site - Room 2"
        
        val cC1Opt = cC1.copy(); cC1Opt.assignedRoomName = "Site - Room 3"
        val cC2Opt = cC2.copy(); cC2Opt.assignedRoomName = "Site - Room 4"
        val cDOpt = cD.copy(); cDOpt.assignedRoomName = "Site - Room 4"
        
        val cE1Opt = cE1.copy(); cE1Opt.assignedRoomName = "Site - Room 5"
        val cE2Opt = cE2.copy(); cE2Opt.assignedRoomName = "Site - Room 6"
        val cFOpt = cF.copy(); cFOpt.assignedRoomName = "Site - Room 6"
        
        val allOriginal = listOf(cA1, cA2, cB, cC1, cC2, cD, cE1, cE2, cF)
        val allOptimized = listOf(cA1Opt, cA2Opt, cBOpt, cC1Opt, cC2Opt, cDOpt, cE1Opt, cE2Opt, cFOpt)
        val actualRooms = setOf("Site - Room 1", "Site - Room 2", "Site - Room 3", "Site - Room 4", "Site - Room 5", "Site - Room 6")
        
        val savingsNoPad = service.calculateSavings(allOriginal, allOptimized, actualRooms, 
            method1Enabled = true, method2Enabled = true, method3Enabled = true, anesPaddingMinutes = 0)
            
        assertEquals(0, savingsNoPad.method1Savings, "No one should be eliminated")
        assertEquals(0, savingsNoPad.method3Savings, "No one should be eliminated")
        assertEquals(1, savingsNoPad.method2Savings, "Should have 1 FTE saved via window reduction")

        val savingsWithPad = service.calculateSavings(allOriginal, allOptimized, actualRooms,
            method1Enabled = true, method2Enabled = true, method3Enabled = true, anesPaddingMinutes = 60)
        
        assertTrue(savingsWithPad.method2Savings >= 1)
    }

    @Test
    fun testMethod1Strictness() {
        val date = LocalDate.now()
        
        // Dr. A worked ONLY in Room 1
        val caseA = createCase("Dr. A", "08:00", "10:00", date, orName = "Room 1")
        
        // Optimized: Room 1 is eliminated, Dr. A's case is moved to Room 2
        val caseAOpt = createCase("Dr. A", "08:00", "10:00", date, orName = "Room 1")
        caseAOpt.assignedRoomName = "Room 2" // Optimized Room
        
        val actualRooms = setOf("Room 1", "Room 2")
        
        // Method 1 should be 0 because Dr. A still has a case in the optimized schedule
        val savings = service.calculateSavings(listOf(caseA), listOf(caseAOpt), actualRooms, 
            method1Enabled = true, method2Enabled = false, method3Enabled = false)
            
        assertEquals(0, savings.method1Savings, "Dr. A should not be saved if they are just moved to another room")
    }

    private fun createCase(provider: String, start: String, end: String, date: LocalDate, orName: String = "Room"): ParsedRow {
        val s = LocalTime.parse(start)
        val e = LocalTime.parse(end)
        val record = CaseRecord("C1", "Site", date, orName, "Surgeon", "Proc", s, e, s, e, provider)
        val dur = java.time.Duration.between(s, e).toMinutes()
        // Using s and e for BOTH anesthesia and occupancy for simplicity in this test
        return ParsedRow(1, record, s, e, s, e, dur, true, null, false)
    }
}
