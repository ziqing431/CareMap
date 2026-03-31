package com.example.caremap.domain.model

data class NavigationInstruction(
    val stepNumber: Int,
    val rawText: String,
    val landmarkHint: String,
    val distanceMeters: Int = 0,
    val routePoints: List<RoutePoint> = emptyList(),
)
