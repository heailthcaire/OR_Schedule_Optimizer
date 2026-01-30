package org.heailth.reader

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.heailth.config.ConfigLoader
import org.heailth.model.CaseRecord
import org.heailth.model.ParsedRow
import org.heailth.util.DateTimeParser
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class CsvCaseReader(private val config: ConfigLoader) {
    private val validator = CaseValidator(config)

    fun readFromValidated(acceptedRows: List<Map<String, String>>): List<ParsedRow> {
        return acceptedRows.mapIndexed { index, data ->
            parseMap(data, index.toLong() + 1)
        }
    }

    private fun parseMap(data: Map<String, String>, rowNum: Long): ParsedRow {
        return try {
            val id = data[config.getIdColumn()] ?: ""
            val site = data[config.getSiteColumn()] ?: ""
            val dateStr = data[config.getDateColumn()] ?: ""
            val orName = data[config.getOrColumn()] ?: ""
            val surgeon = data[config.getSurgeonColumn()]
            val procedure = data[config.getProcedureColumn()]
            val anesthesiologist = data[config.getAnesthesiologistColumn()]
            val startTimeStr = data[config.getStartTimeField()] ?: ""
            val endTimeStr = data[config.getEndTimeField()] ?: ""
            val patientInStr = data[config.getPatientInColumn()] ?: ""
            val patientOutStr = data[config.getPatientOutColumn()] ?: ""

            val date = DateTimeParser.parseDate(dateStr, config.getDateFormat())
            val csvStart = DateTimeParser.parseTime(startTimeStr, config.getTimeFormat())
            val csvEnd = DateTimeParser.parseTime(endTimeStr, config.getTimeFormat())
            val patientIn = DateTimeParser.parseTime(patientInStr, config.getTimeFormat())
            val patientOut = DateTimeParser.parseTime(patientOutStr, config.getTimeFormat())

            if (date == null || csvStart == null || csvEnd == null || patientIn == null || patientOut == null) {
                val unparseable = when {
                    date == null -> "Date"
                    csvStart == null -> "Start Time"
                    csvEnd == null -> "End Time"
                    patientIn == null -> "Patient In Room Time"
                    else -> "Patient Out Room Time"
                }
                return ParsedRow(rowNum, null, null, null, null, null, 0, false, "Invalid format or unparseable value for: $unparseable", false)
            }

            val record = CaseRecord(id, site, date, orName, surgeon, procedure, csvStart, csvEnd, patientIn, patientOut, anesthesiologist)
            validator.validate(rowNum, record, null)
        } catch (e: Exception) {
            ParsedRow(rowNum, null, null, null, null, null, 0, false, "Internal parsing error: ${e.message}", false)
        }
    }

    @Throws(IOException::class)
    fun read(): List<ParsedRow> {
        val format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build()

        Files.newBufferedReader(Paths.get(config.getCsvPath())).use { reader ->
            reader.mark(1)
            if (reader.read() != 0xFEFF) {
                reader.reset()
            }

            CSVParser(reader, format).use { parser ->
                val headerMap = parser.headerMap
                validateHeaders(headerMap)

                return StreamSupport.stream(parser.spliterator(), true)
                    .filter { record -> !isRecordEmpty(record) }
                    .map { record -> parseRow(record) }
                    .collect(Collectors.toList())
            }
        }
    }

    private fun isRecordEmpty(record: CSVRecord?): Boolean {
        if (record == null || record.size() == 0) return true
        return record.all { it.isNullOrBlank() }
    }

    private fun validateHeaders(headerMap: Map<String, Int>) {
        val required = config.csvRequiredColumns
        val missing = mutableListOf<String>()
        val normalizedHeaders = headerMap.keys.associateBy { normalize(it) }

        for (field in required) {
            val normalizedField = normalize(field)
            if (!normalizedHeaders.containsKey(normalizedField)) {
                missing.add(field.trim())
            }
        }
        if (missing.isNotEmpty()) {
            throw RuntimeException("Missing required CSV headers: $missing. Found: ${headerMap.keys}")
        }
    }

    private fun normalize(s: String): String {
        return s.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun getHeader(record: CSVRecord, column: String): String? {
        val normalizedTarget = normalize(column)
        val headerMap = record.parser.headerMap
        for (entry in headerMap.entries) {
            if (normalize(entry.key) == normalizedTarget) {
                return record[entry.key]
            }
        }
        return null
    }

    private fun parseRow(csvRecord: CSVRecord): ParsedRow {
        val rowNum = csvRecord.recordNumber
        return try {
            val id = getHeader(csvRecord, config.getIdColumn())
            val site = getHeader(csvRecord, config.getSiteColumn())
            val dateStr = getHeader(csvRecord, config.getDateColumn())
            val orName = getHeader(csvRecord, config.getOrColumn())
            val surgeon = getHeader(csvRecord, config.getSurgeonColumn())
            val procedure = getHeader(csvRecord, config.getProcedureColumn())
            val anesthesiologist = getHeader(csvRecord, config.getAnesthesiologistColumn())
            val startTimeStr = getHeader(csvRecord, config.getStartTimeField())
            val endTimeStr = getHeader(csvRecord, config.getEndTimeField())
            val patientInStr = getHeader(csvRecord, config.getPatientInColumn())
            val patientOutStr = getHeader(csvRecord, config.getPatientOutColumn())

            if (id == null || dateStr == null || site == null || orName == null || startTimeStr == null || endTimeStr == null || patientInStr == null || patientOutStr == null) {
                return ParsedRow(rowNum, null, null, null, null, null, 0, false, "Missing required data in row", false)
            }

            val date = DateTimeParser.parseDate(dateStr, config.getDateFormat())
            val csvStart = DateTimeParser.parseTime(startTimeStr, config.getTimeFormat())
            val csvEnd = DateTimeParser.parseTime(endTimeStr, config.getTimeFormat())
            val patientIn = DateTimeParser.parseTime(patientInStr, config.getTimeFormat())
            val patientOut = DateTimeParser.parseTime(patientOutStr, config.getTimeFormat())

            var unparseableField: String? = null
            if (date == null) unparseableField = "Date"
            else if (csvStart == null) unparseableField = "Start Time"
            else if (csvEnd == null) unparseableField = "End Time"
            else if (patientIn == null) unparseableField = "Patient In Room Time"
            else if (patientOut == null) unparseableField = "Patient Out Room Time"

            val record = if (unparseableField == null) {
                CaseRecord(id, site, date!!, orName, surgeon, procedure, csvStart!!, csvEnd!!, patientIn!!, patientOut!!, anesthesiologist)
            } else null

            validator.validate(rowNum, record, unparseableField)
        } catch (e: Exception) {
            logger.error("Error parsing row {}: {}", rowNum, e.message)
            ParsedRow(rowNum, null, null, null, null, null, 0, false, "Parsing error: ${e.message}", false)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CsvCaseReader::class.java)
    }
}
