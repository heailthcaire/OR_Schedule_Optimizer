package org.heailth.report

import com.fasterxml.jackson.databind.ObjectMapper
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.loader.ClasspathLoader
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import org.heailth.config.ConfigLoader
import org.heailth.model.*
import org.heailth.service.ReportDataService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class HtmlReportWriter(private val config: ConfigLoader) {
    private val dataService = ReportDataService()
    private val engine: PebbleEngine

    init {
        val loader = ClasspathLoader()
        loader.prefix = "templates/"

        this.engine = PebbleEngine.Builder()
            .loader(loader)
            .extension(JsonExtension())
            .build()
    }

    @Throws(IOException::class)
    fun write(
        weekSummaries: List<WeekSummary>,
        dayResults: List<DayGroupResult>,
        allInvalidRows: List<ParsedRow>,
        totalRoomsSavedGlobal: Int,
        totalRoomDaysGlobal: Int,
        startDate: String?,
        endDate: String?,
        siteCount: Int,
        roomCount: Int,
        siteStats: List<StatisticsResult>,
        globalStats: StatisticsResult,
        totalAnesthesiologistsGlobal: Int,
        globalAnesthesia: AnesthesiaSavings,
        caseValidation: ValidationReport? = null,
        crnaValidation: ValidationReport? = null
    ) {
        val context = dataService.prepareContext(
            config, weekSummaries, dayResults, allInvalidRows,
            totalRoomsSavedGlobal, totalRoomDaysGlobal, startDate, endDate, siteCount, roomCount, siteStats, globalStats,
            totalAnesthesiologistsGlobal, globalAnesthesia, caseValidation, crnaValidation
        )

        val compiledTemplate = engine.getTemplate("report.peb")
        val writer = StringWriter()
        compiledTemplate.evaluate(writer, context)

        Files.writeString(Paths.get(config.getReportPath()), writer.toString(), StandardCharsets.UTF_8)
    }

    private class JsonExtension : AbstractExtension() {
        override fun getFilters(): Map<String, Filter> {
            return mapOf("json" to JsonFilter())
        }
    }

    private class JsonFilter : Filter {
        private val objectMapper = ObjectMapper()

        override fun getArgumentNames(): List<String>? = null

        override fun apply(
            input: Any?,
            args: Map<String, Any>,
            self: PebbleTemplate,
            context: EvaluationContext,
            lineNumber: Int
        ): Any {
            return try {
                objectMapper.writeValueAsString(input)
            } catch (e: Exception) {
                "null"
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HtmlReportWriter::class.java)
    }
}
