package org.heailth.optimizer

import org.heailth.model.CaseRecord
import org.heailth.model.ParsedRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class BinPackingOptimizerTest {

    @Test
    fun testNewPaddingRule() {
        // Case 1: 08:00 - 10:00, with 15m padding: 07:45 - 10:15
        val c1 = createCase("C1", "Room 1", "08:00", "10:00", 15)
        
        // Case 2: 10:10 - 12:00, with 15m padding: 09:55 - 12:15
        // These overlap because of padding (09:55 < 10:15)
        val c2 = createCase("C2", "Room 1", "10:10", "12:00", 15)

        val optimizer = BinPackingOptimizer(5)
        val cases = listOf(c1, c2)
        val actualRooms = setOf("Room 1")
        
        // Under the NEW RULE, since they were in the same room (Room 1) and didn't originally overlap
        // (10:00 <= 10:10), they SHOULD be allowed in the same optimized room.
        val result = optimizer.solve(cases, 480, actualRooms, "Site A")
        
        assertEquals(1, result.roomsUsed, "Should only use 1 room despite padding overlap because they were in the same room originally")
        assertEquals(0, result.roomsSaved)
    }

    @Test
    fun testPaddingRuleDifferentRooms() {
        // Case 1: 08:00 - 10:00, with 15m padding: 07:45 - 10:15
        val c1 = createCase("C1", "Room 1", "08:00", "10:00", 15)
        
        // Case 2: 10:10 - 12:00, with 15m padding: 09:55 - 12:15
        // These overlap because of padding (09:55 < 10:15)
        // BUT they were in different rooms originally
        val c2 = createCase("C2", "Room 2", "10:10", "12:00", 15)

        val optimizer = BinPackingOptimizer(5)
        val cases = listOf(c1, c2)
        val actualRooms = setOf("Room 1", "Room 2")
        
        // Under the NEW RULE, since they were in different rooms, the padding MUST be respected.
        // They should be in separate rooms.
        val result = optimizer.solve(cases, 480, actualRooms, "Site A")
        
        assertEquals(2, result.roomsUsed, "Should use 2 rooms because they overlap after padding and were originally in different rooms")
    }

    @Test
    fun testOccupancyBasedStacking() {
        // CFO Evolution Phase 1: Verify that stacking is now based on occupancy times.
        // Patient In/Out are the primary boundaries.
        
        // Case 1: In 08:00, Out 10:00. Padding 10m -> Occupancy: 07:50 - 10:10
        val c1 = createCaseWithOccupancy("C1", "Room 1", "08:00", "10:00", 10)
        
        // Case 2: In 10:20, Out 12:00. Padding 10m -> Occupancy: 10:10 - 12:10
        // These do NOT overlap (ends 10:10, starts 10:10)
        val c2 = createCaseWithOccupancy("C2", "Room 2", "10:20", "12:00", 10)
        
        val optimizer = BinPackingOptimizer(5)
        val result = optimizer.solve(listOf(c1, c2), 480, setOf("Room 1", "Room 2"), "Site A")
        
        assertEquals(1, result.roomsUsed, "Should consolidate to 1 room because occupancy buffers do not overlap")
        assertEquals(1, result.roomsSaved)
    }

    private fun createCase(id: String, room: String, start: String, end: String, pad: Long): ParsedRow {
        // Legacy helper for anesthesia-based tests, keeping for rule verification
        val s = LocalTime.parse(start)
        val e = LocalTime.parse(end)
        // Patient In/Out same as anesthesia for legacy tests to pass with new logic
        val pIn = s
        val pOut = e
        val record = CaseRecord(id, "Site A", LocalDate.now(), room, "Surgeon", "Proc", s, e, pIn, pOut, "Dr. Anes")
        val effS = s.minusMinutes(pad)
        val effE = e.plusMinutes(pad)
        // New logic uses occupancy for duration and conflicts
        val occS = pIn.minusMinutes(pad)
        val occE = pOut.plusMinutes(pad)
        val dur = java.time.Duration.between(occS, occE).toMinutes()
        return ParsedRow(1, record, effS, effE, occS, occE, dur, true, null, false)
    }

    private fun createCaseWithOccupancy(id: String, room: String, pInStr: String, pOutStr: String, pad: Long): ParsedRow {
        val pIn = LocalTime.parse(pInStr)
        val pOut = LocalTime.parse(pOutStr)
        // Anesthesia times (clinical) are metadata now, setting them to same as occupancy for simplicity
        val s = pIn
        val e = pOut
        val record = CaseRecord(id, "Site A", LocalDate.now(), room, "Surgeon", "Proc", s, e, pIn, pOut, "Dr. Anes")
        
        val effS = s.minusMinutes(pad)
        val effE = e.plusMinutes(pad)
        val occS = pIn.minusMinutes(pad)
        val occE = pOut.plusMinutes(pad)
        val dur = java.time.Duration.between(occS, occE).toMinutes()
        return ParsedRow(1, record, effS, effE, occS, occE, dur, true, null, false)
    }
}
