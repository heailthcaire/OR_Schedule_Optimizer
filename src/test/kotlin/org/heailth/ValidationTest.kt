package org.heailth

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.heailth.config.ConfigLoader
import org.heailth.util.DateTimeParser
import org.heailth.validation.BaseValidationTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ValidationTest : BaseValidationTest() {
    
    @Test
    @Order(1)
    @DisplayName("Verify csv.path directory and file existence")
    fun testCsvPathExists() {
        val csvPathStr = config.getCsvPath()
        val csvPath = Paths.get(csvPathStr)
        
        println("[DEBUG_LOG] Checking CSV path: $csvPathStr")
        
        assertTrue(Files.exists(csvPath), "CSV file does not exist: $csvPathStr")
        assertTrue(Files.isRegularFile(csvPath), "CSV path is not a file: $csvPathStr")
        assertTrue(Files.exists(csvPath.parent), "CSV directory does not exist: ${csvPath.parent}")
        
        println("[DEBUG_LOG] CSV path validation passed.")
    }

    @Test
    @Order(2)
    @DisplayName("Verify report.path directory existence")
    fun testReportPathDirExists() {
        val reportPathStr = config.getReportPath()
        val reportPath = Paths.get(reportPathStr)
        val parentDir = reportPath.parent
        
        println("[DEBUG_LOG] Checking report directory: $parentDir")
        
        assertNotNull(parentDir, "Report path must have a parent directory")
        assertTrue(Files.exists(parentDir), "Report directory does not exist: $parentDir")
        
        println("[DEBUG_LOG] Report directory validation passed.")
    }

    @Test
    @Order(3)
    @DisplayName("Verify csv.required.columns exist in CSV file")
    @Throws(IOException::class)
    fun testRequiredFieldsExist() {
        val csvPathStr = config.getCsvPath()
        val requiredFields = config.csvRequiredColumns
        
        println("[DEBUG_LOG] Checking required columns: $requiredFields")

        val format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build()

        Files.newBufferedReader(Paths.get(csvPathStr)).use { reader ->
            // Handle UTF-8 BOM
            reader.mark(1)
            if (reader.read() != 0xFEFF) {
                reader.reset()
            }

            CSVParser(reader, format).use { parser ->
                val headerMap = parser.headerMap
                assertNotNull(headerMap, "CSV must have a header")
                
                for (field in requiredFields) {
                    val trimmedField = field.trim()
                    assertTrue(headerMap.containsKey(trimmedField), "Missing required field in CSV: $trimmedField")
                }
            }
        }
        println("[DEBUG_LOG] Required fields validation passed.")
    }

    @Test
    @Order(4)
    @DisplayName("Verify at least 100 rows of data")
    @Throws(IOException::class)
    fun testMinimumRowsOfData() {
        val csvPathStr = config.getCsvPath()

        println("[DEBUG_LOG] Checking for minimum 100 rows of data...")

        val format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build()

        Files.newBufferedReader(Paths.get(csvPathStr)).use { reader ->
            // Handle UTF-8 BOM
            reader.mark(1)
            if (reader.read() != 0xFEFF) {
                reader.reset()
            }

            CSVParser(reader, format).use { parser ->
                var count = 0
                for (record in parser) {
                    if (isRecordNotEmpty(record)) {
                        count++
                    }
                    if (count >= 100) break
                }
                assertTrue(count >= 100, "CSV file must have at least 100 rows of data. Found: $count")
            }
        }
        println("[DEBUG_LOG] Minimum rows validation passed.")
    }

    private fun isRecordNotEmpty(record: CSVRecord): Boolean {
        for (value in record) {
            if (!value.isNullOrBlank()) {
                return true
            }
        }
        return false
    }

    @Test
    @Order(5)
    @DisplayName("Verify first 100 rows have valid date and time formats")
    @Throws(IOException::class)
    fun testDateTimeFormatsInFirst100Rows() {
        val csvPathStr = config.getCsvPath()
        val dateFormat = config.getDateFormat()
        val timeFormat = config.getTimeFormat()
        val dateColumn = config.getDateColumn()
        val startTimeField = config.getStartTimeField()
        val endTimeField = config.getEndTimeField()

        println("[DEBUG_LOG] Checking first 100 rows for valid date/time formats...")

        val format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build()

        Files.newBufferedReader(Paths.get(csvPathStr)).use { reader ->
            // Handle UTF-8 BOM
            reader.mark(1)
            if (reader.read() != 0xFEFF) {
                reader.reset()
            }

            CSVParser(reader, format).use { parser ->
                var count = 0
                for (record in parser) {
                    if (!isRecordNotEmpty(record)) continue
                    count++
                    
                    val dateStr = record.get(dateColumn)
                    val startStr = record.get(startTimeField)
                    val endStr = record.get(endTimeField)

                    assertNotNull(DateTimeParser.parseDate(dateStr, dateFormat), 
                        "Invalid date format at row ${record.recordNumber}: $dateStr")
                    assertNotNull(DateTimeParser.parseTime(startStr, timeFormat), 
                        "Invalid start time format at row ${record.recordNumber}: $startStr")
                    assertNotNull(DateTimeParser.parseTime(endStr, timeFormat), 
                        "Invalid end time format at row ${record.recordNumber}: $endStr")

                    if (count >= 100) break
                }
                assertTrue(count > 0, "No data rows found to validate.")
            }
        }
        println("[DEBUG_LOG] Date and time format validation for first 100 rows passed.")
    }

    companion object {
        private lateinit var config: ConfigLoader
        private const val CONFIG_PATH = "src/main/resources/app.properties"

        @JvmStatic
        @BeforeAll
        @Throws(Exception::class)
        fun setup() {
            val configFile = File(CONFIG_PATH)
            if (!configFile.exists()) {
                org.junit.jupiter.api.Assertions.fail<Any>("Configuration file not found at $CONFIG_PATH")
            }
            config = ConfigLoader(CONFIG_PATH, null)
            println("[DEBUG_LOG] Loaded configuration from $CONFIG_PATH")
        }
    }
}
