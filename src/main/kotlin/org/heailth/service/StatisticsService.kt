package org.heailth.service

import org.apache.commons.math3.stat.Frequency
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.heailth.model.ParsedRow
import org.heailth.model.ProcedureComparison
import org.heailth.model.StatisticsResult
import org.slf4j.LoggerFactory
import java.util.*
import java.util.stream.Collectors

class StatisticsService(private val financialService: FinancialMetricsService? = null) {

    private val logger = LoggerFactory.getLogger(StatisticsService::class.java)

    fun calculateSiteStatistics(validRows: List<ParsedRow>, crnaRows: List<org.heailth.model.CrnaRecord> = emptyList(), config: org.heailth.config.ConfigLoader? = null, globalStaffingMap: Map<String, Any?> = emptyMap()): List<StatisticsResult> {
        val bySite = validRows.parallelStream()
            .collect(Collectors.groupingBy { r: ParsedRow -> r.record!!.site })

        // Pre-calculate regional staffing if possible to distribute to sites
        val siteToCrnaExtraHours = mutableMapOf<String, Double>()
        if (config != null && globalStaffingMap.isNotEmpty()) {
            val dailyRegionalExtra = globalStaffingMap["dailyRegionalExtra"] as? Map<Pair<String, java.time.LocalDate>, Double> ?: emptyMap()
            val dailyRegionalDemand = globalStaffingMap["dailyRegionalDemand"] as? Map<Pair<String, java.time.LocalDate>, Double> ?: emptyMap()
            
            for (site in bySite.keys) {
                val region = config.siteToRegion[site] ?: "Standalone: $site"
                val siteRows = bySite[site] ?: emptyList()
                val siteRowsByDate = siteRows.groupBy { it.record!!.date }
                
                var siteTotalExtra = 0.0
                for ((date, rowsInDate) in siteRowsByDate) {
                    val key = region to date
                    val regionExtraDay = dailyRegionalExtra[key] ?: 0.0
                    val regionDemandDay = dailyRegionalDemand[key] ?: 0.0
                    
                    if (regionDemandDay > 0) {
                        val siteDayAnesthesiaMinutes = rowsInDate.sumOf { row ->
                            val start = row.record?.csvStart ?: return@sumOf 0L
                            val end = row.record.csvEnd ?: return@sumOf 0L
                            val diff = java.time.temporal.ChronoUnit.MINUTES.between(start, end)
                            if (diff < 0) 0L else diff
                        }
                        val siteDayDemand = siteDayAnesthesiaMinutes / 60.0
                        val siteDayShare = siteDayDemand / regionDemandDay
                        siteTotalExtra += (regionExtraDay * siteDayShare)
                    }
                }
                siteToCrnaExtraHours[site] = siteTotalExtra
            }
        }

        return bySite.entries.parallelStream()
            .map { entry -> 
                val stats = calculateStats(entry.key, entry.value, emptyList(), emptyMap())
                val extraCrna = siteToCrnaExtraHours[entry.key] ?: 0.0
                stats.copy(extraCrnaHours = extraCrna)
            }
            .sorted(Comparator.comparing { it.name })
            .collect(Collectors.toList())
    }

    fun calculateGlobalStatistics(validRows: List<ParsedRow>, siteStats: List<StatisticsResult>, globalStaffing: Map<String, Any?> = emptyMap()): StatisticsResult {
        val validRecords = validRows.mapNotNull { it.record }
        val startDate = if (validRecords.isNotEmpty()) validRecords.minOf { it.date } else null
        val endDate = if (validRecords.isNotEmpty()) validRecords.maxOf { it.date } else null

        val global = calculateStats("Global Overall", validRows, emptyList(), emptyMap())

        val comparisons = mutableListOf<ProcedureComparison>()
        val topMeans = global.topProcedureMeans

        for ((proc, globalMean) in topMeans) {
            var minSite = "N/A"
            var minMean = Double.MAX_VALUE
            var maxSite = "N/A"
            var maxMean = -1.0

            for (ss in siteStats) {
                if (ss.topProcedureMeans.containsKey(proc)) {
                    val siteMean = ss.topProcedureMeans[proc]!!
                    if (siteMean < minMean) {
                        minMean = siteMean
                        minSite = ss.name
                    }
                    if (siteMean > maxMean) {
                        maxMean = siteMean
                        maxSite = ss.name
                    }
                }
            }

            if (minSite != "N/A") {
                comparisons.add(ProcedureComparison(proc, globalMean, minSite, minMean, maxSite, maxMean))
            }
        }

        // Recalculate site stats with global means for variance costing
        val finalSiteStats = siteStats.map { ss ->
            val siteRows = validRows.filter { it.record!!.site == ss.name }
            val stats = calculateStats(ss.name, siteRows, emptyList(), global.topProcedureMeans)
            stats.copy(extraCrnaHours = ss.extraCrnaHours)
        }

        return StatisticsResult(
            name = global.name,
            procedureFrequency = global.procedureFrequency,
            averageDuration = global.averageDuration,
            variance = global.variance,
            stdDev = global.stdDev,
            min = global.min,
            max = global.max,
            p25 = global.p25,
            p75 = global.p75,
            p90 = global.p90,
            p95 = global.p95,
            totalProcedures = global.totalProcedures,
            maxUniqueOrs = global.maxUniqueOrs,
            totalRoomDays = global.totalRoomDays,
            topProcedureMeans = global.topProcedureMeans,
            procedureComparisons = comparisons,
            averageLaborYield = global.averageLaborYield,
            totalCostOfVariance = global.totalCostOfVariance,
            primeTimeUtilization = global.primeTimeUtilization,
            fcots = global.fcots,
            averageTot = global.averageTot,
            contributionMargin = global.contributionMargin,
            totalCrnaHours = Math.round((globalStaffing["totalCrnaHours"] as? Double) ?: 0.0).toDouble(),
            scheduledCrnaHours = Math.round((globalStaffing["scheduledCrnaHours"] as? Double) ?: 0.0).toDouble(),
            requiredCrnaHours = Math.round((globalStaffing["requiredCrnaHours"] as? Double) ?: 0.0).toDouble(),
            extraCrnaHours = Math.round((globalStaffing["extraCrnaHours"] as? Double) ?: 0.0).toDouble(),
            crnaCostSaved = (globalStaffing["crnaCostSaved"] as? Double) ?: 0.0,
            crnaCurrentUtil = (globalStaffing["crnaCurrentUtil"] as? Double) ?: 0.0,
            crnaTargetUtil = (globalStaffing["crnaTargetUtil"] as? Double) ?: 0.0,
            avgActiveRooms = (globalStaffing["avgActiveRooms"] as? Double) ?: 0.0,
            maxActiveRooms = (globalStaffing["maxActiveRooms"] as? Int) ?: 0,
            requiredAnesDays = (globalStaffing["requiredAnesDays"] as? Int) ?: 0,
            scheduledAnesDays = (globalStaffing["scheduledAnesDays"] as? Int) ?: 0,
            regionsCount = (globalStaffing["regionsCount"] as? Int) ?: 0,
            startDate = startDate,
            endDate = endDate
        ).copy(procedureComparisons = comparisons)
    }

    fun calculateGlobalStaffing(validRows: List<ParsedRow>, crnaRows: List<org.heailth.model.CrnaRecord>, config: org.heailth.config.ConfigLoader, dayResults: List<org.heailth.model.DayGroupResult> = emptyList()): Map<String, Any?> {
        val crnaRate = config.getProperty("financials.crna.rate", "1800").toDoubleOrNull() ?: 1800.0
        val productivityFactor = config.getCrnaProductivityFactor()
        val crnaHourRate = config.getProperty("financials.crna.hour.rate", "150").toDoubleOrNull() ?: 150.0
        
        // Mapping from SiteDay to optimized rooms
        val optimizedRoomsMap = dayResults.associate { (it.groupKey.site to it.groupKey.date) to (it.optimizedRoomsUsed ?: it.actualRoomsUsed) }

        // Group Cases by (Region, Date) using the site-to-region map
        val casesByRegionDate = validRows.groupBy { row ->
            val site = row.record!!.site
            val region = config.siteToRegion[site] ?: "Standalone: $site"
            region to row.record.date
        }

        // Supply: CRNA hours in this region-date
        val crnaByRegionDate = crnaRows.groupBy { it.region to it.date }

        // Determine the date range of the OR cases to prevent misleading "Extra CRNA" 
        // values for dates that exist only in the CRNA staffing dataset.
        val orDates = validRows.map { it.record!!.date }.toSet()

        if (orDates.isEmpty()) {
            logger.warn("No valid OR cases found. CRNA staffing cannot be calculated without case dates.")
        }

        val allKeys = (casesByRegionDate.keys + crnaByRegionDate.keys)
            .filter { key -> orDates.contains(key.second) }
            .distinct()
        
        logger.debug("Calculating staffing for ${allKeys.size} region-date combinations. OR Dates: ${orDates.size}, CRNA Entries: ${crnaRows.size}")
        
        var totalCrnaHours = 0.0
        var totalEffectiveHours = 0.0
        var totalRequiredHours = 0.0
        var totalExtraHours = 0.0
        var totalActualRooms = 0
        var totalOptimizedRooms = 0
        val activeRoomsList = mutableListOf<Int>()
        val regionalDetails = mutableMapOf<String, MutableMap<String, Any>>()
        val dailyRegionalExtra = mutableMapOf<Pair<String, java.time.LocalDate>, Double>()
        val dailyRegionalDemand = mutableMapOf<Pair<String, java.time.LocalDate>, Double>()

        for (key in allKeys) {
            val regionName = key.first
            val date = key.second
            val casesInRegion = casesByRegionDate[key] ?: emptyList()
            val crnasInRegion = crnaByRegionDate[key] ?: emptyList()
            
            if (casesInRegion.isEmpty()) {
                logger.debug("No cases found for region-date: $key. Supply exists (${crnasInRegion.size} entries), but skipping because no demand data for this date.")
                continue
            }
            
            // Demand: Unique ORs across all sites in this region-date
            val activeRooms = casesInRegion.map { "${it.record!!.site} - ${it.record!!.orName}" }.distinct().size
            activeRoomsList.add(activeRooms)

            // Optimized Demand: Sum of optimized rooms for all sites in this region-date
            val regionOptimizedRooms = casesInRegion.map { it.record!!.site to it.record.date }.distinct()
                .sumOf { siteDateKey -> optimizedRoomsMap[siteDateKey] ?: 0 }
            
            // Demand Side: Total Anesthesia Minutes model
            val totalAnesthesiaMinutes = casesInRegion.sumOf { row ->
                val start = row.record?.csvStart ?: return@sumOf 0L
                val end = row.record.csvEnd ?: return@sumOf 0L
                val diff = java.time.temporal.ChronoUnit.MINUTES.between(start, end)
                if (diff < 0) 0L else diff
            }
            val requiredCrnaHours = totalAnesthesiaMinutes / 60.0
            
            if (requiredCrnaHours > 1000) {
                 logger.debug("HIGH_DEMAND: Region-Date: {} | Total Minutes: {} | Required Hours: {}", key, totalAnesthesiaMinutes, requiredCrnaHours)
                 casesInRegion.sortedByDescending { it.durationMinutes }.take(10).forEach { 
                     val s = it.record?.csvStart
                     val e = it.record?.csvEnd
                     logger.debug("  - Case: {} | Site: {} | Raw Start: {} | Raw End: {} | Padded Duration: {}", it.record?.id, it.record?.site, s, e, it.durationMinutes)
                 }
            }
            
            // Supply Side: CRNA hours in this region-date
            val crnaHours = crnasInRegion.sumOf { it.durationHours }
            val effectiveCrnaHours = crnaHours * productivityFactor
            
            // Demand Side: Concurrent OR Demand (Supply side should match number of providers)
            // In a perfectly optimized world, we need 1 CRNA per concurrent OR.
            // requiredCrnaHours (from minutes) represents the 'theoretical' continuous work.
            // But CRNAs are staffed in blocks (8h, 10h, etc.).
            
            // Extra = Effective Supply - Demand
            var extraHours = (effectiveCrnaHours - requiredCrnaHours).coerceAtLeast(0.0)
            
            // If supply is missing for this date, do not treat it as a negative "saving".
            // It just means we don't have enough data to calculate staffing efficiency for this date.
            // When we skip it, we should also not count its demand/supply in the totals for efficiency.
            if (crnaHours == 0.0) {
                extraHours = 0.0
            } else {
                totalCrnaHours += crnaHours
                totalEffectiveHours += effectiveCrnaHours
                totalRequiredHours += requiredCrnaHours
                totalExtraHours += extraHours
                
                // Only count rooms for days where we have staffing data to keep efficiency metrics consistent
                totalActualRooms += activeRooms
                totalOptimizedRooms += regionOptimizedRooms

                // Store daily regional details for distribution
                dailyRegionalExtra[regionName to date] = extraHours
                dailyRegionalDemand[regionName to date] = requiredCrnaHours
            }

            if (crnaHours > 0.0 || requiredCrnaHours > 0.0) {
                logger.debug("Region-Date: $key | Supply: $crnaHours hrs | Effective: $effectiveCrnaHours hrs | Demand: $requiredCrnaHours hrs (from ${casesInRegion.size} cases) | Extra: $extraHours hrs")
                
                if (requiredCrnaHours > 0.0 && crnaHours == 0.0) {
                    logger.debug("No CRNA supply found for region-date: {}, but cases exist.", key)
                }
            }

            // Aggregation for regional details
            val regionAgg = regionalDetails.getOrPut(regionName) {
                mutableMapOf(
                    "crnaHours" to 0.0,
                    "scheduledCrnaHours" to 0.0,
                    "requiredCrnaHours" to 0.0,
                    "extraCrnaHours" to 0.0
                )
            }
            if (crnaHours > 0.0) {
                regionAgg["crnaHours"] = (regionAgg["crnaHours"] as Double) + crnaHours
                regionAgg["scheduledCrnaHours"] = (regionAgg["scheduledCrnaHours"] as Double) + effectiveCrnaHours
                regionAgg["requiredCrnaHours"] = (regionAgg["requiredCrnaHours"] as Double) + requiredCrnaHours
                regionAgg["extraCrnaHours"] = (regionAgg["extraCrnaHours"] as Double) + extraHours.coerceAtLeast(0.0)
            }
        }

        val roomsSaved = (totalActualRooms - totalOptimizedRooms).coerceAtLeast(0).toDouble()
        
        val crnaCurrentUtil = if (totalCrnaHours > 0) (totalRequiredHours / totalCrnaHours) * 100.0 else 0.0
        val crnaTargetUtil = if (totalEffectiveHours > 0) (totalRequiredHours / totalEffectiveHours) * 100.0 else 0.0

        val regionsCount = config.siteToRegion.values.distinct().size

        return mapOf(
            "totalCrnaHours" to totalCrnaHours,
            "scheduledCrnaHours" to totalEffectiveHours,
            "requiredCrnaHours" to totalRequiredHours,
            "extraCrnaHours" to totalExtraHours,
            "crnaCostSaved" to totalEffectiveHours * crnaHourRate,
            "crnaCurrentUtil" to crnaCurrentUtil,
            "crnaTargetUtil" to crnaTargetUtil,
            "avgActiveRooms" to (if (activeRoomsList.isNotEmpty()) activeRoomsList.average() else 0.0),
            "maxActiveRooms" to (activeRoomsList.maxOrNull() ?: 0),
            "requiredAnesDays" to totalOptimizedRooms,
            "scheduledAnesDays" to totalActualRooms,
            "regionsCount" to regionsCount,
            "startDate" to (if (orDates.isNotEmpty()) orDates.minOf { it } else null as java.time.LocalDate?),
            "endDate" to (if (orDates.isNotEmpty()) orDates.maxOf { it } else null as java.time.LocalDate?),
            "regionalDetails" to regionalDetails,
            "dailyRegionalExtra" to dailyRegionalExtra,
            "dailyRegionalDemand" to dailyRegionalDemand
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun distributeExtraCrnaHours(dayResults: List<org.heailth.model.DayGroupResult>, globalStaffingMap: Map<String, Any?>, config: org.heailth.config.ConfigLoader): List<org.heailth.model.DayGroupResult> {
        val dailyRegionalExtra = globalStaffingMap["dailyRegionalExtra"] as? Map<Pair<String, java.time.LocalDate>, Double> ?: emptyMap()
        val dailyRegionalDemand = globalStaffingMap["dailyRegionalDemand"] as? Map<Pair<String, java.time.LocalDate>, Double> ?: emptyMap()

        return dayResults.map { dr ->
            val region = config.siteToRegion[dr.groupKey.site] ?: "Standalone: ${dr.groupKey.site}"
            val key = region to dr.groupKey.date
            
            val regionExtraDay = dailyRegionalExtra[key] ?: 0.0
            val regionDemandDay = dailyRegionalDemand[key] ?: 0.0
            
            if (regionDemandDay > 0) {
                val siteDayAnesthesiaMinutes = dr.validCases.sumOf { row ->
                    val start = row.record?.csvStart ?: return@sumOf 0L
                    val end = row.record.csvEnd ?: return@sumOf 0L
                    val diff = java.time.temporal.ChronoUnit.MINUTES.between(start, end)
                    if (diff < 0) 0L else diff
                }
                val siteDayDemand = siteDayAnesthesiaMinutes / 60.0
                
                // Distribute daily regional extra hours proportional to the site-day's demand relative to daily regional demand
                val siteDayShare = siteDayDemand / regionDemandDay
                dr.copy(extraCrnaHours = regionExtraDay * siteDayShare)
            } else {
                dr
            }
        }
    }

    private fun calculateStats(name: String, rows: List<ParsedRow>, comparisons: List<ProcedureComparison>, globalMeans: Map<String, Double>): StatisticsResult {
        val stats = DescriptiveStatistics()
        val freq = Frequency()
        val uniqueOrs = mutableSetOf<String>()
        val procedureDurations = mutableMapOf<String, MutableList<Long>>()

        for (row in rows) {
            val duration = row.durationMinutes
            stats.addValue(duration.toDouble())
            uniqueOrs.add(row.record!!.orName)
            val proc = row.record.procedure
            var groupedProc = proc ?: ""
            if (proc != null && proc.isNotBlank()) {
                val trimmed = proc.trim()
                val spaceIndex = trimmed.indexOf(' ')
                var firstWord = if (spaceIndex == -1) trimmed else trimmed.substring(0, spaceIndex)

                val len = firstWord.length
                if (len > 0) {
                    val lastChar = firstWord[len - 1]
                    if (!lastChar.isLetterOrDigit()) {
                        firstWord = firstWord.substring(0, len - 1)
                    }
                }
                groupedProc = firstWord.uppercase()
            }
            freq.addValue(groupedProc)
            procedureDurations.getOrPut(groupedProc) { mutableListOf() }.add(duration)
        }

        val procedureFrequency = mutableMapOf<String, Long>()
        val uniqueValues = mutableListOf<String>()
        val it = freq.entrySetIterator()
        while (it.hasNext()) {
            uniqueValues.add(it.next().key.toString())
        }

        val sortedProcedures = uniqueValues.sortedWith { a, b ->
            val f1 = freq.getCount(a)
            val f2 = freq.getCount(b)
            if (f1 != f2) f2.compareTo(f1) else a.compareTo(b)
        }

        sortedProcedures.forEach { p -> procedureFrequency[p] = freq.getCount(p) }

        val topProcedureMeans = mutableMapOf<String, Double>()
        sortedProcedures.take(10).forEach { p ->
            val durations = procedureDurations[p] ?: emptyList<Long>()
            val mean = if (durations.isNotEmpty()) durations.map { it.toDouble() }.average() else 0.0
            topProcedureMeans[p] = mean
        }

        val laborYield = financialService?.calculateLaborYield(rows) ?: 0.0
        val costOfVariance = financialService?.calculateCostOfVariance(rows, globalMeans) ?: 0.0
        val primeTimeUtil = financialService?.calculatePrimeTimeUtilization(rows) ?: 0.0
        val fcots = financialService?.calculateFCOTS(rows) ?: 0.0
        val tot = financialService?.calculateAverageTOT(rows) ?: 0.0
        val margin = financialService?.calculateContributionMargin(rows) ?: 0.0

        val totalRoomDays = if (rows.isNotEmpty()) {
            rows.groupBy { row ->
                val date = row.record?.date
                val room = row.record?.orName
                date to room
            }.size
        } else 0

        return StatisticsResult(
            name,
            procedureFrequency,
            stats.mean,
            stats.variance,
            stats.standardDeviation,
            stats.min,
            stats.max,
            stats.getPercentile(25.0),
            stats.getPercentile(75.0),
            stats.getPercentile(90.0),
            stats.getPercentile(95.0),
            stats.n,
            uniqueOrs.size,
            totalRoomDays,
            topProcedureMeans,
            comparisons,
            laborYield,
            costOfVariance,
            primeTimeUtil,
            fcots,
            tot,
            margin,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0
        )
    }
}
