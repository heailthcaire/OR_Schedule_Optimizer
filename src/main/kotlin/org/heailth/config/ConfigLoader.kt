package org.heailth.config

import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.heailth.model.ProcessingMode
import org.heailth.util.DateTimeParser
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class ConfigLoader @JvmOverloads constructor(
    configPath: String,
    basePath: String? = null,
    private val binCapacityOverride: Int? = null,
    private val startTimePadOverride: Int? = null,
    private val endTimePadOverride: Int? = null,
    private val csvPathOverride: String? = null,
    private val dateFormatOverride: String? = null,
    private val timeFormatOverride: String? = null,
    private val reportDetailOverride: String? = null,
    private val showSummaryGraphOverride: Boolean? = null,
    private val showDetailGraphOverride: Boolean? = null,
    private val timeoutOverride: Int? = null,
    private val maxDurationOverride: Int? = null,
    private val minDurationOverride: Int? = null,
    private val skipSingleOverride: Boolean? = null,
    private val colIdOverride: String? = null,
    private val colDateOverride: String? = null,
    private val colSiteOverride: String? = null,
    private val colOrOverride: String? = null,
    private val colSurgeonOverride: String? = null,
    private val colProcedureOverride: String? = null,
    private val colStartOverride: String? = null,
    private val colEndOverride: String? = null,
    private val colPatientInOverride: String? = null,
    private val colPatientOutOverride: String? = null,
    private val processingModeOverride: String? = null,
    private val reportPackagePathOverride: String? = null
) {
    private val config: Configuration
    private val pathResolver: PathResolver

    val csvRequiredColumns: List<String>
    private val crnaRequiredColumns: List<String>
    val absoluteStartTime: LocalTime?
    val absoluteEndTime: LocalTime?
    val processingMode: ProcessingMode
    val validationMode: String
    val minRowCount: Int
    val isAutoCorrectionEnabled: Boolean
    val isIcd10ValidationEnabled: Boolean
    val procedurePaddings: Map<String, Pair<Int, Int>>
    val clusters: Map<String, List<String>>
    val siteToCluster: Map<String, String>
    val invalidRequiredValues: Set<String>
    val columnSkipValues: Map<String, Set<String>>
    val regionToSites: Map<String, List<String>>
    val siteToRegion: Map<String, String>
    val columnConstraints: Map<String, ColumnConstraint>

    data class ColumnConstraint(
        val type: String? = null,
        val maxLength: Int? = null,
        val minLength: Int? = null,
        val minValue: Double? = null,
        val maxValue: Double? = null,
        val pattern: String? = null
    )

    val startTimePad: Int
        get() = startTimePadOverride ?: config.getInt("start.time.pad.minutes", 0)

    val endTimePad: Int
        get() = endTimePadOverride ?: config.getInt("end.time.pad.minutes", 0)

    init {
        val configs = Configurations()
        config = configs.properties(File(configPath))
        pathResolver = PathResolver(basePath)
        validate()

        // Pre-calculate frequently used values
        csvRequiredColumns = config.getString("csv.required.columns").split(",")
            .map { it.trim() }

        crnaRequiredColumns = config.getString("crna.csv.required.columns", "region,Name,Date,Duration").split(",")
            .map { it.trim() }

        absoluteStartTime = config.getString("absolute.start.time", "").trim().let {
            if (it.isEmpty()) null else parseFlexibleTime(it)
        }

        absoluteEndTime = config.getString("absolute.end.time", "").trim().let {
            if (it.isEmpty()) null else parseFlexibleTime(it)
        }

        processingMode = try {
            val modeStr = processingModeOverride ?: config.getString("processing.mode", "FULL")
            ProcessingMode.valueOf(modeStr.uppercase())
        } catch (e: Exception) {
            ProcessingMode.FULL
        }

        validationMode = config.getString("validation.mode", "VALIDATE_AND_PROCESS").uppercase()
        minRowCount = config.getInt("validation.min.row.count", 1)
        isAutoCorrectionEnabled = config.getBoolean("validation.auto.correction.enabled", true)
        isIcd10ValidationEnabled = config.getBoolean("validation.icd10.enabled", false)

        // Parse procedure-specific paddings
        // format: padding.procedure.COLONOSCOPY = 10,10
        val tempPaddings = mutableMapOf<String, Pair<Int, Int>>()
        config.keys.forEachRemaining { key ->
            if (key.startsWith("padding.procedure.")) {
                val procedureName = key.substring("padding.procedure.".length).uppercase()
                val values = config.getString(key).split(",")
                if (values.size == 2) {
                    val start = values[0].trim().toIntOrNull() ?: 0
                    val end = values[1].trim().toIntOrNull() ?: 0
                    tempPaddings[procedureName] = Pair(start, end)
                }
            }
        }
        procedurePaddings = tempPaddings

        // Parse clusters
        // format: cluster.<NAME> = Site A, Site B
        val tempClusters = mutableMapOf<String, List<String>>()
        val tempSiteToCluster = mutableMapOf<String, String>()
        config.keys.forEachRemaining { key ->
            if (key.startsWith("cluster.")) {
                val clusterName = key.substring("cluster.".length).uppercase()
                val sites = config.getString(key).split(",").map { it.trim() }
                tempClusters[clusterName] = sites
                sites.forEach { site -> tempSiteToCluster[site] = clusterName }
            }
        }
        clusters = tempClusters
        siteToCluster = tempSiteToCluster

        invalidRequiredValues = config.getString("csv.required.columns.invalid", "")
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        val tempSkipMap = mutableMapOf<String, Set<String>>()
        config.keys.forEachRemaining { key ->
            if (key.startsWith("skip.row.")) {
                val columnName = key.substring("skip.row.".length)
                val values = config.getString(key).split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                tempSkipMap[columnName] = values
            }
        }
        columnSkipValues = tempSkipMap

        // Parse regional mappings
        val tempRegionToSites = mutableMapOf<String, List<String>>()
        val tempSiteToRegion = mutableMapOf<String, String>()
        config.keys.forEachRemaining { key ->
            if (key.startsWith("region.")) {
                val regionName = key.substring("region.".length)
                val sites = config.getString(key).split(",").map { it.trim() }
                tempRegionToSites[regionName] = sites
                sites.forEach { site -> tempSiteToRegion[site] = regionName }
            }
        }
        regionToSites = tempRegionToSites
        siteToRegion = tempSiteToRegion

        // Parse column constraints
        val tempConstraints = mutableMapOf<String, ColumnConstraint>()
        config.keys.forEachRemaining { key ->
            if (key.startsWith("column.constraint.")) {
                val parts = key.split(".")
                if (parts.size >= 4) {
                    val columnName = parts[2]
                    val attribute = parts[3]
                    val existing = tempConstraints.getOrPut(columnName) { ColumnConstraint() }
                    
                    val updated = when (attribute) {
                        "type" -> existing.copy(type = config.getString(key))
                        "maxLength" -> existing.copy(maxLength = config.getInt(key))
                        "minLength" -> existing.copy(minLength = config.getInt(key))
                        "minValue" -> existing.copy(minValue = config.getDouble(key))
                        "maxValue" -> existing.copy(maxValue = config.getDouble(key))
                        "pattern" -> existing.copy(pattern = config.getString(key))
                        else -> existing
                    }
                    tempConstraints[columnName] = updated
                }
            }
        }
        columnConstraints = tempConstraints
    }

    private fun validate() {
        val requiredKeys = arrayOf(
            "csv.path", "report.path", "id.column", "date.column", "site.column", "or.column",
            "procedure.column", "csv.required.columns", "anesthesia.start.time.column",
            "anesthesia.end.time.column", "or.occupancy.start.time.column", "or.occupancy.end.time.column", "bin.capacity.minutes"
        )
        for (key in requiredKeys) {
            if (!config.containsKey(key) || config.getString(key).isBlank()) {
                throw RuntimeException("Missing required configuration key: $key")
            }
        }

        // Validate bin.capacity.minutes is a positive integer
        val capacity = config.getInt("bin.capacity.minutes", -1)
        if (capacity <= 0) {
            throw RuntimeException("bin.capacity.minutes must be a positive integer. Found: $capacity")
        }

        // Validate date and time format patterns
        val dateFormat = config.getString("date.format")
        if (dateFormat.isNullOrBlank()) {
            throw RuntimeException("date.format must be defined")
        }
        try {
            java.time.format.DateTimeFormatter.ofPattern(dateFormat)
        } catch (e: Exception) {
            throw RuntimeException("Invalid date.format pattern: $dateFormat")
        }

        val timeFormat = config.getString("time.format")
        if (timeFormat.isNullOrBlank()) {
            throw RuntimeException("time.format must be defined")
        }
        try {
            java.time.format.DateTimeFormatter.ofPattern(timeFormat)
        } catch (e: Exception) {
            throw RuntimeException("Invalid time.format pattern: $timeFormat")
        }
    }

    fun getCsvPath(): String {
        return csvPathOverride ?: pathResolver.resolve(config.getString("csv.path"))!!
    }

    fun getCrnaCsvPath(): String? {
        val path = config.getString("crna.csv.path", null)
        return if (path != null) pathResolver.resolve(path) else null
    }

    fun getCrnaRequiredColumns(): List<String> = crnaRequiredColumns

    fun getReportPath(): String {
        return pathResolver.resolve(config.getString("report.path"))!!
    }

    fun getEncoding(): String = config.getString("encoding", "UTF-8")
    fun getDateFormat(): String = dateFormatOverride ?: config.getString("date.format", "yyyy-MM-dd")
    fun getTimeFormat(): String = timeFormatOverride ?: config.getString("time.format", "HH:mm")
    fun isSkipSingleOr(): Boolean = skipSingleOverride ?: config.getBoolean("skip.single.or", true)
    fun getMaxProcedureDurationHours(): Int = maxDurationOverride ?: config.getInt("max.procedure.duration.hours", 12)
    fun getMinProcedureDurationMinutes(): Int = minDurationOverride ?: config.getInt("min.procedure.duration.minutes", 15)

    fun getIdColumn(): String = colIdOverride ?: config.getString("id.column")
    fun getDateColumn(): String = colDateOverride ?: config.getString("date.column")
    fun getSiteColumn(): String = colSiteOverride ?: config.getString("site.column")
    fun getOrColumn(): String = colOrOverride ?: config.getString("or.column")
    fun getSurgeonColumn(): String = colSurgeonOverride ?: config.getString("surgeon.column")
    fun getProcedureColumn(): String = colProcedureOverride ?: config.getString("procedure.column")
    fun getAnesthesiologistColumn(): String = config.getString("anesthesiologist.column", "Provider_Name")

    fun getStartTimeField(): String = colStartOverride ?: config.getString("anesthesia.start.time.column")
    fun getEndTimeField(): String = colEndOverride ?: config.getString("anesthesia.end.time.column")
    fun getPatientInColumn(): String = colPatientInOverride ?: config.getString("or.occupancy.start.time.column")
    fun getPatientOutColumn(): String = colPatientOutOverride ?: config.getString("or.occupancy.end.time.column")

    fun getCrnaRegionColumn(): String = config.getString("crna.region.column", "region")
    fun getCrnaNameColumn(): String = config.getString("crna.name.column", "Name")
    fun getCrnaDateColumn(): String = config.getString("crna.date.column", "Date")
    fun getCrnaDurationColumn(): String = config.getString("crna.duration.column", "Duration")
    
    fun getCrnaProductivityFactor(): Double {
        val value = config.getString("crna.productivity.factor", "0.85")
        return value.toDoubleOrNull()?.coerceIn(0.5, 1.0) ?: 0.85
    }

    fun getBinCapacity(): Int {
        if (binCapacityOverride != null) return binCapacityOverride
        val start = absoluteStartTime
        val end = absoluteEndTime

        if (start != null && end != null) {
            val diff = ChronoUnit.MINUTES.between(start, end)
            if (diff > 0) {
                return diff.toInt()
            } else {
                logger.warn("Absolute end time {} is not after start time {}. Falling back to bin.capacity.minutes.", end, start)
            }
        }
        return config.getInt("bin.capacity.minutes")
    }

    fun parseFlexibleTime(timeStr: String): LocalTime? {
        return DateTimeParser.parseTime(timeStr, getTimeFormat())
    }

    fun getReportDetailLevel(): String {
        return reportDetailOverride ?: config.getString("report.detail.level", "high").lowercase().trim()
    }

    fun isShowSummaryGraph(): Boolean = showSummaryGraphOverride ?: config.getBoolean("show.summary.graph", true)
    fun isShowDetailGraph(): Boolean = showDetailGraphOverride ?: config.getBoolean("show.detail.graph", true)

    fun isAnesBlocksSavedEnabled(): Boolean = config.getBoolean("modeling.anes.blocks.saved", true)
    fun isAnesFteEfficiencyEnabled(): Boolean = config.getBoolean("modeling.anes.fte.efficiency", true)
    fun isAnesAbsorptionEnabled(): Boolean = config.getBoolean("modeling.anes.absorption", true)
    fun getAnesPaddingMinutes(): Int = config.getInt("modeling.anes.padding.minutes", 0)

    fun getOptimizationTimeout(): Int = timeoutOverride ?: config.getInt("optimization.timeout.seconds", 10)

    fun getBasePath(): String? {
        val resolved = pathResolver.resolve(".") ?: return null
        return if (resolved == ".") null else resolved.removeSuffix("/.")
    }

    fun getEngineName1(): String = config.getString("analysis.engine.name1", "HEalLTHCaiRE.ai Quark (v1.7)")
    fun getEngineName2(): String = config.getString("analysis.engine.name2", "Google OR-Tools (v9.8)")

    fun getReportPackagePath(): String = reportPackagePathOverride ?: config.getString("report.package.path", "src/main/resources/report/CFO_Report_Package.zip")

    fun getProperty(key: String, defaultValue: String): String = config.getString(key, defaultValue)

    companion object {
        private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)
    }
}
