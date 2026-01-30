package org.heailth.optimizer

import org.heailth.model.CaseRecord
import org.heailth.model.ParsedRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class ClusterOptimizationTest {

    @Test
    fun testClusterConsolidation() {
        // Site 1 (Hub) has 1 room
        // Site 2 (Satellite) has 1 room
        // Each has one 2-hour case. They should be consolidated into Site 1.

        val c1 = createCaseAtSite("C1", "Site 1", "Room A", "08:00", "10:00")
        val c2 = createCaseAtSite("C2", "Site 2", "Room B", "11:00", "13:00")

        val optimizer = BinPackingOptimizer(5)
        val cases = listOf(c1, c2)
        val actualRooms = setOf("Site 1 - Room A", "Site 2 - Room B")
        val clusterSites = listOf("Site 1", "Site 2")

        val result = optimizer.solve(cases, 480, actualRooms, "Site 1", clusterSites)

        assertEquals(1, result.roomsUsed, "Should consolidate two non-overlapping cases from different sites into 1 room")
        assertEquals(1, result.roomsSaved)
        
        // Check if assigned to Site 1 (Hub)
        assertTrue(c1.assignedRoomName!!.startsWith("Site 1"), "Case 1 should be in Site 1. Got: ${c1.assignedRoomName}")
        assertTrue(c2.assignedRoomName!!.startsWith("Site 1"), "Case 2 should be moved to Site 1. Got: ${c2.assignedRoomName}")
    }

    @Test
    fun testClusterPreferenceForHub() {
        // Site 1 has 1 room, Site 2 has 1 room.
        // If we have cases that can only fit in 1 room together, they should go to Site 1 if Site 1 is the 'hub' (first in cluster list).
        
        val c1 = createCaseAtSite("C1", "Site 2", "Room B", "08:00", "10:00")
        
        val optimizer = BinPackingOptimizer(5)
        val cases = listOf(c1)
        val actualRooms = setOf("Site 1 - Room A", "Site 2 - Room B")
        val clusterSites = listOf("Site 1", "Site 2") // Site 1 is hub

        val result = optimizer.solve(cases, 480, actualRooms, "Site 1", clusterSites)

        assertEquals(1, result.roomsUsed)
        assertTrue(c1.assignedRoomName!!.startsWith("Site 1"), "Should have moved case from Site 2 to Site 1 (Hub). Got: ${c1.assignedRoomName}")
    }

    private fun createCaseAtSite(id: String, site: String, room: String, start: String, end: String): ParsedRow {
        val s = LocalTime.parse(start)
        val e = LocalTime.parse(end)
        val record = CaseRecord(id, site, LocalDate.now(), room, "Surgeon", "Proc", s, e, s, e, "Dr. Anes")
        val dur = java.time.Duration.between(s, e).toMinutes()
        return ParsedRow(1, record, s, e, s, e, dur, true, null, false)
    }
}
