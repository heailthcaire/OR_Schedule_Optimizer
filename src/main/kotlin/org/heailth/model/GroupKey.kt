package org.heailth.model

import java.time.LocalDate

@JvmRecord
data class GroupKey(val site: String, val date: LocalDate, val clusterName: String? = null)
