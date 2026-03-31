package com.example.caremap.domain.service

import com.example.caremap.domain.model.CurrentLocation
import com.example.caremap.domain.model.DestinationCandidate
import com.example.caremap.domain.model.NavigationInstruction
import com.example.caremap.domain.model.RoutePreview
import kotlinx.coroutines.flow.Flow

interface MapNavigator {
    suspend fun buildRoute(
        destination: DestinationCandidate,
        origin: CurrentLocation?,
    ): Result<RoutePreview>

    suspend fun searchDestinations(
        keyword: String,
        origin: CurrentLocation?,
    ): Result<List<DestinationCandidate>>

    suspend fun searchNearbyPlaces(
        keyword: String,
        origin: CurrentLocation?,
    ): Result<List<DestinationCandidate>>

    fun navigationUpdates(route: RoutePreview): Flow<NavigationInstruction>
}
