package org.heailth.reader

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.heailth.config.ConfigLoader
import org.heailth.model.CrnaRecord
import org.heailth.util.DateTimeParser
import org.heailth.validation.CsvValidator
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class CrnaCsvReader(private val config: ConfigLoader) {

    @Throws(IOException::class)
    fun read(path: String): List<CrnaRecord> {
        return readWithReport(path).first
    }

    @Throws(IOException::class)
    fun readWithReport(path: String): Pair<List<CrnaRecord>, org.heailth.model.ValidationReport> {
        if (!Files.exists(Paths.get(path))) {
            logger.warn("CRNA CSV file not found at: {}", path)
            return Pair(emptyList(), org.heailth.model.ValidationReport().apply { isHeaderRowPresent = false })
        }

        // 1. Validate CRNA CSV
        val validator = CsvValidator(config)
        val validationResult = validator.validate(path, null, config.getCrnaRequiredColumns())
        
        // 2. Map validated rows to CrnaRecord
        val regionCol = config.getCrnaRegionColumn()
        val nameCol = config.getCrnaNameColumn()
        val dateCol = config.getCrnaDateColumn()
        val durationCol = config.getCrnaDurationColumn()

        val records = validationResult.acceptedRows.mapNotNull { row ->
            val region = row[regionCol] ?: ""
            val name = row[nameCol] ?: ""
            val dateStr = row[dateCol] ?: ""
            val durationStr = row[durationCol] ?: ""

            if (region.isBlank() || dateStr.isBlank() || durationStr.isBlank()) {
                return@mapNotNull null
            }

            val date = DateTimeParser.parseFlexibleDate(dateStr)
            
            // Strictly match H:MM or HH:MM format (e.g., 8:00, 10:00)
            // Ignore anything else like "12:00 AM", "7:00 PM"
            val durationRegex = Regex("""^\d{1,2}:\d{2}$""")
            val duration = if (durationRegex.matches(durationStr.trim())) {
                DateTimeParser.parseDurationToDouble(durationStr)
            } else {
                logger.debug("Skipping row for {}: invalid duration format '{}'", name, durationStr)
                null
            }

            if (date == null || duration == null) {
                return@mapNotNull null
            }

            CrnaRecord(region, name, date, duration)
        }
        return Pair(records, validationResult.report)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CrnaCsvReader::class.java)
    }
}
