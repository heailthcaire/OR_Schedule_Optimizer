package org.heailth.reader

import org.heailth.config.ConfigLoader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class CrnaCsvReaderDurationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should only parse durations matching H MM or HH MM format`() {
        val csvFile = File(tempDir.toFile(), "crna_schedule.csv")
        csvFile.writeText(
            """
            Region,Name,Date,Duration
            Region1,CRNA1,2026-01-01,8:00
            Region1,CRNA2,2026-01-01,10:00
            Region1,CRNA3,2026-01-01,12:00 AM
            Region1,CRNA4,2026-01-01,7:00 PM
            Region1,CRNA5,2026-01-01,not-a-time
            Region1,CRNA6,2026-01-01,12:30
            """.trimIndent()
        )

        val config = ConfigLoader(
            configPath = "src/main/resources/app.properties",
            csvPathOverride = csvFile.absolutePath
        )
        
        val reader = CrnaCsvReader(config)
        val records = reader.read(csvFile.absolutePath)

        // Currently, it might parse "12:00 AM" or "7:00 PM" if it just looks for ":"
        // We want it to ONLY have 8:00, 10:00, and 12:30
        
        val recordNames = records.map { it.name }
        
        assertTrue(recordNames.contains("CRNA1"), "Should include 8:00")
        assertTrue(recordNames.contains("CRNA2"), "Should include 10:00")
        assertTrue(recordNames.contains("CRNA6"), "Should include 12:30")
        
        assertFalse(recordNames.contains("CRNA3"), "Should ignore 12:00 AM")
        assertFalse(recordNames.contains("CRNA4"), "Should ignore 7:00 PM")
        assertFalse(recordNames.contains("CRNA5"), "Should ignore not-a-time")
        
        assertEquals(3, records.size, "Should only have 3 valid records")

        val r1 = records.first { it.name == "CRNA1" }
        assertEquals(8.0, r1.durationHours, 0.001)

        val r6 = records.first { it.name == "CRNA6" }
        assertEquals(12.5, r6.durationHours, 0.001)
    }
}
