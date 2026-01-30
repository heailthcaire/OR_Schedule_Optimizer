package org.heailth.service

import org.heailth.config.ConfigLoader
import org.heailth.model.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class ReportDataService {
    fun prepareContext(
        config: ConfigLoader,
        weekSummaries: List<WeekSummary>,
        dayResults: List<DayGroupResult>,
        allInvalidRows: List<ParsedRow>,
        totalRoomsSavedGlobal: Int,
        totalRoomDaysGlobal: Int,
        startDate: String?,
        endDate: String?,
        siteCount: Int,
        roomCount: Int,
        siteStats: List<StatisticsResult>,
        globalStats: StatisticsResult,
        totalAnesthesiologistsGlobal: Int,
        globalAnesthesia: AnesthesiaSavings,
        caseValidation: ValidationReport? = null,
        crnaValidation: ValidationReport? = null
    ): Map<String, Any?> {
        val context = mutableMapOf<String, Any?>()
        context["config"] = config
        context["totalRoomsSavedGlobal"] = totalRoomsSavedGlobal
        context["totalRoomDaysGlobal"] = totalRoomDaysGlobal
        context["startDate"] = startDate
        context["endDate"] = endDate
        context["siteCount"] = siteCount
        context["roomCount"] = roomCount
        context["siteStats"] = siteStats
        context["globalStats"] = globalStats
        context["invalidRows"] = allInvalidRows
        context["timestamp"] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        context["totalAnesthesiologistsGlobal"] = totalAnesthesiologistsGlobal
        context["globalAnesthesia"] = globalAnesthesia
        context["caseValidation"] = caseValidation
        context["crnaValidation"] = crnaValidation

        val isHighDetail = "high" == config.getReportDetailLevel()
        val isLowDetail = "low" == config.getReportDetailLevel()
        context["isHighDetail"] = isHighDetail
        context["isLowDetail"] = isLowDetail

        var capacityText = "${config.getBinCapacity()} minutes"
        if (config.absoluteStartTime != null && config.absoluteEndTime != null) {
            capacityText += " (${config.absoluteStartTime} - ${config.absoluteEndTime})"
        }
        context["capacityText"] = capacityText
        context["startTimePad"] = config.startTimePad
        context["endTimePad"] = config.endTimePad
        context["anesPaddingMinutes"] = config.getAnesPaddingMinutes()

        val siteDays = dayResults.size
        val siteDaysEliminable = dayResults.count { it.roomsSavedDay > 0 }
        
        val financialService = FinancialMetricsService(config)
        val overallUtilization = financialService.calculateAssetUtilization(dayResults, true)
        val currentUtilization = financialService.calculateAssetUtilization(dayResults, false)

        val costPerOrDay = config.getProperty("cost.per.or.day", "1500").toDoubleOrNull() ?: 1500.0
        val includeAnesthesiologist = true
        val includeCRNA1 = true
        val anesthesiaStaffingSelected = true // Always show anesthesia savings in full report for audit transparency

        val anesRate = config.getProperty("financials.anesthesiologist.rate", "3500").toDoubleOrNull() ?: 3500.0
        val crnaRate = config.getProperty("financials.crna.rate", "1800").toDoubleOrNull() ?: 1800.0
        val crnaHourRate = config.getProperty("financials.crna.hour.rate", "150").toDoubleOrNull() ?: 150.0

        val anesthesiaStaffingSavingsPerAnes = anesRate + crnaRate

        val totalRoomsSavedGlobalFinal = totalRoomsSavedGlobal
        val totalRoomDaysGlobalFinal = totalRoomDaysGlobal
        val totalAnesthesiaSavingsFinal = if (anesthesiaStaffingSelected) {
            globalAnesthesia.method1Savings + globalAnesthesia.method2Savings + globalAnesthesia.method3Savings
        } else 0

        val totalCrnaHours = globalStats.totalCrnaHours
        val scheduledCrnaHours = globalStats.totalCrnaHours // Show raw supply as "Scheduled CRNA (Supply)"
        val extraCrnaHours = globalStats.extraCrnaHours
        val crnaCostSaved = globalStats.crnaCostSaved

        context["totalCrnaHours"] = totalCrnaHours
        context["scheduledCrnaHours"] = scheduledCrnaHours
        context["extraCrnaHours"] = extraCrnaHours
        context["crnaCostSaved"] = crnaCostSaved
        context["crnaCurrentUtil"] = globalStats.crnaCurrentUtil
        context["crnaTargetUtil"] = globalStats.crnaTargetUtil
        context["requiredAnesDays"] = globalStats.requiredAnesDays
        context["scheduledAnesDays"] = globalStats.scheduledAnesDays
        context["regionsCount"] = globalStats.regionsCount

        val totalAnesthesiologistsGlobalFinal = globalStats.scheduledAnesDays.toInt()

        // Calculate max extra CRNA hours for consistent chart scaling
        // Requirement: Extend to the next nearest 500, with at least 200 hours buffer.
        val rawMaxCrna = siteStats.maxOfOrNull { it.extraCrnaHours } ?: 0.0
        val bufferedMax = rawMaxCrna + 200.0
        val maxExtraCrnaHours = (Math.ceil(bufferedMax / 500.0) * 500.0).toInt().coerceAtLeast(500)
        context["maxExtraCrnaHours"] = maxExtraCrnaHours
        val laborYield = globalStats.averageLaborYield

        // Dynamic Annualization Logic
        val start = globalStats.startDate
        val end = globalStats.endDate
        var annualizationFactor = 12.0 // Default fallback
        var analyzedDays = 30L // Default fallback

        if (start != null && end != null) {
            analyzedDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
            if (analyzedDays > 0) {
                annualizationFactor = 365.25 / analyzedDays
            }
        }

        val annualOpportunity = Math.round((totalRoomsSavedGlobalFinal * costPerOrDay * annualizationFactor) + 
                                (totalAnesthesiaSavingsFinal * anesRate * annualizationFactor) +
                                (extraCrnaHours * crnaHourRate * annualizationFactor)).toDouble()

        context["crnaAnnualSavings"] = extraCrnaHours * crnaHourRate * annualizationFactor
        context["anesAnnualSavings"] = totalAnesthesiaSavingsFinal * anesRate * annualizationFactor
        context["orAnnualSavings"] = totalRoomsSavedGlobalFinal * costPerOrDay * annualizationFactor

        val hardAnnualSavings = (extraCrnaHours * crnaHourRate * annualizationFactor) + (totalAnesthesiaSavingsFinal * anesRate * annualizationFactor)
        val softAnnualSavings = totalRoomsSavedGlobalFinal * costPerOrDay * annualizationFactor
        
        context["hardAnnualSavings"] = hardAnnualSavings
        context["softAnnualSavings"] = softAnnualSavings
        context["dailyLeakage"] = annualOpportunity / 365.25
        context["auditId"] = "AUD-" + UUID.randomUUID().toString().substring(0, 8).uppercase()

        context["totalRoomsSavedGlobal"] = totalRoomsSavedGlobalFinal
        context["totalRoomDaysGlobal"] = totalRoomDaysGlobalFinal
        context["totalAnesthesiologistsGlobal"] = totalAnesthesiologistsGlobalFinal
        context["laborYield"] = laborYield
        context["crnaHourRate"] = crnaHourRate
        context["anesRate"] = anesRate
        context["crnaProductivityFactor"] = config.getCrnaProductivityFactor()
        context["minProcedureDurationMinutes"] = config.getMinProcedureDurationMinutes()
        context["maxProcedureDurationHours"] = config.getMaxProcedureDurationHours()
        context["skipRowProviderName"] = config.getProperty("skip.row.Provider_Name", "*** Provider Missing ***")
        context["annualizationFactor"] = annualizationFactor
        context["analyzedDays"] = analyzedDays
        context["startDate"] = start?.toString() ?: "N/A"
        context["endDate"] = end?.toString() ?: "N/A"
        
        // Update globalAnesthesia.totalAnesthesiaSavings for correct display in Pebble
        val updatedGlobalAnesthesia = AnesthesiaSavings(
            globalAnesthesia.method1Savings,
            globalAnesthesia.method2Savings,
            globalAnesthesia.method3Savings,
            totalAnesthesiaSavingsFinal
        )
        context["globalAnesthesia"] = updatedGlobalAnesthesia
        
        context["costPerOrDay"] = costPerOrDay
        context["siteDays"] = siteDays
        context["siteDaysEliminable"] = siteDaysEliminable
        context["overallUtilization"] = overallUtilization
        context["currentUtilization"] = currentUtilization
        context["laborYield"] = globalStats.averageLaborYield
        context["anesthesiaStaffingSavingsPerAnes"] = anesthesiaStaffingSavingsPerAnes
        context["anesthesiaStaffingSelected"] = anesthesiaStaffingSelected
        context["annualOpportunity"] = annualOpportunity
        context["dayResults"] = dayResults
        context["verificationSample"] = prepareVerificationSample(dayResults)

        val weekSummariesByWeek = weekSummaries.groupBy { it.weekKey.week }
        context["weekSummariesByWeek"] = weekSummariesByWeek.entries

        context["showSummaryGraph"] = config.isShowSummaryGraph()
        context["showDetailGraph"] = config.isShowDetailGraph()

        if (config.isShowSummaryGraph()) {
            val execSummaryChart = mutableMapOf<String, Any>()
            execSummaryChart["totalActual"] = totalRoomDaysGlobal
            execSummaryChart["totalSaved"] = totalRoomsSavedGlobal
            context["execSummaryChart"] = execSummaryChart
        }

        if (config.isShowDetailGraph()) {
            prepareSiteCharts(context, dayResults, siteStats)
        }

        return context
    }

    private fun prepareSiteCharts(context: MutableMap<String, Any?>, dayResults: List<DayGroupResult>, siteStats: List<StatisticsResult>) {
        val siteCharts = mutableListOf<Map<String, Any>>()
        
        val bySite = dayResults.groupBy { it.groupKey.site }
        
        val siteToCanvasId = mutableMapOf<String, String>()
        // 1. Populate from siteStats
        for (ss in siteStats) {
            val canvasId = "chart_" + ss.name.replace(Regex("[^a-zA-Z0-9]"), "_")
            siteToCanvasId[ss.name] = canvasId
        }
        // 2. Ensure all sites in dayResults are covered (backup)
        for (site in bySite.keys) {
            siteToCanvasId.getOrPut(site) { "chart_" + site.replace(Regex("[^a-zA-Z0-9]"), "_") }
        }

        for ((site, siteDayData) in bySite) {
            val canvasId = siteToCanvasId[site]!!

            val monthlyMap = TreeMap<String, MonthlyData>()
            for (dr in siteDayData) {
                val month = dr.groupKey.date.toString().substring(0, 7)
                monthlyMap.getOrPut(month) { MonthlyData() }
                    .add(dr.actualRoomsUsed, dr.optimizedRoomsUsed ?: dr.actualRoomsUsed)
            }

            val labels = monthlyMap.keys.toList()
            val actual = monthlyMap.values.map { it.actual }
            val optimized = monthlyMap.values.map { it.optimized }
            val saved = monthlyMap.values.map { (it.actual - it.optimized).coerceAtLeast(0) }

            val siteChart = mutableMapOf<String, Any>()
            siteChart["siteName"] = site
            siteChart["labels"] = labels
            siteChart["actual"] = actual
            siteChart["optimized"] = optimized
            siteChart["saved"] = saved
            siteChart["canvasId"] = canvasId
            siteCharts.add(siteChart)
        }
        context["siteCharts"] = siteCharts
        context["siteToCanvasId"] = siteToCanvasId
    }

    private fun prepareVerificationSample(dayResults: List<DayGroupResult>): CFOVerificationSample? {
        val sampleDay = dayResults.filter { it.roomsSavedDay > 0 }
            .maxByOrNull { it.roomsSavedDay } ?: return null

        val originalRoomToCases = sampleDay.validCases.groupBy { it.record?.orName ?: "Unknown" }
        
        // Identify which rooms to call "eliminated". 
        // We pick the N rooms with the least workload, where N is the number of rooms saved.
        val eliminatedRoomNames = originalRoomToCases.entries
            .sortedWith(compareBy<Map.Entry<String, List<ParsedRow>>> { it.value.size }
                .thenBy { it.value.sumOf { c -> c.durationMinutes } })
            .take(sampleDay.roomsSavedDay)
            .map { it.key }
            .toSet()

        val roomConsolidationDetails = originalRoomToCases
            .entries
            .sortedByDescending { it.value.sumOf { c -> c.durationMinutes } }
            .map { (orName, cases) ->
                RoomProof(
                    originalRoomName = orName,
                    procedureCount = cases.size,
                    totalMinutes = cases.sumOf { it.durationMinutes },
                    wasEliminated = orName in eliminatedRoomNames
                )
            }
            .sortedBy { it.wasEliminated } // Put consolidated rooms first, then eliminated ones at the bottom
            .take(6) // Show a representative mix of 6 rooms

        return CFOVerificationSample(
            siteName = sampleDay.groupKey.site,
            date = sampleDay.groupKey.date.toString(),
            baselineRooms = sampleDay.actualRoomsUsed,
            optimizedRooms = sampleDay.optimizedRoomsUsed ?: sampleDay.actualRoomsUsed,
            roomsSaved = sampleDay.roomsSavedDay,
            roomConsolidationDetails = roomConsolidationDetails
        )
    }

    private class MonthlyData {
        var actual = 0
        var optimized = 0

        fun add(a: Int, o: Int) {
            actual += a
            optimized += o
        }
    }
}
