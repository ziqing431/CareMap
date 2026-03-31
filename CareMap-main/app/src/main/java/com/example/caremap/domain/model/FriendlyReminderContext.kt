package com.example.caremap.domain.model

enum class FriendlyReminderEventType {
    STEP_ENTER,
    STEP_APPROACH,
    CROSSING_ALERT,
    REROUTED,
    ARRIVAL_APPROACH,
}

data class FriendlyReminderContext(
    val eventType: FriendlyReminderEventType,
    val rawInstruction: String,
    val stepNumber: Int,
    val stepDistanceMeters: Int,
    val stepRemainingMeters: Int,
    val routeRemainingMeters: Int,
    val roadOrLandmarkHint: String,
    val destinationName: String,
    val isRerouted: Boolean,
    val currentLocationText: String,
)
