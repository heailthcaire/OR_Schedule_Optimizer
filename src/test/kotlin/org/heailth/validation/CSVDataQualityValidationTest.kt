package org.heailth.validation

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.heailth.config.ConfigLoader
import org.heailth.util.DateTimeParser
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

@DisplayName("Data Quality & Volumetric Thresholds")
open class CSVDataQualityValidationTest : BaseValidationTest() {

    private lateinit var config: ConfigLoader
    
    companion object {
        private const val CONFIG_PATH = "src/main/resources/app.properties"
    }

    @BeforeEach
    fun setup() {
        val configFile = File(CONFIG_PATH)
        if (!configFile.exists()) {
            fail<Any>("Configuration file not found at $CONFIG_PATH")
        }
        config = ConfigLoader(CONFIG_PATH, null)
    }

    @Test
    @DisplayName("Validate Data Formats and Volumetric Minimum")
    @Throws(IOException::class)
    fun testDataQuality() {
        println("[DEBUG_LOG] --- Data Quality ---")
        val csvPathStr = config.getCsvPath()
        val dateColumn = config.getDateColumn()
        val startField = config.getStartTimeField()
        val endField = config.getEndTimeField()
        val pInField = config.getPatientInColumn()
        val pOutField = config.getPatientOutColumn()

        val format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build()

        Files.newBufferedReader(Paths.get(csvPathStr)).use { reader ->
            reader.mark(1)
            if (reader.read() != 0xFEFF) reader.reset()

            CSVParser(reader, format).use { parser ->
                var rowCount = 0
                for (record in parser) {
                    if (record.all { it.isNullOrBlank() }) continue
                    
                    // Skip records with empty required values as they are handled by CsvValidator
                    val isInvalidRequired = listOf(dateColumn, startField, endField, pInField, pOutField).any { 
                        val v = record.get(it)
                        v.isNullOrBlank() || config.invalidRequiredValues.contains(v.trim().lowercase())
                    }
                    if (isInvalidRequired) continue

                    rowCount++
                    
                    // Format Compliance (Sample first 100)
                    if (rowCount <= 100) {
                        val rowInfo = "Row ${record.recordNumber}"
                        val dateVal = record.get(dateColumn)
                        val startVal = record.get(startField)
                        val endVal = record.get(endField)
                        val pInVal = record.get(pInField)
                        val pOutVal = record.get(pOutField)

                        assertNotNull(DateTimeParser.parseDate(dateVal, null), "Unparseable date at $rowInfo: $dateVal")
                        assertNotNull(DateTimeParser.parseTime(startVal, null), "Unparseable start time at $rowInfo: $startVal")
                        assertNotNull(DateTimeParser.parseTime(endVal, null), "Unparseable end time at $rowInfo: $endVal")
                        assertNotNull(DateTimeParser.parseTime(pInVal, null), "Unparseable patient in at $rowInfo: $pInVal")
                        assertNotNull(DateTimeParser.parseTime(pOutVal, null), "Unparseable patient out at $rowInfo: $pOutVal")
                        
                        if (rowCount == 1 || rowCount % 25 == 0) {
                            println("[DEBUG_LOG] Sample validation at Row $rowCount: Date=$dateVal, Start=$startVal, End=$endVal")
                        }
                    }
                }
                // Volumetric Minimum
                println("[DEBUG_LOG] Total rows processed: $rowCount")
                assertTrue(rowCount >= 100, "Dataset too small. Found $rowCount rows, expected >= 100")
            }
        }
        println("[DEBUG_LOG] Data Quality Passed.")
    }
}
