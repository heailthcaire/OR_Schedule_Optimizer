package org.heailth.model

import java.time.LocalDate

data class CrnaRecord(
    val region: String,
    val name: String,
    val date: LocalDate,
    val durationHours: Double
)
