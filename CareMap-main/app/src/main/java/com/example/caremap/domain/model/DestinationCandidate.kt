package com.example.caremap.domain.model

data class DestinationCandidate(
    val id: String,
    val name: String,
    val detail: String,
    val latitude: Double,
    val longitude: Double,
    val poiId: String? = null,
)
