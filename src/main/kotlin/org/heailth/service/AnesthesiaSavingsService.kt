package org.heailth.service

import org.heailth.model.AnesthesiaSavings
import org.heailth.model.ParsedRow
import java.time.Duration
import java.time.LocalTime

class AnesthesiaSavingsService {

    fun calculateSavings(
        originalCases: List<ParsedRow>,
        optimizedCases: List<ParsedRow>,
        actualRooms: Set<String>,
        method1Enabled: Boolean = true,
        method2Enabled: Boolean = true,
        method3Enabled: Boolean = true,
        anesPaddingMinutes: Int = 0,
        thresholdMinutes: Int = 480
    ): AnesthesiaSavings {
        if (originalCases.isEmpty()) return AnesthesiaSavings(0, 0, 0, 0)

        // 1. Find all whole providers that can be eliminated via absorption
        val eliminableProviders = findEliminableProviders(optimizedCases, anesPaddingMinutes)
        
        // 2. Method 1: Savings from providers dedicated to rooms that were closed
        val optimizedRoomNames = optimizedCases.mapNotNull { it.assignedRoomName }.toSet()
        val originalRoomsInOptimized = optimizedCases.map { "${it.record!!.site} - ${it.record!!.orName}" }.toSet()
        val eliminatedRooms = actualRooms.filter { it !in originalRoomsInOptimized }.toSet()
        
        val originalProviderMap = originalCases.filter { it.isValid }.groupBy { it.record?.anesthesiologistName }
        
        val m1 = originalProviderMap.count { (name, cases) ->
            if (name == null || name !in eliminableProviders) return@count false
            val roomsWorked = cases.map { "${it.record!!.site} - ${it.record!!.orName}" }.toSet()
            roomsWorked.isNotEmpty() && roomsWorked.all { it in eliminatedRooms }
        }

        // 3. Method 3: Any other eliminable whole providers (Absorption)
        val m3 = (eliminableProviders.size - m1).coerceAtLeast(0)

        // 4. Method 2: FTE Efficiency (Hours reduction from active room windows)
        val m2 = calculateMethod2(originalCases, optimizedCases, anesPaddingMinutes, m1 + m3, thresholdMinutes)

        val total = (if (method1Enabled) m1 else 0) + 
                    (if (method2Enabled) m2 else 0) + 
                    (if (method3Enabled) m3 else 0)
                    
        return AnesthesiaSavings(m1, m2, m3, total)
    }

    private fun findEliminableProviders(
        optimizedCases: List<ParsedRow>,
        anesPaddingMinutes: Int
    ): Set<String> {
        val providers = optimizedCases.filter { it.isValid }
            .groupBy { it.record?.anesthesiologistName }
            .filterKeys { it != null && it.isNotBlank() }
            .mapValues { it.value.sortedBy { c -> c.anesthesiaStart } }

        if (providers.size <= 1) return emptySet()

        val sortedProviders = providers.entries.sortedBy { it.value.size }
        val eliminatedProviders = mutableSetOf<String>()

        for (candidate in sortedProviders) {
            val candidateCases = candidate.value
            val otherProviders = providers.filter { it.key != candidate.key && it.key !in eliminatedProviders }
            
            var canAbsorbAll = true
            for (case in candidateCases) {
                var foundAbsorber = false
                val start = getPaddedStart(case, anesPaddingMinutes) ?: continue
                val end = getPaddedEnd(case, anesPaddingMinutes) ?: continue

                for (other in otherProviders) {
                    if (isProviderFreeDuring(other.value, start, end, anesPaddingMinutes)) {
                        foundAbsorber = true
                        break
                    }
                }
                if (!foundAbsorber) {
                    canAbsorbAll = false
                    break
                }
            }

            if (canAbsorbAll) {
                eliminatedProviders.add(candidate.key!!)
            }
        }
        return eliminatedProviders
    }

    private fun calculateMethod1(
        originalCases: List<ParsedRow>,
        optimizedCases: List<ParsedRow>,
        actualRooms: Set<String>
    ): Int {
        // This is now replaced by logic inside calculateSavings
        return 0
    }

    private fun calculateMethod2(
        originalCases: List<ParsedRow>,
        optimizedCases: List<ParsedRow>,
        anesPaddingMinutes: Int,
        alreadySavedWholePeople: Int,
        thresholdMinutes: Int = 480
    ): Int {
        val activeProviders = originalCases.filter { it.isValid }
            .mapNotNull { it.record?.anesthesiologistName }
            .filter { it.isNotBlank() }
            .toSet()
        
        if (activeProviders.isEmpty()) return 0

        // Calculate total active room window time reduction
        val windowMinutesBefore = calculateTotalRoomWindowMinutes(originalCases, false, anesPaddingMinutes)
        val windowMinutesAfter = calculateTotalRoomWindowMinutes(optimizedCases, true, anesPaddingMinutes)
        
        val savedMinutes = (windowMinutesBefore - windowMinutesAfter).coerceAtLeast(0)
        
        // Convert to FTEs
        if (thresholdMinutes <= 0) return 0
        val totalFtesSaved = (savedMinutes / thresholdMinutes).toInt()
        
        // Deduplicate: If we already saved whole people, those people's windows were part of the reduction.
        // We only want "additional" FTEs from efficiency.
        return (totalFtesSaved - alreadySavedWholePeople).coerceAtLeast(0)
            .coerceAtMost(activeProviders.size - alreadySavedWholePeople)
    }

    private fun calculateMethod3(
        optimizedCases: List<ParsedRow>,
        alreadySaved: Int,
        anesPaddingMinutes: Int
    ): Int {
        // This is now replaced by logic inside calculateSavings
        return 0
    }

    private fun calculateTotalRoomWindowMinutes(cases: List<ParsedRow>, useOptimizedRoom: Boolean, anesPaddingMinutes: Int): Long {
        val groups = if (useOptimizedRoom) {
            cases.filter { it.isValid }.groupBy { it.assignedRoomName ?: "Unknown" }
        } else {
            // Use actual site and room name from record to ensure stability regardless of optimizer modifications
            cases.filter { it.isValid }.groupBy { "${it.record?.site ?: "Unknown"} - ${it.record?.orName ?: "Unknown"}" }
        }
        
        val total = groups.values.sumOf { groupCases ->
            val start = groupCases.mapNotNull { getPaddedStart(it, anesPaddingMinutes) }.minOrNull()
            val end = groupCases.mapNotNull { getPaddedEnd(it, anesPaddingMinutes) }.maxOrNull()
            if (start != null && end != null) {
                Duration.between(start, end).toMinutes()
            } else 0L
        }
        return total
    }

    private fun calculateTotalCoverageMinutes(cases: List<ParsedRow>, anesPaddingMinutes: Int): Long {
        val providers = cases.filter { it.isValid }
            .groupBy { it.record?.anesthesiologistName }
            .filterKeys { it != null && it.isNotBlank() }

        return providers.values.sumOf { providerCases ->
            val start = providerCases.mapNotNull { getPaddedStart(it, anesPaddingMinutes) }.minOrNull()
            val end = providerCases.mapNotNull { getPaddedEnd(it, anesPaddingMinutes) }.maxOrNull()
            if (start != null && end != null) {
                // Deduplicate: If multiple cases for same provider overlap, the duration is simply (maxEnd - minStart)
                Duration.between(start, end).toMinutes()
            } else 0L
        }
    }

    private fun isProviderFreeDuring(providerCases: List<ParsedRow>, start: LocalTime, end: LocalTime, anesPaddingMinutes: Int): Boolean {
        return providerCases.none { existing ->
            val exStart = getPaddedStart(existing, anesPaddingMinutes) ?: return@none false
            val exEnd = getPaddedEnd(existing, anesPaddingMinutes) ?: return@none false
            exStart.isBefore(end) && start.isBefore(exEnd)
        }
    }

    private fun getPaddedStart(case: ParsedRow, padding: Int): LocalTime? {
        val base = case.anesthesiaStart ?: return null
        return if (padding > 0) base.minusMinutes(padding / 2L) else base
    }

    private fun getPaddedEnd(case: ParsedRow, padding: Int): LocalTime? {
        val base = case.anesthesiaEnd ?: return null
        return if (padding > 0) base.plusMinutes(padding / 2L) else base
    }
}