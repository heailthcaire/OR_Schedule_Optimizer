package org.heailth.service

import org.heailth.config.ConfigLoader
import org.heailth.model.DayGroupResult
import org.heailth.model.GroupKey
import org.heailth.model.ParsedRow
import org.heailth.optimizer.BinPackingOptimizer
import org.slf4j.LoggerFactory
import java.util.stream.Collectors
import kotlin.math.ceil

class OptimizationService {
    private val anesthesiaService = AnesthesiaSavingsService()

    fun runOptimization(grouped: Map<GroupKey, List<ParsedRow>>, config: ConfigLoader): List<DayGroupResult> {
        val binCapacity = config.getBinCapacity()
        val timeout = config.getOptimizationTimeout()
        val skipSingle = config.isSkipSingleOr()

        // Using parallelStream() for heavy optimization workload as requested
        return grouped.entries.parallelStream()
            .map { entry -> optimizeDay(entry.key, entry.value, binCapacity, timeout, skipSingle, config) }
            .collect(Collectors.toList())
            .sortedWith(compareBy<DayGroupResult> { it.groupKey.site }.thenBy { it.groupKey.date })
    }

    private fun optimizeDay(key: GroupKey, cases: List<ParsedRow>, binCapacity: Int, timeout: Int, skipSingle: Boolean, config: ConfigLoader): DayGroupResult {
        val actualRooms = cases.map { "${it.record!!.site} - ${it.record!!.orName}" }.toSet()
        val actualRoomsUsed = actualRooms.size

        val skip = skipSingle && actualRoomsUsed <= 1
        val offending = cases.filter { it.durationMinutes > binCapacity }

        return when {
            offending.isNotEmpty() -> {
                logger.debug("Site-day {} infeasible: {} cases exceed capacity {}", key, offending.size, binCapacity)
                DayGroupResult(key, cases, emptyList(), actualRoomsUsed, null, 0, "INFEASIBLE")
            }
            skip -> {
                applyDefaultMapping(cases)
                DayGroupResult(key, cases, emptyList(), actualRoomsUsed, actualRoomsUsed, 0, "SKIPPED")
            }
            else -> {
                logger.debug("Optimizing site-day {}: {} cases, {} rooms, capacity {}", key, cases.size, actualRoomsUsed, binCapacity)
                val optimizer = BinPackingOptimizer(timeout)
                val clusterSites = if (key.clusterName != null) config.clusters[key.clusterName] else null
                
                // Heuristic check: Removed high utilization skip to maximize savings discovery
                val result = optimizer.solve(cases, binCapacity, actualRooms, key.site, clusterSites)
                
                if (result.status == "OR_TOOLS_SAT" || result.status == "FFD_HEURISTIC" || result.status == "FFD_OPTIMAL") {
                    val finalOptimizedRooms = Math.min(actualRoomsUsed, result.roomsUsed)
                    val anesthesiaSavings = anesthesiaService.calculateSavings(
                        cases, cases, actualRooms,
                        true, true, true, // Always calculate all methods, filtering happens in UI or Report
                        config.getAnesPaddingMinutes(),
                        binCapacity
                    )
                    DayGroupResult(key, cases, emptyList(), actualRoomsUsed, finalOptimizedRooms, 
                                   (actualRoomsUsed - finalOptimizedRooms).coerceAtLeast(0), "OK", anesthesiaSavings)
                } else if (result.status == "OK_LOWER_BOUND_REACHED") {
                    applyDefaultMapping(cases)
                    val anesthesiaSavings = anesthesiaService.calculateSavings(
                        cases, cases, actualRooms,
                        true, true, true,
                        config.getAnesPaddingMinutes(),
                        binCapacity
                    )
                    DayGroupResult(key, cases, emptyList(), actualRoomsUsed, actualRoomsUsed, 0, "OK_NO_SAVINGS_POSSIBLE", anesthesiaSavings)
                } else {
                    DayGroupResult(key, cases, emptyList(), actualRoomsUsed, null, 0, "INFEASIBLE")
                }
            }
        }
    }

    private fun applyDefaultMapping(cases: List<ParsedRow>) {
        val originalRooms = cases.map { "${it.record!!.site} - ${it.record!!.orName}" }.distinct().sorted()
        
        for (pr in cases) {
            val originalRoomStr = "${pr.record!!.site} - ${pr.record!!.orName}"
            val index = originalRooms.indexOf(originalRoomStr)
            val roomLetter = ('A' + index).toString()
            pr.assignedRoomName = "${pr.record!!.site} - Consolidated Room $roomLetter"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OptimizationService::class.java)
    }
}
