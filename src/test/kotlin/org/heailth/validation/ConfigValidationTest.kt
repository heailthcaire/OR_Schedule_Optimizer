package org.heailth.validation

import org.heailth.config.ConfigLoader
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@DisplayName("Configuration & Environment Audit")
open class ConfigValidationTest : BaseValidationTest() {

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
    @DisplayName("Validate Configuration Paths and Mandatory Keys")
    fun testConfigurationAudit() {
        println("[DEBUG_LOG] --- Configuration Audit ---")
        
        // 1. Config File Existence
        val configFile = File(CONFIG_PATH)
        assertTrue(configFile.exists(), "app.properties must exist at $CONFIG_PATH")
        println("[DEBUG_LOG] Config file found.")

        // 2. Path Validation
        val csvPathStr = config.getCsvPath()
        println("[DEBUG_LOG] CSV Path: $csvPathStr")
        val csvPath = Paths.get(csvPathStr)
        assertTrue(Files.exists(csvPath), "csv.path does not exist: $csvPathStr")
        assertTrue(Files.isRegularFile(csvPath), "csv.path is not a file: $csvPathStr")
        println("[DEBUG_LOG] CSV file validated: $csvPathStr")
        
        val reportPathStr = config.getReportPath()
        println("[DEBUG_LOG] Report Path: $reportPathStr")
        val reportPath = Paths.get(reportPathStr)
        val parentDir = reportPath.parent
        assertNotNull(parentDir, "report.path must have a parent directory")
        assertTrue(Files.exists(parentDir), "report directory does not exist: $parentDir")
        println("[DEBUG_LOG] Report directory validated: $parentDir")

        // 3. Mandatory Keys & Config Consistency
        val capacity = config.getBinCapacity()
        println("[DEBUG_LOG] Bin Capacity: $capacity")
        assertTrue(capacity > 0, "bin.capacity.minutes must be positive")
        
        // 4. Pattern Validation
        val dateFormat = config.getDateFormat()
        val timeFormat = config.getTimeFormat()
        println("[DEBUG_LOG] Date Format: $dateFormat")
        println("[DEBUG_LOG] Time Format: $timeFormat")
        assertDoesNotThrow({ java.time.format.DateTimeFormatter.ofPattern(dateFormat) }, "Invalid date.format")
        assertDoesNotThrow({ java.time.format.DateTimeFormatter.ofPattern(timeFormat) }, "Invalid time.format")
        
        println("[DEBUG_LOG] Configuration Audit Passed.")
    }

    @Test
    @DisplayName("Verify Mandatory Keys and Values")
    fun testMandatoryKeys() {
        println("[DEBUG_LOG] --- Mandatory Keys Verification ---")
        val requiredKeys = arrayOf(
            "csv.path", "report.path", "id.column", "date.column", "site.column", "or.column",
            "procedure.column", "csv.required.columns", "anesthesia.start.time.column",
            "anesthesia.end.time.column", "or.occupancy.start.time.column", "or.occupancy.end.time.column", "bin.capacity.minutes"
        )
        
        val props = java.util.Properties()
        File(CONFIG_PATH).inputStream().use { props.load(it) }

        for (key in requiredKeys) {
            val value = props.getProperty(key)
            assertNotNull(value, "Missing mandatory key: $key")
            assertFalse(value!!.isBlank(), "Mandatory key $key cannot be blank")
            println("[DEBUG_LOG] Key '$key' is present and has value: $value")
        }
    }

    @Test
    @DisplayName("Verify Numerical Values are Integers")
    fun testNumericalValues() {
        println("[DEBUG_LOG] --- Numerical Values Verification ---")
        val integerKeys = arrayOf(
            "bin.capacity.minutes", "optimization.timeout.seconds", 
            "max.procedure.duration.hours", "min.procedure.duration.minutes",
            "cost.per.or.day", "financials.anesthesiologist.rate", "financials.crna.rate"
        )

        val props = java.util.Properties()
        File(CONFIG_PATH).inputStream().use { props.load(it) }

        for (key in integerKeys) {
            val value = props.getProperty(key)
            if (value != null) {
                val intValue = value.trim().toIntOrNull()
                assertNotNull(intValue, "Key '$key' must be a valid integer. Found: '$value'")
                println("[DEBUG_LOG] Key '$key' is a valid integer: $intValue")
            }
        }

        // Check optional padding minutes if present
        val optionalIntKeys = arrayOf("start.time.pad.minutes", "end.time.pad.minutes")
        for (key in optionalIntKeys) {
            val value = props.getProperty(key)
            if (value != null && value.isNotBlank()) {
                val intValue = value.trim().toIntOrNull()
                assertNotNull(intValue, "Optional key '$key' must be a valid integer if provided. Found: '$value'")
                println("[DEBUG_LOG] Optional key '$key' is a valid integer: $intValue")
            }
        }
    }

    @Test
    @DisplayName("Verify Optional Cluster and Padding Keys")
    fun testOptionalKeys() {
        println("[DEBUG_LOG] --- Optional Keys Verification ---")
        val props = java.util.Properties()
        File(CONFIG_PATH).inputStream().use { props.load(it) }

        // Padding is optional
        val startPad = props.getProperty("start.time.pad.minutes")
        val endPad = props.getProperty("end.time.pad.minutes")
        println("[DEBUG_LOG] Optional padding: start=$startPad, end=$endPad")

        // Clusters are optional
        val hasCluster = props.keys().asSequence().any { (it as String).startsWith("cluster.") }
        if (hasCluster) {
            val clusterKeys = props.keys().asSequence()
                .filter { (it as String).startsWith("cluster.") }
                .map { it as String }
                .toList()
            
            for (key in clusterKeys) {
                val value = props.getProperty(key)
                assertFalse(value.isNullOrBlank(), "Cluster definition for $key cannot be blank")
                println("[DEBUG_LOG] Cluster found: $key = $value")
            }
        } else {
            println("[DEBUG_LOG] No clusters defined (optional).")
        }
    }
}
