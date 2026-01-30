package org.heailth.service
import org.heailth.config.ConfigLoader
import org.heailth.model.*
import java.time.temporal.WeekFields
import java.util.Comparator

class DataAggregator {

    fun aggregateWeekly(dayResults: List<DayGroupResult>, capacity: Int): List<WeekSummary> {
        val weeklyGroups = dayResults.groupBy { dr ->
            val date = dr.groupKey.date
            val weekFields = WeekFields.ISO
            val week = date.get(weekFields.weekOfWeekBasedYear())
            val year = date.get(weekFields.weekBasedYear())
            val weekStr = String.format("%04d-W%02d", year, week)
            WeekKey(dr.groupKey.site, weekStr)
        }

        return weeklyGroups.keys
            .sortedWith(compareBy<WeekKey> { it.week }.thenBy { it.site })
            .map { wk ->
                val days = weeklyGroups[wk]!!
                val daysCount = days.size
                val totalCases = days.sumOf { it.validCases.size }
                val sumActualRoomsUsed = days.sumOf { it.actualRoomsUsed }
                val sumOptimizedRoomsUsed = days.sumOf { it.optimizedRoomsUsed ?: 0 }
                val totalRoomsSaved = days.sumOf { it.roomsSavedDay }
                val totalMinutes = days.flatMap { it.validCases }.sumOf { it.durationMinutes }

                val totalCapacityMinutes = days.sumOf { (it.optimizedRoomsUsed ?: it.actualRoomsUsed).toLong() * capacity }
                val totalUnusedMinutes = totalCapacityMinutes - totalMinutes

                WeekSummary(
                    wk, daysCount, totalCases, sumActualRoomsUsed, sumOptimizedRoomsUsed,
                    totalRoomsSaved, totalMinutes, totalCapacityMinutes, totalUnusedMinutes
                )
            }
    }

    fun groupForOptimization(validRows: List<ParsedRow>, config: ConfigLoader): Map<GroupKey, List<ParsedRow>> {
        return validRows.groupBy { r ->
            val site = r.record!!.site
            val clusterName = config.siteToCluster[site]
            if (clusterName != null) {
                GroupKey(clusterName, r.record.date, clusterName)
            } else {
                GroupKey(site, r.record.date)
            }
        }
    }
}
