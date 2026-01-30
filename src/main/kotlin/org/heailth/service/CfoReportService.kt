package org.heailth.service

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.ClasspathLoader
import org.heailth.config.ConfigLoader
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.*
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists

class CfoReportService(private val config: ConfigLoader) {
    private val logger = LoggerFactory.getLogger(CfoReportService::class.java)
    private val engine: PebbleEngine

    init {
        val loader = ClasspathLoader()
        loader.prefix = "templates/report_package/"
        this.engine = PebbleEngine.Builder()
            .loader(loader)
            .build()
    }

    fun finalizeCfoReport(context: Map<String, Any?>) {
        val projectRoot = config.getBasePath() ?: "."
        val reportDir = Paths.get(projectRoot, "src/main/resources/report")
        val supplementalDir = reportDir.resolve("supplemental")
        
        logger.debug("finalizeCfoReport started. ReportDir: {}", reportDir)
        
        if (!Files.exists(supplementalDir)) {
            Files.createDirectories(supplementalDir)
            logger.debug("Created supplemental directory")
        }

        // 1. Copy report.html to supplemental/05_Full_Audit_Report.html
        val mainReportFile = reportDir.resolve("report.html")
        if (Files.exists(mainReportFile)) {
            Files.copy(mainReportFile, supplementalDir.resolve("05_Full_Audit_Report.html"), StandardCopyOption.REPLACE_EXISTING)
            logger.debug("Copied report.html to 05_Full_Audit_Report.html")
        } else {
            logger.error("Main report file NOT FOUND at: {}", mainReportFile)
            throw FileNotFoundException("Main report file NOT FOUND at: $mainReportFile. Ensure it is generated before calling finalizeCfoReport.")
        }

        // 2. Render templates
        renderTemplate("Executive_ROI_Report.peb", reportDir.resolve("Executive_ROI_Report.html"), context)
        renderTemplate("00_Data_Validation_Audit.peb", supplementalDir.resolve("00_Data_Validation_Audit.html"), context)
        renderTemplate("01_Program_Executive_Summary.peb", supplementalDir.resolve("01_Program_Executive_Summary.html"), context)
        renderTemplate("02_Methodology_CRNA_Savings.peb", supplementalDir.resolve("02_Methodology_CRNA_Savings.html"), context)
        renderTemplate("03_Methodology_Anesthesia_Efficiency.peb", supplementalDir.resolve("03_Methodology_Anesthesia_Efficiency.html"), context)
        renderTemplate("04_Methodology_OR_Consolidation.peb", supplementalDir.resolve("04_Methodology_OR_Consolidation.html"), context)
        renderTemplate("06_CFO_Audit_Verification_Guide.peb", supplementalDir.resolve("06_CFO_Audit_Verification_Guide.html"), context)
        renderTemplate("07_Calculation_Methodology_Summary.peb", supplementalDir.resolve("07_Calculation_Methodology_Summary.html"), context)

        // 3. Zip the folder, excluding the original report.html
        zipReportFolder(reportDir)
    }

    private fun renderTemplate(templateName: String, outputPath: Path, context: Map<String, Any?>) {
        try {
            logger.debug("Rendering template {} to {}", templateName, outputPath)
            val template = engine.getTemplate(templateName)
            val writer = StringWriter()
            template.evaluate(writer, context)
            Files.writeString(outputPath, writer.toString())
            logger.debug("Rendered template {} successfully", templateName)
        } catch (e: Exception) {
            logger.error("ERROR rendering template {}: {}", templateName, e.message)
            throw RuntimeException("Failed to render template $templateName: ${e.message}", e)
        }
    }

    private fun zipReportFolder(reportDir: Path) {
        val zipPathStr = config.getReportPackagePath()
        val zipFile = if (Paths.get(zipPathStr).isAbsolute) {
            Paths.get(zipPathStr)
        } else {
            val projectRoot = config.getBasePath() ?: "."
            Paths.get(projectRoot, zipPathStr)
        }
        
        // Ensure parent directories exist
        val parent = zipFile.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }

        logger.debug("Zipping folder {} to {}", reportDir, zipFile)
        
        // Delete old zip if exists
        Files.deleteIfExists(zipFile)

        try {
            ZipOutputStream(FileOutputStream(zipFile.toFile())).use { zos ->
                Files.walkFileTree(reportDir, object : SimpleFileVisitor<Path>() {
                    @Throws(IOException::class)
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        // Exclude the original report.html, the zip file itself, and hidden files
                        val fileName = file.fileName.toString()
                        if (fileName == "report.html" || file.equals(zipFile) || fileName.startsWith(".")) {
                            return FileVisitResult.CONTINUE
                        }

                        val relativePath = reportDir.relativize(file).toString()
                        zos.putNextEntry(ZipEntry(relativePath))
                        Files.copy(file, zos)
                        zos.closeEntry()
                        return FileVisitResult.CONTINUE
                    }
                })
            }
            logger.debug("Created CFO Report Package at {}", zipFile)
        } catch (e: Exception) {
            logger.error("ERROR zipping folder: {}", e.message)
            throw RuntimeException("Failed to create CFO Report Package ZIP: ${e.message}", e)
        }
    }
}
