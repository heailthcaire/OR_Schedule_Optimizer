@file:JvmName("Main")
package org.heailth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.heailth.config.ConfigLoader
import org.heailth.model.*
import org.heailth.reader.CsvCaseReader
import org.heailth.report.HtmlReportWriter
import org.heailth.service.DataAggregator
import org.heailth.service.OptimizationService
import org.heailth.service.StatisticsService
import org.heailth.validation.CsvValidator
import org.heailth.validation.ValidationReportWriter
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val buildId = "2026-01-20T05:15:00"
    
    val logger = LoggerFactory.getLogger("org.heailth.Main")

    // Argument parsing using Kotlin idiomatic associate
    val argMap = args.filter { it.contains("=") }
        .associate { 
            val (key, value) = it.split("=", limit = 2)
            key to value.removeSurrounding("\"")
        }
    
    val hasDebug = args.contains("--debug") || System.getenv("OPTIMIZER_JSON_OUTPUT")?.lowercase() == "true"
    val isJsonOutput = args.contains("--json") || System.getenv("OPTIMIZER_JSON_OUTPUT")?.lowercase() == "true"

    if (!isJsonOutput) {
        logger.info("Java process started. Build ID: {}", buildId)
        logger.info("Arguments: {}", args.joinToString(", "))
        logger.info("hasDebug: {}, isJsonOutput: {}", hasDebug, isJsonOutput)
    }
    System.err.flush() // Force flush to ensure it's captured early

        if (hasDebug && !isJsonOutput) {
            logger.debug("--- DEBUG START ---")
            logger.debug("OPTIMIZER_CSV_PATH: {}", System.getenv("OPTIMIZER_CSV_PATH"))
            logger.debug("OPTIMIZER_CONFIG_PATH: {}", System.getenv("OPTIMIZER_CONFIG_PATH"))
            logger.debug("OPTIMIZER_CAPACITY: {}", System.getenv("OPTIMIZER_CAPACITY"))
            logger.debug("Working Dir: {}", System.getProperty("user.dir"))
            logger.debug("-------------------")
        }

    val configPath = resolveConfigPath(argMap)
    if (configPath == null) {
        logger.error("Configuration not found.")
        logger.error("Usage: --config=/path/to/app.properties [--base.path=/abs/path] [--capacity=480] [--startPad=15] [--endPad=15] [--json]")
        logger.error("Alternatively, ensure app.properties exists in target/classes/ or src/main/resources/")
        exitProcess(1)
    }

        if (hasDebug && !isJsonOutput) {
            logger.debug("Resolved Config: {}", configPath)
        }

    val csvOverride = argMap["--csv"] ?: System.getenv("OPTIMIZER_CSV_PATH")
    val crnaCsvOverride = argMap["--crnaCsv"] ?: System.getenv("OPTIMIZER_CRNA_CSV_PATH")
    val capacityOverride = argMap["--capacity"]?.toIntOrNull() ?: System.getenv("OPTIMIZER_CAPACITY")?.toIntOrNull()
    val startPadOverride = argMap["--startPad"]?.toIntOrNull() ?: System.getenv("OPTIMIZER_START_PAD")?.toIntOrNull()
    val endPadOverride = argMap["--endPad"]?.toIntOrNull() ?: System.getenv("OPTIMIZER_END_PAD")?.toIntOrNull()
    val processingModeOverride = argMap["--processing.mode"] ?: System.getenv("OPTIMIZER_PROCESSING_MODE")
    val basePath = argMap["--base.path"]

    // Additional overrides from environment variables (from Electron)
    val dateFormatOverride = System.getenv("OPTIMIZER_DATE_FORMAT")
    val timeFormatOverride = System.getenv("OPTIMIZER_TIME_FORMAT")
    val reportDetailOverride = System.getenv("OPTIMIZER_REPORT_DETAIL")
    val showSummaryGraphOverride = System.getenv("OPTIMIZER_SHOW_SUMMARY_GRAPH")?.toBoolean()
    val showDetailGraphOverride = System.getenv("OPTIMIZER_SHOW_DETAIL_GRAPH")?.toBoolean()
    val timeoutOverride = System.getenv("OPTIMIZER_TIMEOUT")?.toIntOrNull()
    val maxDurationOverride = System.getenv("OPTIMIZER_MAX_DURATION")?.toIntOrNull()
    val minDurationOverride = System.getenv("OPTIMIZER_MIN_DURATION")?.toIntOrNull()
    val skipSingleOverride = System.getenv("OPTIMIZER_SKIP_SINGLE")?.toBoolean()
    val reportPackagePathOverride = System.getenv("OPTIMIZER_REPORT_PACKAGE_PATH")

    val engineName1Override = System.getenv("OPTIMIZER_ENGINE_NAME1")
    val engineName2Override = System.getenv("OPTIMIZER_ENGINE_NAME2")

    // Column mappings from environment variables
    val colIdOverride = System.getenv("OPTIMIZER_COL_ID")
    val colDateOverride = System.getenv("OPTIMIZER_COL_DATE")
    val colSiteOverride = System.getenv("OPTIMIZER_COL_SITE")
    val colOrOverride = System.getenv("OPTIMIZER_COL_OR")
    val colSurgeonOverride = System.getenv("OPTIMIZER_COL_SURGEON")
    val colProcedureOverride = System.getenv("OPTIMIZER_COL_PROCEDURE")
    val colStartOverride = System.getenv("OPTIMIZER_COL_START")
    val colEndOverride = System.getenv("OPTIMIZER_COL_END")
    val colPatientInOverride = System.getenv("OPTIMIZER_COL_PATIENT_IN")
    val colPatientOutOverride = System.getenv("OPTIMIZER_COL_PATIENT_OUT")

    try {
        if (!isJsonOutput) logger.info("Using configuration file: {}", configPath)
        val config = ConfigLoader(
            configPath, basePath, capacityOverride, startPadOverride, endPadOverride, csvOverride,
            dateFormatOverride, timeFormatOverride, reportDetailOverride, showSummaryGraphOverride,
            showDetailGraphOverride, timeoutOverride, maxDurationOverride, minDurationOverride, skipSingleOverride,
            colIdOverride, colDateOverride, colSiteOverride, colOrOverride, colSurgeonOverride,
            colProcedureOverride, colStartOverride, colEndOverride, colPatientInOverride, colPatientOutOverride,
            processingModeOverride, reportPackagePathOverride
        )
        
        // Ensure siteToCanvasId is always pre-populated for all sites in config mappings too
        // and any sites that might appear in the data.
        // Actually, ReportDataService handles this dynamically.

        // Apply command line override for processing mode if present
        val effectiveProcessingMode = processingModeOverride?.let {
            try {
                ProcessingMode.valueOf(it.uppercase())
            } catch (e: Exception) {
                config.processingMode
            }
        } ?: config.processingMode

        if (!isJsonOutput) {
            logger.info("Configuration: CSV={}, Capacity={}, StartPad={}, EndPad={}",
                config.getCsvPath(), config.getBinCapacity(), config.startTimePad, config.endTimePad)
        } else if (hasDebug) {
            logger.debug("JSON_MODE: ON")
            logger.debug("CSV_PATH: {}", config.getCsvPath())
            logger.debug("CAPACITY: {}", config.getBinCapacity())
            logger.debug("START_PAD: {}", config.startTimePad)
            logger.debug("END_PAD: {}", config.endTimePad)
            logger.debug("SKIP_SINGLE: {}", config.isSkipSingleOr())
        }

        // 1. Validation & Normalization Step
        val validator = CsvValidator(config)
        if (!isJsonOutput) logger.debug("validator instance: {}", validator)
        // Only use the override if it's explicitly provided and NOT blank.
        // If it IS blank, it means the user explicitly wants NO file processed (e.g. from Dashboard)
        val orCsvPath = csvOverride ?: config.getCsvPath()
        val cleanedCsvPath = if (isJsonOutput) null else "$orCsvPath.cleaned.csv"
        
        if (hasDebug && !isJsonOutput) {
            logger.debug("Validating OR CSV: {} (override: {})", orCsvPath, csvOverride)
        }

        val validationResult = if (csvOverride != null && csvOverride.isNotBlank()) {
            val file = File(csvOverride)
            if (file.exists()) {
                if (!isJsonOutput) logger.debug("Validating OR CSV via override: {}", csvOverride)
                validator.validate(csvOverride, cleanedCsvPath, null, isJsonOutput)
            } else {
                if (!isJsonOutput) logger.debug("OR CSV override file NOT FOUND: {}", csvOverride)
                CsvValidator.ValidationResult(ValidationReport().apply { isHeaderRowPresent = false; incrementSkipped("File not found at: $csvOverride") }, emptyList())
            }
        } else if (csvOverride == null && orCsvPath.isNotBlank()) {
            val file = File(orCsvPath)
            if (file.exists()) {
                if (!isJsonOutput) logger.debug("Validating OR CSV via config: {}", orCsvPath)
                validator.validate(orCsvPath, cleanedCsvPath, null, isJsonOutput)
            } else {
                if (!isJsonOutput) logger.debug("OR CSV config file NOT FOUND: {}", orCsvPath)
                CsvValidator.ValidationResult(ValidationReport().apply { isHeaderRowPresent = false; incrementSkipped("File not found at: $orCsvPath") }, emptyList())
            }
        } else {
            if (!isJsonOutput) logger.debug("No OR CSV path provided to validate. csvOverride: '{}', orCsvPath: '{}'", csvOverride, orCsvPath)
            CsvValidator.ValidationResult(ValidationReport().apply { isHeaderRowPresent = false; incrementSkipped("No file path provided") }, emptyList())
        }

        // Validate CRNA if provided
        val crnaPath = crnaCsvOverride ?: config.getCrnaCsvPath()
        if (hasDebug && !isJsonOutput) {
            logger.debug("CRNA CSV Path Check: {} (override: {})", crnaPath, crnaCsvOverride)
        }
        val crnaValidationResult = if (crnaCsvOverride != null && crnaCsvOverride.isNotBlank()) {
            val file = File(crnaCsvOverride)
            if (file.exists()) {
                if (!isJsonOutput) logger.debug("Validating CRNA CSV via override: {}", crnaCsvOverride)
                validator.validate(crnaCsvOverride, null, config.getCrnaRequiredColumns(), isJsonOutput)
            } else {
                if (!isJsonOutput) logger.debug("CRNA CSV override file NOT FOUND: {}", crnaCsvOverride)
                CsvValidator.ValidationResult(ValidationReport().apply { isHeaderRowPresent = false; incrementSkipped("File not found at: $crnaCsvOverride") }, emptyList())
            }
        } else if (crnaCsvOverride == null && !crnaPath.isNullOrBlank()) {
            val file = File(crnaPath)
            if (file.exists()) {
                if (!isJsonOutput) logger.debug("Validating CRNA CSV via config: {}", crnaPath)
                validator.validate(crnaPath, null, config.getCrnaRequiredColumns(), isJsonOutput)
            } else {
                if (!isJsonOutput) logger.debug("CRNA CSV config file NOT FOUND: {}", crnaPath)
                CsvValidator.ValidationResult(ValidationReport().apply { isHeaderRowPresent = false; incrementSkipped("File not found at: $crnaPath") }, emptyList())
            }
        } else {
            if (!isJsonOutput) logger.debug("No CRNA CSV path provided to validate.")
            CsvValidator.ValidationResult(ValidationReport().apply { isHeaderRowPresent = false; incrementSkipped("No file path provided") }, emptyList())
        }

        val reportWriter = ValidationReportWriter()
        val terminalReport = reportWriter.generateTerminalReport(validationResult.report)
        if (!isJsonOutput) {
            logger.debug("--- OR Validation Debug ---")
            logger.debug("isHeaderRowPresent: {}", validationResult.report.isHeaderRowPresent)
            logger.debug("TotalValidRows: {}", validationResult.report.getTotalValidRows())
            logger.debug("OR Report: {}", terminalReport)
        }
        
        if (crnaValidationResult.report.isHeaderRowPresent || true) {
             val crnaTerminalReport = reportWriter.generateTerminalReport(crnaValidationResult.report)
             if (!isJsonOutput) {
                 logger.debug("--- CRNA Validation Debug ---")
                 logger.debug("isHeaderRowPresent: {}", crnaValidationResult.report.isHeaderRowPresent)
                 logger.debug("CRNA Report: {}", crnaTerminalReport)
             }
        }
        
        if (!isJsonOutput) {
            logger.info("{}", terminalReport)
            if (crnaValidationResult.report.isHeaderRowPresent) {
                logger.info("\n--- CRNA Validation Report ---")
                logger.info("{}", reportWriter.generateTerminalReport(crnaValidationResult.report))
            }
        } else {
            logger.info("{}", terminalReport)
            if (crnaValidationResult.report.isHeaderRowPresent) {
                logger.info("\n--- CRNA Validation Report ---")
                logger.info("{}", reportWriter.generateTerminalReport(crnaValidationResult.report))
            }
        }

        if (effectiveProcessingMode == ProcessingMode.VALIDATE_ONLY) {
            if (isJsonOutput) {
                val mapper = ObjectMapper().apply {
                    registerModule(JavaTimeModule())
                }
                println("JSON_START")
                val validationData = mutableMapOf<String, Any>(
                    "report" to validationResult.report,
                    "crnaReport" to crnaValidationResult.report,
                    "mode" to "VALIDATE_ONLY"
                )
                println(mapper.writeValueAsString(validationData))
                println("JSON_END")
            } else {
                logger.info("Validation complete. Mode: VALIDATE_ONLY")
            }
            return
        }

        if (effectiveProcessingMode == ProcessingMode.NORMALIZE_ONLY) {
            val normalizedPath = cleanedCsvPath ?: "${config.getCsvPath()}.normalized.csv"
            if (isJsonOutput) {
                val mapper = ObjectMapper().apply {
                    registerModule(JavaTimeModule())
                }
                println("JSON_START")
                val normalizationData = mapOf(
                    "report" to validationResult.report,
                    "cleanedPath" to normalizedPath,
                    "mode" to "NORMALIZE_ONLY"
                )
                println(mapper.writeValueAsString(normalizationData))
                println("JSON_END")
            } else {
                logger.info("Normalization complete. File saved to: $normalizedPath")
            }
            return
        }

        if (effectiveProcessingMode == ProcessingMode.DIAGNOSTIC) {
            runDiagnostics(isJsonOutput)
            return
        }

        val reader = CsvCaseReader(config)
        val validRows = reader.readFromValidated(validationResult.acceptedRows)
        
        val caseValidationReport = validationResult.report

        if (isJsonOutput) {
            logger.debug("TOTAL_ROWS: {}", caseValidationReport.getTotalRowsRead())
            logger.debug("TOTAL_VALID_ROWS: {}", caseValidationReport.getTotalValidRows())
            logger.debug("TOTAL_ERROR_ROWS: {}", caseValidationReport.getTotalErrorRows())
        } else {
            logger.info("Total rows read: {}, accepted: {}, skipped: {}", 
                caseValidationReport.getTotalRowsRead(), 
                caseValidationReport.getTotalValidRows(),
                caseValidationReport.getTotalErrorRows())
        }

        // 2. Process & Optimize
        val aggregator = DataAggregator()
        val grouped = aggregator.groupForOptimization(validRows, config)

        val crnaCsvPath = crnaCsvOverride ?: config.getCrnaCsvPath()
        var crnaValidationReport: ValidationReport? = null
        val crnaRows = if (!crnaCsvPath.isNullOrBlank() && File(crnaCsvPath).exists()) {
            val crnaResult = org.heailth.reader.CrnaCsvReader(config).readWithReport(crnaCsvPath)
            crnaValidationReport = crnaResult.second
            crnaResult.first
        } else {
            emptyList()
        }

        if (!isJsonOutput) logger.info("Site-day groups: {}", grouped.size)

        val optimizationService = OptimizationService()
        val dayResults = optimizationService.runOptimization(grouped, config)

        val roomsSavedTotal = dayResults.sumOf { it.roomsSavedDay }
        val roomDaysTotal = dayResults.sumOf { it.actualRoomsUsed }

        if (dayResults.isEmpty()) {
            logger.warn("No clinical data was processed. Day results are empty.")
        } else {
            logger.debug("Processed {} day results. Total rooms saved: {}", dayResults.size, roomsSavedTotal)
        }

        val financialService = org.heailth.service.FinancialMetricsService(config)

        // 3. Aggregate Results
        val weekSummaries = aggregator.aggregateWeekly(dayResults, config.getBinCapacity())

        // 4. Calculate Statistics
        val statsService = StatisticsService(financialService)
        val globalStaffing = statsService.calculateGlobalStaffing(validRows, crnaRows, config, dayResults)
        
        // Distribute extra CRNA hours to day results
        val updatedDayResults = statsService.distributeExtraCrnaHours(dayResults, globalStaffing, config)
        
        val siteStats = statsService.calculateSiteStatistics(validRows, crnaRows, config, globalStaffing)
        val globalStats = statsService.calculateGlobalStatistics(validRows, siteStats, globalStaffing)

        // 5. Prepare Global Metrics
        val validRecords = validRows.mapNotNull { it.record }
        
        var minDate: String? = "N/A"
        var maxDate: String? = "N/A"
        if (validRecords.isNotEmpty()) {
            minDate = validRecords.minOf { it.date }.toString()
            maxDate = validRecords.maxOf { it.date }.toString()
        }

        val siteCount = validRecords.map { it.site }.distinct().size
        val roomCount = validRecords.map { "${it.site} - ${it.orName}" }.distinct().size

        val totalAnesthesiologistsGlobal = dayResults.sumOf { dr ->
            dr.validCases.mapNotNull { it.record?.anesthesiologistName }.filter { it.isNotBlank() }.distinct().size
        }

        val globalAnesthesia = AnesthesiaSavings(
            method1Savings = dayResults.sumOf { it.anesthesiaSavings.method1Savings },
            method2Savings = dayResults.sumOf { it.anesthesiaSavings.method2Savings },
            method3Savings = dayResults.sumOf { it.anesthesiaSavings.method3Savings },
            totalAnesthesiaSavings = dayResults.sumOf { it.anesthesiaSavings.totalAnesthesiaSavings }
        )

        if (effectiveProcessingMode == ProcessingMode.CREATE_CFO_REPORT) {
            logger.debug("CREATE_CFO_REPORT mode detected")
            
            // Generate the main report.html first, as it's needed by the CFO package
            try {
                logger.info("Generating main report.html for CFO package...")
                val writer = HtmlReportWriter(config)
                writer.write(
                    weekSummaries, dayResults, emptyList(),
                    roomsSavedTotal, roomDaysTotal, minDate, maxDate, siteCount, roomCount, siteStats, globalStats,
                    totalAnesthesiologistsGlobal, globalAnesthesia, caseValidationReport, crnaValidationReport
                )
            } catch (e: Exception) {
                logger.error("Failed to generate main report.html: {}", e.message)
                if (isJsonOutput) {
                    println("JSON_START")
                    println("{\"status\": \"ERROR\", \"error\": \"Failed to generate main report.html: ${e.message}\"}")
                    println("JSON_END")
                    return
                }
            }

            val reportDataService = org.heailth.service.ReportDataService()
            logger.debug("Preparing context for CFO report")
            val context = reportDataService.prepareContext(
                config, weekSummaries, dayResults, emptyList(),
                roomsSavedTotal, roomDaysTotal, minDate, maxDate, siteCount, roomCount, siteStats, globalStats,
                totalAnesthesiologistsGlobal, globalAnesthesia, caseValidationReport, crnaValidationReport
            )
            
            if (dayResults.isEmpty()) {
                logger.warn("dayResults is EMPTY. CFO Report will contain $0 values.")
            }
            logger.debug("Initializing CfoReportService")
            val cfoService = org.heailth.service.CfoReportService(config)
            logger.debug("Finalizing CFO report")
            
            try {
                cfoService.finalizeCfoReport(context)
                logger.debug("CFO report finalized")
                
                if (isJsonOutput) {
                    println("JSON_START")
                    println("{\"status\": \"CFO_REPORT_CREATED\"}")
                    println("JSON_END")
                } else {
                    logger.info("CFO Report Package created successfully.")
                }
            } catch (e: Exception) {
                logger.error("Error creating CFO report: ${e.message}", e)
                if (isJsonOutput) {
                    println("JSON_START")
                    println("{\"status\": \"ERROR\", \"error\": \"${e.message}\"}")
                    println("JSON_END")
                }
            }
            return
        }

        if (isJsonOutput) {
            val overallUtilization = financialService.calculateAssetUtilization(dayResults, true)
            val currentUtilization = financialService.calculateAssetUtilization(dayResults, false)
            val totalDurationMinutes = dayResults.flatMap { it.validCases }.sumOf { it.durationMinutes }
            val siteDaysWithSavings = dayResults.count { it.roomsSavedDay > 0 }

            val summary = SummaryMetrics(
                totalActualRooms = roomDaysTotal,
                totalOptimizedRooms = roomDaysTotal - roomsSavedTotal,
                totalRoomsSaved = roomsSavedTotal,
                overallUtilization = Math.round(overallUtilization).toDouble(),
                currentUtilization = Math.round(currentUtilization).toDouble(),
                siteDays = grouped.size,
                siteDaysWithSavings = siteDaysWithSavings,
                totalDurationMinutes = totalDurationMinutes,
                totalAnesthesiologists = totalAnesthesiologistsGlobal,
                averageLaborYield = Math.round(globalStats.averageLaborYield).toDouble(),
                totalCostOfVariance = Math.round(globalStats.totalCostOfVariance).toDouble(),
                primeTimeUtilization = Math.round(globalStats.primeTimeUtilization).toDouble(),
                fcots = Math.round(globalStats.fcots).toDouble(),
                averageTot = Math.round(globalStats.averageTot).toDouble(),
                contributionMargin = Math.round(globalStats.contributionMargin).toDouble(),
                anesthesiaSavings = globalAnesthesia,
                totalCrnaHours = Math.round(globalStats.totalCrnaHours).toDouble(),
                scheduledCrnaHours = Math.round(globalStats.scheduledCrnaHours).toDouble(),
                requiredCrnaHours = Math.round(globalStats.requiredCrnaHours).toDouble(),
                extraCrnaHours = Math.round(globalStats.extraCrnaHours).toDouble(),
                crnaCostSaved = (globalStats.crnaCostSaved),
                crnaCurrentUtil = (globalStats.crnaCurrentUtil),
                crnaTargetUtil = (globalStats.crnaTargetUtil),
                avgActiveRooms = (globalStats.avgActiveRooms),
                maxActiveRooms = globalStats.maxActiveRooms,
                requiredAnesDays = globalStats.requiredAnesDays,
                scheduledAnesDays = globalStats.scheduledAnesDays,
                regionsCount = globalStats.regionsCount,
                startDate = globalStats.startDate,
                endDate = globalStats.endDate,
                engineName1 = config.getEngineName1(),
                engineName2 = config.getEngineName2()
            )

            logger.debug("GROUPS: {}", grouped.size)
            logger.debug("TOTAL_SAVINGS: {}", roomsSavedTotal)
            logger.debug("TOTAL_ROOMS: {}", roomDaysTotal)

            val statusCounts = dayResults.groupBy { it.status }
                .mapValues { it.value.size.toLong() }
            logger.debug("STATUS_COUNTS: {}", statusCounts)

            // 6. Generate Report
            val writer = HtmlReportWriter(config)
            writer.write(
                weekSummaries, updatedDayResults, emptyList(), roomsSavedTotal, roomDaysTotal,
                minDate, maxDate, siteCount, roomCount, siteStats, globalStats,
                totalAnesthesiologistsGlobal, globalAnesthesia
            )

            val mapper = ObjectMapper().apply {
                registerModule(JavaTimeModule())
            }
            println("JSON_START")
            val output = JsonOutput(summary, updatedDayResults)
            println(mapper.writeValueAsString(output))
            println("JSON_END")
            return
        }

        val savedCount = dayResults.count { it.roomsSavedDay > 0 }
        logger.info("Optimization complete. Site-days with savings: {}", savedCount)

        // 6. Generate Report
        val writer = HtmlReportWriter(config)
        writer.write(
            weekSummaries, updatedDayResults, emptyList(), roomsSavedTotal, roomDaysTotal,
            minDate, maxDate, siteCount, roomCount, siteStats, globalStats,
            totalAnesthesiologistsGlobal, globalAnesthesia
        )

        logger.info("Report generated successfully at: {}", config.getReportPath())

        // 7. Generate AI Strategic Prompt if enabled
        if (config.getProperty("reporting.generate.ai.prompt", "false").toBoolean()) {
            val promptService = org.heailth.service.StrategicPromptService()
            val aiPrompt = promptService.generateStrategicPrompt(globalStats, siteStats, dayResults, config)
            
            println("\n================================================================================")
            println("                 COPY-PASTE STRATEGIC AI PROMPT BELOW")
            println("================================================================================\n")
            println(aiPrompt)
            println("\n================================================================================")
        }
    } catch (e: Exception) {
        logger.error("Error during execution", e)
        exitProcess(1)
    }
}

private fun runDiagnostics(isJsonOutput: Boolean) {
    println("\n==========================================")
    println("      SYSTEM HEALTH & INTEGRITY CHECK")
    println("==========================================")
    
    val checks = mutableListOf<Pair<String, Boolean>>()
    val details = mutableMapOf<String, String>()
    
    // 1. JVM Check
    checks.add("Java Runtime (${System.getProperty("java.version")})" to true)
    
    // 2. Configuration File Check
    val configPath = resolveConfigPath(emptyMap())
    val configFile = configPath?.let { File(it) }
    val configExists = configFile?.exists() ?: false
    checks.add("Configuration File Presence" to configExists)
    if (configExists) {
        details["Configuration File Presence"] = "Found at: $configPath"
    } else {
        details["Configuration File Presence"] = "Not found in standard locations."
    }
    
    // 3. Configuration Integrity Check (Required Keys)
    var integrityPass = false
    if (configExists) {
        try {
            val config = ConfigLoader(configPath!!)
            // If it initializes, required keys are present (ConfigLoader.validate() runs in init)
            integrityPass = true
            details["Configuration Integrity"] = "All required key-value pairs are present."
        } catch (e: Exception) {
            details["Configuration Integrity"] = "Error: ${e.message}"
        }
    } else {
        details["Configuration Integrity"] = "Skipped (Config file missing)."
    }
    checks.add("Configuration Integrity" to integrityPass)

    // 4. Resource Check
    val sampleFile = File("src/main/resources/data/OR_data.csv")
    checks.add("Bundled Dataset Access" to sampleFile.exists())
    
    // 5. Engine Initialization Check
    var engineHealthy = false
    if (integrityPass) {
        try {
            val dummyConfig = ConfigLoader(configPath!!)
            val validator = CsvValidator(dummyConfig)
            engineHealthy = true
        } catch (e: Exception) {
            // Error already captured in integrity check or new error here
        }
    }
    checks.add("Optimization Engine Readiness" to engineHealthy)
    
    // Print Results
    var allPassed = true
    for ((name, passed) in checks) {
        val status = if (passed) "PASSED" else "FAILED"
        if (!passed) allPassed = false
        println(String.format("%-35s [%s]", name, status))
        details[name]?.let { println("   -> $it") }
    }
    
    println("------------------------------------------")
    if (allPassed) {
        println("STATUS: ALL SYSTEMS HEALTHY")
    } else {
        println("STATUS: SYSTEM HEALTH ISSUES DETECTED")
    }
    println("==========================================\n")

    if (isJsonOutput) {
        val mapper = ObjectMapper().apply { registerModule(JavaTimeModule()) }
        println("JSON_START")
        
        val summaryText = StringBuilder("Diagnostic complete.\n\nTested items:\n")
        checks.forEach { (name, passed) ->
            summaryText.append(if (passed) "✓ " else "✗ ").append(name)
            details[name]?.let { summaryText.append(" ($it)") }
            summaryText.append("\n")
        }
        
        println(mapper.writeValueAsString(mapOf(
            "success" to allPassed,
            "checks" to checks.map { mapOf("name" to it.first, "passed" to it.second, "detail" to details[it.first]) },
            "output" to summaryText.toString()
        )))
        println("JSON_END")
    }
}

private fun resolveConfigPath(argMap: Map<String, String>): String? {
    val envPath = System.getenv("OPTIMIZER_CONFIG_PATH")
    if (!envPath.isNullOrBlank()) return envPath

    argMap["--config"]?.let { return it }

    val defaults = arrayOf("target/classes/app.properties", "src/main/resources/app.properties")
    return defaults.firstOrNull { File(it).exists() }
}
