package com.example.caremap.domain.service

import com.example.caremap.domain.model.CurrentLocation
import kotlinx.coroutines.flow.Flow

interface LocationTracker {
    suspend fun getCurrentLocation(): Result<CurrentLocation>

    fun observeLocationUpdates(intervalMillis: Long = 3_000L): Flow<CurrentLocation>
}
