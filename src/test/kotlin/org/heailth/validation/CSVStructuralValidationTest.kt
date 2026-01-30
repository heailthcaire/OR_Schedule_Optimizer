package org.heailth.validation

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.heailth.config.ConfigLoader
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

@DisplayName("Data Structural Integrity")
open class CSVStructuralValidationTest : BaseValidationTest() {

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
    @DisplayName("Validate CSV Headers and Schema Mapping")
    @Throws(IOException::class)
    fun testStructuralIntegrity() {
        println("[DEBUG_LOG] --- Structural Integrity ---")
        val csvPathStr = config.getCsvPath()
        val requiredColumns = config.csvRequiredColumns
        println("[DEBUG_LOG] Required Columns: ${requiredColumns.joinToString(", ")}")
        
        val format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build()

        Files.newBufferedReader(Paths.get(csvPathStr)).use { reader ->
            reader.mark(1)
            val firstChar = reader.read()
            if (firstChar != 0xFEFF) {
                reader.reset()
            } else {
                println("[DEBUG_LOG] Detected UTF-8 BOM.")
            }

            CSVParser(reader, format).use { parser ->
                val headerMap = parser.headerMap
                assertNotNull(headerMap, "CSV must have a header")
                println("[DEBUG_LOG] CSV Headers Found: ${headerMap.keys.joinToString(", ")}")
                
                // Verify all required fields are present in header
                val requiredFields = config.csvRequiredColumns
                for (field in requiredFields) {
                    val trimmedField = field.trim()
                    assertTrue(headerMap.containsKey(trimmedField), "Missing required field in CSV: $trimmedField")
                    println("[DEBUG_LOG] Verified field: $trimmedField")
                }
                
                // Verify schema mapping
                val idCol = config.getIdColumn().trim()
                val dateCol = config.getDateColumn().trim()
                val siteCol = config.getSiteColumn().trim()
                val orCol = config.getOrColumn().trim()
                val startCol = config.getStartTimeField().trim()
                val endCol = config.getEndTimeField().trim()
                val pInCol = config.getPatientInColumn().trim()
                val pOutCol = config.getPatientOutColumn().trim()

                println("[DEBUG_LOG] Verifying schema mapping...")
                assertNotNull(headerMap[idCol], "id.column map failure: $idCol")
                assertNotNull(headerMap[dateCol], "date.column map failure: $dateCol")
                assertNotNull(headerMap[siteCol], "site.column map failure: $siteCol")
                assertNotNull(headerMap[orCol], "or.column map failure: $orCol")
                assertNotNull(headerMap[startCol], "anesthesia.start.time.column map failure: $startCol")
                assertNotNull(headerMap[endCol], "anesthesia.end.time.column map failure: $endCol")
                assertNotNull(headerMap[pInCol], "or.occupancy.start.time.column map failure: $pInCol")
                assertNotNull(headerMap[pOutCol], "or.occupancy.end.time.column map failure: $pOutCol")
                println("[DEBUG_LOG] All schema mappings verified.")
            }
        }
        println("[DEBUG_LOG] Structural Integrity Passed.")
    }
}
