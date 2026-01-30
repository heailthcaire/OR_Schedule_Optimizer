package org.heailth.service

import org.heailth.config.ConfigLoader
import org.heailth.model.DayGroupResult
import org.heailth.model.StatisticsResult
import java.util.*
import kotlin.math.roundToInt

class StrategicPromptService {

    fun generateStrategicPrompt(
        globalStats: StatisticsResult,
        siteStats: List<StatisticsResult>,
        dayResults: List<DayGroupResult>,
        config: ConfigLoader
    ): String {
        val totalRoomDaysGlobal = dayResults.sumOf { it.actualRoomsUsed }
        val totalRoomsSavedGlobal = dayResults.sumOf { it.roomsSavedDay }
        val savingsPercent = if (totalRoomDaysGlobal > 0) (totalRoomsSavedGlobal.toDouble() / totalRoomDaysGlobal * 100).roundToInt() else 0
        
        val financialService = FinancialMetricsService(config)
        val baselineUtil = financialService.calculateAssetUtilization(dayResults, false)
        val optimizedUtil = financialService.calculateAssetUtilization(dayResults, true)

        val sb = StringBuilder()
        sb.append("### COPY-PASTE PROMPT START\n\n")
        sb.append("**Role:** You are a Senior Health System CFO and Strategic Operations Consultant.\n")
        sb.append("**Task:** Analyze the following surgical optimization data and provide a high-level executive recommendation for facility consolidation and financial recovery.\n\n")
        sb.append("**Analysis Guidelines:**\n")
        sb.append("1. **Identify the 'Leak':** Which sites are the most inefficient (low utilization/high fragmentation)?\n")
        sb.append("2. **Quantify the Opportunity:** Calculate the potential annual savings if the \"Optimized\" room count is adopted (assume $1,500 per OR-Day saved).\n")
        sb.append("3. **Regional Strategy:** For Clusters, determine if satellite sites should be absorbed into the Hub to eliminate building overhead.\n")
        sb.append("4. **Action Plan:** Provide 3 clear, prioritized \"Strategic Moves\" for the next fiscal quarter.\n\n")
        sb.append("**Tone:** Professional, data-driven, and focused on \"Return on Asset\" (ROA).\n\n")
        sb.append("---\n")
        sb.append("### STRATEGIC DATA PAYLOAD (Current Snapshot)\n\n")
        sb.append("**[SYSTEM OVERVIEW]**\n")
        sb.append("*   **Total Sites Analyzed:** ${siteStats.size}\n")
        sb.append("*   **Total OR-Days Processed:** $totalRoomDaysGlobal\n")
        sb.append("*   **Global Room Savings Identified:** $totalRoomsSavedGlobal rooms ($savingsPercent% reduction)\n")
        sb.append("*   **Global Asset Utilization:** Baseline: ${baselineUtil.roundToInt()}% → Optimized: ${optimizedUtil.roundToInt()}%\n\n")

        sb.append("**[SITE-SPECIFIC PERFORMANCE]**\n")
        val sortedSites = siteStats.sortedBy { it.averageLaborYield } // Rank by potential improvement
        sortedSites.forEachIndexed { index, stats ->
            val siteResults = dayResults.filter { it.groupKey.site == stats.name }
            val siteBaselineUtil = financialService.calculateAssetUtilization(siteResults, false)
            val siteOptimizedUtil = financialService.calculateAssetUtilization(siteResults, true)
            val gap = (siteOptimizedUtil - siteBaselineUtil).coerceAtLeast(0.0).roundToInt()
            
            sb.append("*   **Site:** ${stats.name}\n")
            sb.append("    *   **Baseline Utilization:** ${siteBaselineUtil.roundToInt()}%\n")
            sb.append("    *   **Opportunity Gap:** $gap% (Room for consolidation)\n")
            sb.append("    *   **Labor Yield:** ${stats.averageLaborYield.roundToInt()}% (Staffing efficiency)\n")
            sb.append("    *   **Consolidation Rank:** ${index + 1} / ${siteStats.size}\n")
        }
        sb.append("\n")

        sb.append("**[REGIONAL CLUSTER ANALYSIS]**\n")
        if (config.clusters.isEmpty()) {
            sb.append("No regional clusters defined in the current configuration.\n")
        } else {
            config.clusters.forEach { (clusterName, sites) ->
                if (sites.size > 1) {
                    val hub = sites.first()
                    val satellites = sites.drop(1)
                    satellites.forEach { satellite ->
                        val absorptionData = calculateAbsorptionPotential(hub, satellite, dayResults, config)
                        sb.append("*   **Cluster:** $clusterName\n")
                        sb.append("    *   **Primary Hub:** $hub\n")
                        sb.append("    *   **Absorption Potential:** $satellite can be absorbed by the Hub ${absorptionData.percent}% of the time.\n")
                        sb.append("    *   **Recommended Action:** ${absorptionData.action}\n")
                    }
                }
            }
        }
        sb.append("\n---\n")
        sb.append("**GOAL:** Generate a 3nd-grade-simple summary for the board of directors followed by a detailed CFO justification.\n\n")
        sb.append("### COPY-PASTE PROMPT END")

        return sb.toString()
    }

    private data class AbsorptionData(val percent: Int, val action: String)

    private fun calculateAbsorptionPotential(hub: String, satellite: String, dayResults: List<DayGroupResult>, config: ConfigLoader): AbsorptionData {
        val satelliteDays = dayResults.filter { it.groupKey.site == satellite }
        if (satelliteDays.isEmpty()) return AbsorptionData(0, "No data for satellite site.")
        
        val hubDays = dayResults.filter { it.groupKey.site == hub }.associateBy { it.groupKey.date }
        val binCapacity = config.getBinCapacity()

        var fullyAbsorbableCount = 0
        satelliteDays.forEach { satDay ->
            val date = satDay.groupKey.date
            val hubDay = hubDays[date]
            
            if (hubDay != null) {
                val hubUsedRooms = hubDay.optimizedRoomsUsed ?: hubDay.actualRoomsUsed
                val hubTotalMinutes = hubDay.validCases.sumOf { it.durationMinutes }
                val hubCapacityMinutes = hubUsedRooms * binCapacity
                val hubUnusedMinutes = (hubCapacityMinutes - hubTotalMinutes).coerceAtLeast(0L)
                
                val satTotalMinutes = satDay.validCases.sumOf { it.durationMinutes }
                
                // Simple heuristic: If the entire volume of the satellite fits in the hub's unused minutes
                // we consider it absorbable. In reality, temporal overlap matters too, but this is a strategic indicator.
                if (satTotalMinutes <= hubUnusedMinutes) {
                    fullyAbsorbableCount++
                }
            }
        }

        val percent = (fullyAbsorbableCount.toDouble() / satelliteDays.size * 100).roundToInt()
        val action = when {
            percent >= 80 -> "High Priority: Move all volume to Hub and evaluate site closure."
            percent >= 50 -> "Strategic: Consolidate high-volume days and reduce staff at Satellite."
            else -> "Operational: Maintain current footprint but optimize scheduling sequentially."
        }

        return AbsorptionData(percent, action)
    }
}
