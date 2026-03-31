package com.example.caremap.domain.model

data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val addressText: String,
    val cityName: String?,
)
