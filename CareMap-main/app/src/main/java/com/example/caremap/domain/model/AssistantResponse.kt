package com.example.caremap.domain.model

data class AssistantResponse(
    val answerText: String,
    val recommendedDestinations: List<DestinationCandidate> = emptyList(),
)
