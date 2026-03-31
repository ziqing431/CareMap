package com.example.caremap.domain.model

data class AssistantRequest(
    val questionText: String,
    val pageContext: AssistantPageContext,
    val currentLocation: CurrentLocation?,
    val isNavigating: Boolean,
    val destinationName: String,
    val nextRawInstruction: String,
    val currentFriendlyInstruction: String,
    val routeRemainingMeters: Int?,
    val isRerouting: Boolean,
)
