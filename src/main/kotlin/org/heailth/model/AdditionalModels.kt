package org.heailth.model

import com.fasterxml.jackson.annotation.JsonProperty

@JvmRecord
data class WeekKey(val site: String, val week: String)

@JvmRecord
data class WeekSummary(
    val weekKey: WeekKey,
    val daysCount: Int,
    val totalCases: Int,
    val sumActualRoomsUsed: Int,
    val sumOptimizedRoomsUsed: Int,
    val totalRoomsSaved: Int,
    val totalMinutes: Long,
    val totalCapacityMinutes: Long,
    val totalUnusedMinutes: Long
)

@JvmRecord
data class ProcedureComparison(
    val procedure: String,
    val globalMean: Double,
    val minSite: String,
    val minMean: Double,
    val maxSite: String,
    val maxMean: Double
)

@JvmRecord
data class StatisticsResult(
    val name: String,
    val procedureFrequency: Map<String, Long>,
    val averageDuration: Double,
    val variance: Double,
    val stdDev: Double,
    val min: Double,
    val max: Double,
    val p25: Double,
    val p75: Double,
    val p90: Double,
    val p95: Double,
    val totalProcedures: Long,
    val maxUniqueOrs: Int,
    val totalRoomDays: Int = 0,
    val topProcedureMeans: Map<String, Double>,
    val procedureComparisons: List<ProcedureComparison>,
    val averageLaborYield: Double = 0.0,
    val totalCostOfVariance: Double = 0.0,
    val primeTimeUtilization: Double = 0.0,
    val fcots: Double = 0.0,
    val averageTot: Double = 0.0,
    val contributionMargin: Double = 0.0,
    val totalCrnaHours: Double = 0.0,
    val scheduledCrnaHours: Double = 0.0,
    val requiredCrnaHours: Double = 0.0,
    val extraCrnaHours: Double = 0.0,
    val crnaCostSaved: Double = 0.0,
    val crnaCurrentUtil: Double = 0.0,
    val crnaTargetUtil: Double = 0.0,
    val avgActiveRooms: Double = 0.0,
    val maxActiveRooms: Int = 0,
    val requiredAnesDays: Int = 0,
    val scheduledAnesDays: Int = 0,
    val regionsCount: Int = 0,
    val startDate: java.time.LocalDate? = null,
    val endDate: java.time.LocalDate? = null
) : Comparable<StatisticsResult> {
    override fun compareTo(other: StatisticsResult): Int {
        return this.maxUniqueOrs.compareTo(other.maxUniqueOrs)
    }
}

@JvmRecord
data class ValidationOutcome(
    val accepted: Boolean,
    val corrected: Boolean,
    val normalizedData: Map<String, String>?,
    val skipReason: String?
)

@JvmRecord
data class SummaryMetrics(
    val totalActualRooms: Int,
    val totalOptimizedRooms: Int,
    val totalRoomsSaved: Int,
    val overallUtilization: Double,
    val currentUtilization: Double = 0.0,
    val siteDays: Int,
    val siteDaysWithSavings: Int,
    val totalDurationMinutes: Long,
    val totalAnesthesiologists: Int = 0,
    val averageLaborYield: Double = 0.0,
    val totalCostOfVariance: Double = 0.0,
    val primeTimeUtilization: Double = 0.0,
    val fcots: Double = 0.0,
    val averageTot: Double = 0.0,
    val contributionMargin: Double = 0.0,
    val anesthesiaSavings: AnesthesiaSavings = AnesthesiaSavings(0, 0, 0, 0),
    val totalCrnaHours: Double = 0.0,
    val scheduledCrnaHours: Double = 0.0,
    val requiredCrnaHours: Double = 0.0,
    val extraCrnaHours: Double = 0.0,
    val crnaCostSaved: Double = 0.0,
    val crnaCurrentUtil: Double = 0.0,
    val crnaTargetUtil: Double = 0.0,
    val avgActiveRooms: Double = 0.0,
    val maxActiveRooms: Int = 0,
    val requiredAnesDays: Int = 0,
    val scheduledAnesDays: Int = 0,
    val regionsCount: Int = 0,
    val startDate: java.time.LocalDate? = null,
    val endDate: java.time.LocalDate? = null,
    val engineName1: String = "",
    val engineName2: String = ""
)

@JvmRecord
data class AnesthesiaSavings(
    val method1Savings: Int,
    val method2Savings: Int,
    val method3Savings: Int,
    val totalAnesthesiaSavings: Int
)

@JvmRecord
data class JsonOutput(
    val summary: SummaryMetrics,
    val results: List<DayGroupResult>
)

class ValidationReport {
    private var _totalRowsRead: Long = 0
    private var _acceptedOriginal: Long = 0
    private var _acceptedCorrected: Long = 0
    private var _procedureCodeViolations: Long = 0

    val skippedReasons: MutableMap<String, Long> = mutableMapOf()
    val formatViolations: MutableMap<String, Long> = mutableMapOf()
    val requiredFieldNulls: MutableMap<String, Long> = mutableMapOf()
    val missingHeaders: MutableList<String> = mutableListOf()
    val foundHeaders: MutableList<String> = mutableListOf()
    
    @get:JsonProperty("isHeaderRowPresent")
    var isHeaderRowPresent: Boolean = true

    fun incrementRead() { _totalRowsRead++ }
    fun incrementAcceptedOriginal() { _acceptedOriginal++ }
    fun incrementAcceptedCorrected() { _acceptedCorrected++ }
    fun incrementSkipped(reason: String?) {
        val key = reason ?: "Unknown"
        skippedReasons[key] = (skippedReasons[key] ?: 0L) + 1
    }
    fun incrementFormatViolation(field: String) {
        formatViolations[field] = (formatViolations[field] ?: 0L) + 1
    }
    fun incrementRequiredFieldNull(field: String) {
        requiredFieldNulls[field] = (requiredFieldNulls[field] ?: 0L) + 1
    }
    fun incrementProcedureCodeViolation() { _procedureCodeViolations++ }
    fun addMissingHeader(header: String) { missingHeaders.add(header) }
    fun addFoundHeader(header: String) { foundHeaders.add(header) }

    // Explicit Getters for compatibility and to avoid platform clash
    fun getTotalRowsRead(): Long = _totalRowsRead
    fun getRowsAcceptedOriginal(): Long = _acceptedOriginal
    fun getRowsAcceptedCorrected(): Long = _acceptedCorrected
    fun getRowsSkipped(): Long = skippedReasons.values.sum()
    fun getTotalValidRows(): Long = _acceptedOriginal + _acceptedCorrected
    fun getTotalErrorRows(): Long = getRowsSkipped()
    fun getSkipReasons(): Map<String, Long> = skippedReasons
    fun getRequiredFieldNullCounts(): Map<String, Long> = requiredFieldNulls
    fun getFormatViolationCounts(): Map<String, Long> = formatViolations
    fun getIcd10ValidCount(): Long = _acceptedOriginal + _acceptedCorrected
    fun getIcd10CorrectedCount(): Long = _acceptedCorrected
    fun getIcd10InvalidCount(): Long = _procedureCodeViolations
}
