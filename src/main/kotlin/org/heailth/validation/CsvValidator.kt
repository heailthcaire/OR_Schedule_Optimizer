package org.heailth.validation

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.heailth.config.ConfigLoader
import org.heailth.model.ValidationReport
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class CsvValidator(private val config: ConfigLoader) {
    private val logger = LoggerFactory.getLogger(CsvValidator::class.java)
    private val headerValidator = HeaderValidator(config)
    private val normalizer = ColumnNormalizer(config)
    private val rowValidator: RowValidator

    init {
        val pluggable = mutableListOf<ColumnValidator>()
        pluggable.add(ICD10PCSValidator(config))
        this.rowValidator = RowValidator(config, normalizer, pluggable)
    }

    @Throws(IOException::class)
    fun validate(inputPath: String, outputPath: String?, requiredColumns: List<String>? = null, isJsonOutput: Boolean = false): ValidationResult {
        if (!isJsonOutput && logger.isDebugEnabled) logger.debug("validate() called for: {}", inputPath)
        if (inputPath.isBlank() || !Files.exists(Paths.get(inputPath))) {
            if (!isJsonOutput && logger.isDebugEnabled) logger.debug("File blank or NOT FOUND: '{}'", inputPath)
            return ValidationResult(ValidationReport().apply { isHeaderRowPresent = false }, emptyList())
        }
        if (!isJsonOutput && logger.isDebugEnabled) logger.debug("File exists: {}", inputPath)
        val report = ValidationReport()
        val acceptedRows = mutableListOf<Map<String, String>>()
        val rejectedRows = mutableListOf<Pair<Map<String, String>, String>>()
        val seenIds = mutableSetOf<String>()
        val idColumn = if (requiredColumns == null) config.getIdColumn() else null
        val reqCols = requiredColumns ?: config.csvRequiredColumns

        val formatBuilder = CSVFormat.DEFAULT.builder()
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .setAllowMissingColumnNames(true)

        if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Entering use block for {}", inputPath)
        val inputPathObj = Paths.get(inputPath)
        
        // Optimize CSV Parsing Buffers: Increase buffer size to 128KB
        java.io.BufferedReader(java.io.InputStreamReader(Files.newInputStream(inputPathObj)), 128 * 1024).use { reader ->
            if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Reader opened for: {}", inputPath)
            // Skip leading empty lines or lines with only commas
            var headerLine: String? = null
            var lineCount = 0
            while (true) {
                lineCount++
                reader.mark(8192)
                var line = reader.readLine() ?: break
                val trimmed = line.trim()
                
                // Detailed character-level debug for the first few lines
                if (lineCount <= 10 && !isJsonOutput && logger.isDebugEnabled) {
                    val hex = line.map { String.format("%04X", it.toInt()) }.joinToString(" ")
                    logger.debug("Line {}: '{}' (Length: {}, Hex: {})", lineCount, line, line.length, hex)
                }

                if (trimmed.replace(",", "").isNotEmpty()) {
                    // Check and strip BOM if present on the first non-empty line
                    if (line.startsWith("\ufeff")) {
                        if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Detected and stripping BOM from Line {}", lineCount)
                        line = line.substring(1)
                    }
                    
                    // Simple heuristic: If first non-empty line has no commas, it's unlikely to be a valid header
                    // or if it has commas, we'll check if it matches expected headers later.
                    // But if it's just raw data like "1001,2025-01-01...", it might look like a header to CSVParser.
                    
                    headerLine = line
                    if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Potential header found on line {}: {}", lineCount, headerLine)
                    reader.reset() // Positioned AT the header line
                    break
                }
            }

            val format = if (headerLine != null) {
                if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Building format with header. HeaderLine: {}", headerLine)
                formatBuilder.setHeader().setSkipHeaderRecord(true).build()
            } else {
                if (!isJsonOutput && logger.isDebugEnabled) logger.debug("No non-empty lines found in file.")
                formatBuilder.build()
            }

            if (!isJsonOutput && logger.isDebugEnabled) logger.debug("About to create CSVParser")
            CSVParser(reader, format).use { parser ->
                if (!isJsonOutput && logger.isDebugEnabled) logger.debug("CSVParser initialized. RecordNumber: {}, CurrentLineNumber: {}", parser.recordNumber, parser.currentLineNumber)
                // 1. Header Validation
                val headerMap = parser.headerMap
                
                val headerResult = headerValidator.validateAndMap(headerMap ?: emptyMap(), reqCols)
                headerResult.missingHeaders.forEach { 
                    if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Adding missing header to report: {}", it)
                    report.addMissingHeader(it) 
                }
                headerResult.actualHeaders.forEach { report.addFoundHeader(it) }

                // Determine if header row is present based on whether we matched any required columns
                // or if the first line was definitely not a header.
                if (headerMap == null || headerMap.isEmpty() || headerResult.requiredToActual.size < (reqCols.size / 2)) {
                    if (!isJsonOutput && logger.isDebugEnabled) logger.debug("headerMap is null/empty or too few required columns matched. Matched: {}, Required: {}. Found headers: {}", headerResult.requiredToActual.size, reqCols.size, headerResult.actualHeaders)
                    
                    report.isHeaderRowPresent = false
                    // We return empty results to signal header missing
                    return ValidationResult(report, acceptedRows)
                }

                report.isHeaderRowPresent = true 

                if (headerResult.missingHeaders.isNotEmpty()) {
                    if (!isJsonOutput && logger.isDebugEnabled) logger.debug("Failing fast due to missing required headers: {}", headerResult.missingHeaders)
                    return ValidationResult(report, acceptedRows)
                }

                val requiredToActual = headerResult.requiredToActual

                // 2. Row Processing
                for (record in parser) {
                    report.incrementRead()
                    
                    if (idColumn != null) {
                        val actualIdCol = requiredToActual[idColumn]
                        if (actualIdCol != null) {
                            val idValue = record[actualIdCol]
                            if (idValue != null && !seenIds.add(idValue)) {
                                val reason = "Duplicate Case ID: $idValue"
                                report.incrementSkipped(reason)
                                rejectedRows.add(record.toMap() to reason)
                                continue
                            }
                        }
                    }

                    val outcome = rowValidator.validate(record, requiredToActual, report, isJsonOutput)
                    if (outcome.accepted) {
                        acceptedRows.add(outcome.normalizedData!!)
                        if (outcome.corrected) report.incrementAcceptedCorrected()
                        else report.incrementAcceptedOriginal()
                    } else {
                        val reason = outcome.skipReason ?: "Unknown"
                        report.incrementSkipped(reason)
                        rejectedRows.add(record.toMap() to reason)
                    }
                }
            }
        }

        // 3. Write Cleaned CSV
        if (outputPath != null) {
            if (acceptedRows.isNotEmpty()) {
                writeCleanedCsv(acceptedRows, outputPath, reqCols)
            }
            if (rejectedRows.isNotEmpty()) {
                writeRejectedCsv(rejectedRows, outputPath.replace(".csv", ".rejected.csv"))
            }
        }

        return ValidationResult(report, acceptedRows)
    }

    @Throws(IOException::class)
    private fun writeRejectedCsv(rows: List<Pair<Map<String, String>, String>>, outputPath: String) {
        if (rows.isEmpty()) return

        val originalHeaders = rows.first().first.keys.toList()
        val headersWithReason = originalHeaders + "Error_Reason"
        val format = CSVFormat.DEFAULT.builder().setHeader(*headersWithReason.toTypedArray()).build()

        Files.newBufferedWriter(Paths.get(outputPath)).use { writer ->
            CSVPrinter(writer, format).use { printer ->
                for ((row, reason) in rows) {
                    val values = originalHeaders.map { row[it] } + reason
                    printer.printRecord(values)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun writeCleanedCsv(rows: List<Map<String, String>>, outputPath: String, requiredColumns: List<String>? = null) {
        if (rows.isEmpty()) return

        val headers = (requiredColumns ?: config.csvRequiredColumns).toTypedArray()
        val format = CSVFormat.DEFAULT.builder().setHeader(*headers).build()

        Files.newBufferedWriter(Paths.get(outputPath)).use { writer ->
            CSVPrinter(writer, format).use { printer ->
                for (row in rows) {
                    val values = headers.map { row[it] }
                    printer.printRecord(values)
                }
            }
        }
    }

    data class ValidationResult(val report: ValidationReport, val acceptedRows: List<Map<String, String>>)
}
