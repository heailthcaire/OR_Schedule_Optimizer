package org.heailth.model

data class CFOVerificationSample(
    val siteName: String,
    val date: String,
    val baselineRooms: Int,
    val optimizedRooms: Int,
    val roomsSaved: Int,
    val roomConsolidationDetails: List<RoomProof>
)

data class RoomProof(
    val originalRoomName: String,
    val procedureCount: Int,
    val totalMinutes: Long,
    val wasEliminated: Boolean,
    val movedToRoom: String? = null,
    val note: String? = null
)
