package org.heailth.validation

import org.heailth.config.ConfigLoader
import org.heailth.reader.CsvCaseReader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class ValidationTestCSV : BaseValidationTest() {

    private fun createConfig(csvPath: String): ConfigLoader {
        // We use the existing app.properties but override the csv.path
        return ConfigLoader("src/main/resources/app.properties", null, null, null, null, csvPath)
    }

    @Test
    fun `test valid csv`() {
        val csvPath = "src/test/resources/data/valid.csv"
        // Ensure valid.csv is properly formatted for the current code
        val f = File(csvPath)
        val lines = f.readLines()
        if (lines.isNotEmpty() && !lines[0].contains("Provider_Name")) {
            val newHeader = lines[0] + ",Provider_Name"
            val newLines = mutableListOf(newHeader)
            for (i in 1 until lines.size) {
                newLines.add(lines[i] + ",Dr. Anes")
            }
            f.writeText(newLines.joinToString("\n"))
        }

        val config = createConfig(csvPath)
        val validator = CsvValidator(config)
        
        val result = validator.validate(csvPath, null)
        val report = result.report

        val reportWriter = ValidationReportWriter()
        println("Metrics for valid.csv:")
        println(reportWriter.generateTerminalReport(report))
        
        assertEquals(103, report.getTotalRowsRead())
        assertEquals(103, report.getTotalValidRows())
        assertEquals(0, report.getTotalErrorRows())
        assertEquals(103, result.acceptedRows.size, "Should accept all 103 valid rows")
        assertEquals(0, result.report.getRowsSkipped(), "Should have 0 skipped rows")

        // Logical check via Reader
        val reader = CsvCaseReader(config)
        val parsedRows = reader.readFromValidated(result.acceptedRows)
        assertEquals(103, parsedRows.size)
        assertTrue(parsedRows.all { it.isValid })
    }

    @Test
    fun `test invalid csv`() {
        val csvPath = "src/test/resources/data/invalid.csv"
        // Ensure invalid.csv is properly formatted for the current code
        val f = File(csvPath)
        val lines = f.readLines()
        if (lines.isNotEmpty() && !lines[0].contains("Provider_Name")) {
            val newHeader = lines[0] + ",Provider_Name"
            val newLines = mutableListOf(newHeader)
            for (i in 1 until lines.size) {
                newLines.add(lines[i] + ",Dr. Anes")
            }
            f.writeText(newLines.joinToString("\n"))
        }

        val config = createConfig(csvPath)
        val validator = CsvValidator(config)
        
        val result = validator.validate(csvPath, null)
        val report = result.report

        val reportWriter = ValidationReportWriter()
        println("\nMetrics for invalid.csv:")
        println(reportWriter.generateTerminalReport(report))

        // invalid.csv has 107 data rows (excluding header)
        assertEquals(107, report.getTotalRowsRead())
        
        // After enhancing RowValidator, logical errors (zero duration, cross-midnight) 
        // are caught during validation phase. 
        assertEquals(50, report.getTotalValidRows())
        assertEquals(57, report.getTotalErrorRows())
        
        // CsvValidator catches structural/format errors
        // Row 1 (201): Missing Date -> Skipped
        // Row 2 (202): Invalid Start Time -> Skipped
        // Row 7 (empty ID): Missing Required Field -> Skipped
        // Row 8 (205): Invalid Date -> Skipped
        // + 50 new rows with missing dates (total 51 missing dates)
        
        // AND enhanced RowValidator catches logical errors:
        // Row 3 (203): Cross-midnight
        // Row 4 (204): Under min duration (was zero duration)
        // Row 6 (206): Negative duration
        
        assertEquals(50, result.acceptedRows.size, "Should accept only rows with valid formats and required fields")
        assertEquals(57, result.report.getRowsSkipped())

        // Verify the new error message format
        assertTrue(report.getSkipReasons().keys.any { it.startsWith("Rows missing or containing invalid required data:") })

        // Logical errors are now caught by CsvValidator, so parsedRows should be all valid
        val reader = CsvCaseReader(config)
        val parsedRows = reader.readFromValidated(result.acceptedRows)
        
        assertEquals(50, parsedRows.size)
        assertTrue(parsedRows.all { it.isValid }, "All accepted rows should be logically valid")
    }

    @Test
    fun `test normalization csv`() {
        val csvPath = "src/test/resources/data/normalization.csv"
        // Ensure normalization.csv is properly formatted for the current code
        val f = File(csvPath)
        val lines = f.readLines()
        if (lines.isNotEmpty() && !lines[0].contains("Provider_Name")) {
            val newHeader = lines[0] + ",Provider_Name"
            val newLines = mutableListOf(newHeader)
            for (i in 1 until lines.size) {
                newLines.add(lines[i] + ",Dr. Anes")
            }
            f.writeText(newLines.joinToString("\n"))
        }

        val outputPath = "src/test/resources/data/normalization.cleaned.csv"
        
        // Create a config with specific normalization regex if needed
        // For now let's just test the auto-correction of "8:00 AM" to "08:00 AM"
        val config = createConfig(csvPath)
        val validator = CsvValidator(config)
        
        val result = validator.validate(csvPath, outputPath)
        val report = result.report

        val reportWriter = ValidationReportWriter()
        println("\nMetrics for normalization.csv:")
        println(reportWriter.generateTerminalReport(report))

        assertEquals(103, report.getTotalRowsRead())
        assertEquals(103, report.getTotalValidRows())
        assertEquals(0, report.getTotalErrorRows())
        
        assertEquals(103, result.acceptedRows.size)
        
        // Verify that 8:00 AM was corrected to 08:00 AM in the first accepted row
        val firstRow = result.acceptedRows[0]
        assertEquals("08:00 AM", firstRow["Anesthesia_Start_Time"], "Should have auto-corrected 8:00 AM to 08:00 AM")
        
        // Verify trimmed values
        val thirdRow = result.acceptedRows[2]
        assertEquals("11:00 AM", thirdRow["Anesthesia_Start_Time"], "Should have trimmed whitespace")
        
        assertTrue(File(outputPath).exists(), "Cleaned CSV should be generated")
        
        // Cleanup
        Files.deleteIfExists(File(outputPath).toPath())
    }

    @Test
    fun `test duplicate case id`() {
        // Create a temporary CSV with a duplicate Case ID
        val headers = "Case_ID,Date,Site,OR_Room,Surgeon_Name,Procedure_Type,Anesthesia_Start_Time,Anesthesia_End_Time,Provider_Name,Patient_In_Room_Time,Patient_Out_Room_Time"
        val csvContent = """
            $headers
            1001,2025-01-01,Site A,OR 1,Dr. Smith,Knee Surgery,08:00 AM,09:00 AM,Dr. Anes,08:00 AM,09:00 AM
            1001,2025-01-01,Site A,OR 1,Dr. Smith,Knee Surgery,10:00 AM,11:00 AM,Dr. Anes,10:00 AM,11:00 AM
        """.trimIndent()
        
        val tempCsv = File("src/test/resources/data/duplicates.csv")
        tempCsv.writeText(csvContent)
        
        val config = createConfig(tempCsv.path)
        val validator = CsvValidator(config)
        
        val outputPath = "src/test/resources/data/duplicates.cleaned.csv"
        val result = validator.validate(tempCsv.path, outputPath)
        val report = result.report
        
        println("\nMetrics for duplicates.csv:")
        println(ValidationReportWriter().generateTerminalReport(report))
        
        assertEquals(2, report.getTotalRowsRead())
        assertEquals(1, report.getTotalValidRows())
        assertEquals(1, report.getTotalErrorRows())
        assertTrue(report.getSkipReasons().containsKey("Duplicate Case ID: 1001"))
        
        val rejectedFile = File("src/test/resources/data/duplicates.cleaned.rejected.csv")
        assertTrue(rejectedFile.exists(), "Rejected CSV should be generated")
        
        // Cleanup
        tempCsv.delete()
        File(outputPath).delete()
        rejectedFile.delete()
    }

    @Test
    fun `test custom max duration outlier`() {
        val headers = "Case_ID,Date,Site,OR_Room,Surgeon_Name,Procedure_Type,Anesthesia_Start_Time,Anesthesia_End_Time,Provider_Name,Patient_In_Room_Time,Patient_Out_Room_Time"
        val csvContent = """
            $headers
            1003,2025-01-01,Site A,OR 1,Dr. Jones,Long Surgery,08:00 AM,11:00 AM,Dr. Anes,08:00 AM,11:00 AM
        """.trimIndent()
        
        val tempCsv = File("src/test/resources/data/custom_outlier.csv")
        tempCsv.writeText(csvContent)
        
        // Create a custom properties file with 2h limit
        val propsFile = File("src/test/resources/custom.properties")
        val originalProps = File("src/main/resources/app.properties").readLines()
        val filteredProps = originalProps.filter { !it.startsWith("max.procedure.duration.hours") }
        propsFile.writeText(filteredProps.joinToString("\n") + "\nmax.procedure.duration.hours = 2")
        
        val config = ConfigLoader(propsFile.path, null, null, null, null, tempCsv.path)
        val validator = CsvValidator(config)
        
        val result = validator.validate(tempCsv.path, null)
        val report = result.report
        
        println("\nMetrics for custom_outlier.csv (max 2h):")
        println(ValidationReportWriter().generateTerminalReport(report))
        
        // 3 hours duration should be rejected because limit is 2h
        assertEquals(1, report.getTotalErrorRows())
        assertTrue(report.getSkipReasons().containsKey("Procedures over max duration (>2h)"))
        
        tempCsv.delete()
    }

    @Test
    fun `test custom min duration`() {
        val headers = "Case_ID,Date,Site,OR_Room,Surgeon_Name,Procedure_Type,Anesthesia_Start_Time,Anesthesia_End_Time,Provider_Name,Patient_In_Room_Time,Patient_Out_Room_Time"
        val csvContent = """
            ${headers}
            1004,2025-01-01,Site A,OR 1,Dr. Smith,Short Surgery,08:00 AM,08:10 AM,Dr. Anes,08:00 AM,08:10 AM
        """.trimIndent()
        
        val tempCsv = File("src/test/resources/data/custom_min.csv")
        tempCsv.writeText(csvContent)
        
        // Use default config but it has 15m min duration by default now in app.properties (if loaded)
        // Let's create a custom config with 20m limit
        val propsFile = File("src/test/resources/custom_min.properties")
        val originalProps = File("src/main/resources/app.properties").readLines()
        val filteredProps = originalProps.filter { !it.startsWith("min.procedure.duration.minutes") }
        propsFile.writeText(filteredProps.joinToString("\n") + "\nmin.procedure.duration.minutes = 20")
        
        val config = ConfigLoader(propsFile.path, null, null, null, null, tempCsv.path)
        val validator = CsvValidator(config)
        
        val result = validator.validate(tempCsv.path, null)
        val report = result.report
        
        println("\nMetrics for custom_min.csv (min 20m):")
        println(ValidationReportWriter().generateTerminalReport(report))
        
        // 10 minutes duration should be rejected because limit is 20m
        assertEquals(1, report.getTotalErrorRows())
        assertTrue(report.getSkipReasons().containsKey("Procedures under min duration (<20m)"))
        
        tempCsv.delete()
        propsFile.delete()
    }

    @Test
    fun `test missing headers`() {
        val testFilePath = "src/test/resources/data/missing_headers.csv"
        val config = createConfig(testFilePath)
        val validator = CsvValidator(config)
        
        val result = validator.validate(testFilePath, null)
        val report = result.report
        
        println("\nMetrics for missing_headers.csv:")
        val terminalReport = ValidationReportWriter().generateTerminalReport(report)
        println(terminalReport)
        
        assertTrue(report.missingHeaders.isNotEmpty())
        assertTrue(report.missingHeaders.contains("OR_Room"))
        assertEquals(0, result.acceptedRows.size)
    }

    @Test
    fun `test invalid required values`() {
        val headers = "Case_ID,Date,Site,OR_Room,Surgeon_Name,Procedure_Type,Anesthesia_Start_Time,Anesthesia_End_Time,Provider_Name,Patient_In_Room_Time,Patient_Out_Room_Time"
        val csvContent = """
            $headers
            1001,2025-01-01,Site A,OR 1,Dr. Smith,Knee Surgery,08:00 AM,09:00 AM,Dr. Anes,08:00 AM,09:00 AM
            1002,NULL,Site A,OR 1,Dr. Smith,Knee Surgery,09:00 AM,10:00 AM,Dr. Anes,09:00 AM,10:00 AM
            1003,2025-01-01,N/A,OR 1,Dr. Smith,Knee Surgery,10:00 AM,11:00 AM,Dr. Anes,10:00 AM,11:00 AM
            1004,2025-01-01,Site A,empty,Dr. Smith,Knee Surgery,11:00 AM,12:00 PM,Dr. Anes,11:00 AM,12:00 PM
            1005,2025-01-01,Site A,OR 1,Dr. Smith,Knee Surgery,0,01:00 PM,Dr. Anes,0,01:00 PM
        """.trimIndent()

        val tempCsv = File("src/test/resources/data/invalid_values.csv")
        tempCsv.parentFile.mkdirs()
        tempCsv.writeText(csvContent)

        val config = createConfig(tempCsv.path)
        val validator = CsvValidator(config)

        val result = validator.validate(tempCsv.path, null)
        val report = result.report

        println("\nMetrics for invalid_values.csv:")
        println(ValidationReportWriter().generateTerminalReport(report))

        // Row 1: Valid
        // Row 2: Date is "NULL" (invalid)
        // Row 3: Site is "N/A" (invalid)
        // Row 4: OR_Room is "empty" (invalid)
        // Row 5: Start Time is "0" (invalid)

        assertEquals(5, report.getTotalRowsRead())
        assertEquals(1, report.getTotalValidRows())
        assertEquals(4, report.getTotalErrorRows())

        assertTrue(report.getSkipReasons().keys.any { it.contains("Rows missing or containing invalid required data: Date") })
        assertTrue(report.getSkipReasons().keys.any { it.contains("Rows missing or containing invalid required data: Site") })
        assertTrue(report.getSkipReasons().keys.any { it.contains("Rows missing or containing invalid required data: OR_Room") })
        assertTrue(report.getSkipReasons().keys.any { it.contains("Rows missing or containing invalid required data: Anesthesia_Start_Time") })

        tempCsv.delete()
    }

    @Test
    fun `test file with data but no header`() {
        val csvContent = """
            1005,2025-01-01,Site A,OR 1,Dr. Smith,Knee Surgery,08:00 AM,09:00 AM,08:00 AM,09:00 AM
        """.trimIndent()
        val tempCsv = File("src/test/resources/data/no_header_data.csv")
        tempCsv.writeText(csvContent)

        val config = createConfig(tempCsv.path)
        val validator = CsvValidator(config)
        val result = validator.validate(tempCsv.path, null)
        val report = result.report

        println("\nMetrics for no_header_data.csv:")
        val terminalReport = ValidationReportWriter().generateTerminalReport(report)
        println(terminalReport)

        assertFalse(report.isHeaderRowPresent)
        assertTrue(terminalReport.contains("HEADER ROW NOT FOUND"))

        tempCsv.delete()
    }
}
