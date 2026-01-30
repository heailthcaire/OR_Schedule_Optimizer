package org.heailth.service

import org.heailth.config.ConfigLoader
import org.heailth.model.ParsedRow
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class FinancialMetricsService(private val config: ConfigLoader) {

    /**
     * Calculate Labor Yield: (Anesthesia End - Anesthesia Start) / Total Block Time (480m)
     */
    fun calculateLaborYield(rows: List<ParsedRow>): Double {
        if (rows.isEmpty()) return 0.0
        val binCapacity = config.getBinCapacity().toDouble()
        if (binCapacity <= 0) return 0.0

        // Group by site, date, room to find unique room-days
        val roomDays = rows.groupBy { "${it.record?.site}_${it.record?.date}_${it.record?.orName}" }.size
        if (roomDays == 0) return 0.0

        val totalCapacity = roomDays * binCapacity
        val totalAnesthesiaMinutes = rows.sumOf { row ->
            val record = row.record ?: return@sumOf 0L
            ChronoUnit.MINUTES.between(record.csvStart, record.csvEnd)
        }.toDouble()

        return (totalAnesthesiaMinutes / totalCapacity) * 100.0
    }

    /**
     * Calculate Asset Utilization: (Patient Out - Patient In) / Total Block Time of used rooms
     */
    fun calculateAssetUtilization(dayResults: List<org.heailth.model.DayGroupResult>, useOptimized: Boolean = true): Double {
        if (dayResults.isEmpty()) return 0.0
        
        val totalDurationMinutes = dayResults.flatMap { it.validCases }.sumOf { it.durationMinutes }
        val totalCapacityMinutes = dayResults.sumOf { 
            val rooms = if (useOptimized) (it.optimizedRoomsUsed ?: it.actualRoomsUsed) else it.actualRoomsUsed
            rooms.toLong() * config.getBinCapacity() 
        }
        
        return if (totalCapacityMinutes > 0) totalDurationMinutes.toDouble() / totalCapacityMinutes * 100.0 else 0.0
    }

    /**
     * Calculate Prime Time Utilization (07:00 - 15:00)
     */
    fun calculatePrimeTimeUtilization(rows: List<ParsedRow>): Double {
        if (rows.isEmpty()) return 0.0
        val primeStart = LocalTime.of(7, 0)
        val primeEnd = LocalTime.of(15, 0)
        val primeTotalMinutes = ChronoUnit.MINUTES.between(primeStart, primeEnd).toDouble()
        
        // Group by site, date, room to find unique room-days
        val roomDays = rows.groupBy { "${it.record?.site}_${it.record?.date}_${it.record?.orName}" }.size
        if (roomDays == 0) return 0.0
        
        val totalPrimeCapacity = roomDays * primeTotalMinutes
        
        var usedPrimeMinutes = 0.0
        for (row in rows) {
            val start = row.record?.patientIn ?: continue
            val end = row.record.patientOut
            
            val overlapStart = if (start.isAfter(primeStart)) start else primeStart
            val overlapEnd = if (end.isBefore(primeEnd)) end else primeEnd
            
            if (overlapStart.isBefore(overlapEnd)) {
                usedPrimeMinutes += ChronoUnit.MINUTES.between(overlapStart, overlapEnd)
            }
        }
        
        return (usedPrimeMinutes / totalPrimeCapacity) * 100.0
    }

    /**
     * Calculate First Case On-Time Start (FCOTS) %
     * Benchmark is 07:30 AM
     */
    fun calculateFCOTS(rows: List<ParsedRow>): Double {
        if (rows.isEmpty()) return 0.0
        val targetStart = LocalTime.of(7, 30)
        
        // Group by site, date, room
        val byRoomDay = rows.groupBy { "${it.record?.site}_${it.record?.date}_${it.record?.orName}" }
        
        var totalFirstCases = 0
        var onTimeFirstCases = 0
        
        for (roomDayRows in byRoomDay.values) {
            val firstCase = roomDayRows.minByOrNull { it.record?.patientIn ?: LocalTime.MAX }
            val start = firstCase?.record?.patientIn ?: continue
            
            totalFirstCases++
            if (!start.isAfter(targetStart)) {
                onTimeFirstCases++
            }
        }
        
        return if (totalFirstCases > 0) (onTimeFirstCases.toDouble() / totalFirstCases) * 100.0 else 0.0
    }

    /**
     * Calculate Average Turnover Time (TOT)
     */
    fun calculateAverageTOT(rows: List<ParsedRow>): Double {
        if (rows.isEmpty()) return 0.0
        
        // Group by site, date, room
        val byRoomDay = rows.groupBy { "${it.record?.site}_${it.record?.date}_${it.record?.orName}" }
        
        val allTots = mutableListOf<Long>()
        
        for (roomDayRows in byRoomDay.values) {
            val sorted = roomDayRows.sortedBy { it.record?.patientIn }
            for (i in 0 until sorted.size - 1) {
                val currentOut = sorted[i].record?.patientOut ?: continue
                val nextIn = sorted[i+1].record?.patientIn ?: continue
                
                if (nextIn.isAfter(currentOut)) {
                    allTots.add(ChronoUnit.MINUTES.between(currentOut, nextIn))
                }
            }
        }
        
        return if (allTots.isNotEmpty()) allTots.average() else 0.0
    }

    /**
     * Calculate Contribution Margin per OR Hour
     * formula: (Revenue - Variable Costs) / OR Time
     * Since we don't have revenue in CSV, we use configurable defaults.
     */
    fun calculateContributionMargin(rows: List<ParsedRow>): Double {
        if (rows.isEmpty()) return 0.0
        
        val estRevenuePerHour = config.getProperty("financials.est.revenue.per.hour", "5000").toDoubleOrNull() ?: 5000.0
        
        // Use bundled cost per day (Room + selected Anesthesia)
        val costPerOrDay = config.getProperty("cost.per.or.day", "1500").toDoubleOrNull() ?: 1500.0
        val includeAnesthesiologist = config.getProperty("financials.include.anesthesiologist", "false").toBoolean()
        val includeCRNA1 = config.getProperty("financials.include.crna1", "false").toBoolean()
        val anesRate = config.getProperty("financials.anesthesiologist.rate", "3500").toDoubleOrNull() ?: 3500.0
        val crnaRate = config.getProperty("financials.crna.rate", "1800").toDoubleOrNull() ?: 1800.0

        var bundledCostPerDay = costPerOrDay
        if (includeAnesthesiologist) bundledCostPerDay += anesRate
        if (includeCRNA1) bundledCostPerDay += crnaRate

        val costPerHour = bundledCostPerDay / (config.getBinCapacity() / 60.0)
        
        val marginPerHour = estRevenuePerHour - costPerHour
        
        // If neither anesthesia options nor explicit revenue are provided, 
        // the margin might be misleadingly high, but we still return it 
        // as it represents the theoretical margin over the room footprint.
        return marginPerHour
    }

    /**
     * Calculate Cost of Variance for a set of rows compared to a benchmark mean.
     * Variance = Actual Duration - Benchmark Mean
     * Cost = Variance * (Base OR-Day Cost / 480)
     */
    fun calculateCostOfVariance(rows: List<ParsedRow>, globalProcedureMeans: Map<String, Double>): Double {
        val baseCostPerDay = config.getProperty("cost.per.or.day", "1500").toDoubleOrNull() ?: 1500.0
        val costPerMinute = baseCostPerDay / config.getBinCapacity()

        var totalCost = 0.0
        for (row in rows) {
            val proc = row.record?.procedure ?: continue
            val benchmarkMean = globalProcedureMeans[normalizeProcedure(proc)] ?: continue
            
            val actualDuration = row.durationMinutes.toDouble()
            if (actualDuration > benchmarkMean) {
                val varianceMinutes = actualDuration - benchmarkMean
                totalCost += varianceMinutes * costPerMinute
            }
        }
        return totalCost
    }

    private fun normalizeProcedure(proc: String): String {
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
        return firstWord.uppercase()
    }
}
