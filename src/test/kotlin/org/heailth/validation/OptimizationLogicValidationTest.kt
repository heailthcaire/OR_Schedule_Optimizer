package org.heailth.validation

import org.heailth.model.CaseRecord
import org.heailth.model.ParsedRow
import org.heailth.optimizer.BinPackingOptimizer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Process & Optimization Logic Verification")
open class OptimizationLogicValidationTest : BaseValidationTest() {

    @Test
    @DisplayName("Validate Implicit Safety Rule")
    fun testImplicitSafetyRule() {
        println("[DEBUG_LOG] --- Process Logic: Implicit Safety Rule ---")
        val optimizer = BinPackingOptimizer(5)
        
        // Case A: 08:00 - 10:00 (In/Out)
        // Case B: 10:10 - 12:00 (In/Out)
        // Padding: 10m -> A ends 10:10, B starts 10:00. Overlap!
        // But they were in same room originally.
        val pInA = LocalTime.of(8, 0)
        val pOutA = LocalTime.of(10, 0)
        val pInB = LocalTime.of(10, 10)
        val pOutB = LocalTime.of(12, 0)
        
        val recordA = CaseRecord("A", "Site", LocalDate.now(), "OR 1", "Surg", "Proc", pInA, pOutA, pInA, pOutA, "Dr. Anes")
        val recordB = CaseRecord("B", "Site", LocalDate.now(), "OR 1", "Surg", "Proc", pInB, pOutB, pInB, pOutB, "Dr. Anes")
        
        val rowA = ParsedRow(1, recordA, pInA.minusMinutes(10), pOutA.plusMinutes(10), pInA.minusMinutes(10), pOutA.plusMinutes(10), 140, true, null, false)
        val rowB = ParsedRow(2, recordB, pInB.minusMinutes(10), pOutB.plusMinutes(10), pInB.minusMinutes(10), pOutB.plusMinutes(10), 130, true, null, false)
        
        println("[DEBUG_LOG] Testing implicit safety rule: Cases A (8:00-10:00) and B (10:10-12:00) with 10m padding.")
        val result = optimizer.solve(listOf(rowA, rowB), 480, setOf("OR 1"), "Site")
        println("[DEBUG_LOG] Implicit Safety Result: Rooms Used = ${result.roomsUsed}")
        assertEquals(1, result.roomsUsed, "Implicit safety rule failed: cases should be in 1 room")
    }

    @Test
    @DisplayName("Validate Capacity Constraint")
    fun testCapacityConstraint() {
        println("[DEBUG_LOG] --- Process Logic: Capacity Constraint ---")
        val optimizer = BinPackingOptimizer(5)
        
        val pInA = LocalTime.of(8, 0)
        val pOutA = LocalTime.of(10, 0)
        val recordA = CaseRecord("A", "Site", LocalDate.now(), "OR 1", "Surg", "Proc", pInA, pOutA, pInA, pOutA, "Dr. Anes")
        val rowA = ParsedRow(1, recordA, pInA.minusMinutes(10), pOutA.plusMinutes(10), pInA.minusMinutes(10), pOutA.plusMinutes(10), 400, true, null, false)
        
        val pInB = LocalTime.of(11, 0)
        val pOutB = LocalTime.of(12, 0)
        val recordB = CaseRecord("B", "Site", LocalDate.now(), "OR 1", "Surg", "Proc", pInB, pOutB, pInB, pOutB, "Dr. Anes")
        val rowB = ParsedRow(2, recordB, pInB.minusMinutes(10), pOutB.plusMinutes(10), pInB.minusMinutes(10), pOutB.plusMinutes(10), 100, true, null, false)
        
        println("[DEBUG_LOG] Testing capacity constraint: 400m + 100m in 480m capacity.")
        val resultCapacity = optimizer.solve(listOf(rowA, rowB), 480, setOf("OR 1", "OR 2"), "Site")
        println("[DEBUG_LOG] Capacity Constraint Result: Rooms Used = ${resultCapacity.roomsUsed}")
        assertEquals(2, resultCapacity.roomsUsed, "Capacity constraint failed: 400 + 100 > 480")
        
        println("[DEBUG_LOG] Process Logic Passed.")
    }
}
