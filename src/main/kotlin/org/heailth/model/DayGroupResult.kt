package org.heailth.model

data class DayGroupResult @JvmOverloads constructor(
    val groupKey: GroupKey,
    val validCases: List<ParsedRow>,
    val invalidCases: List<ParsedRow>,
    val actualRoomsUsed: Int,
    var optimizedRoomsUsed: Int? = null,
    val roomsSavedDay: Int,
    val status: String,
    val anesthesiaSavings: AnesthesiaSavings = AnesthesiaSavings(0, 0, 0, 0),
    val extraCrnaHours: Double = 0.0
)
