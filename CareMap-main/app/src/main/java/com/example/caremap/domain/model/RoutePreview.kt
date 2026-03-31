package com.example.caremap.domain.model

data class RoutePreview(
    val destinationName: String,
    val distanceText: String,
    val durationText: String,
    val totalDistanceMeters: Int,
    val areaHint: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val targetLatitude: Double,
    val targetLongitude: Double,
    val polylinePoints: List<RoutePoint>,
    val steps: List<NavigationInstruction>,
)
