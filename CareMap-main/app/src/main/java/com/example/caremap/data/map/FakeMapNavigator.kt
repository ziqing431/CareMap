package com.example.caremap.data.map

import com.example.caremap.domain.model.CurrentLocation
import com.example.caremap.domain.model.DestinationCandidate
import com.example.caremap.domain.model.NavigationInstruction
import com.example.caremap.domain.model.RoutePoint
import com.example.caremap.domain.model.RoutePreview
import com.example.caremap.domain.service.MapNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeMapNavigator : MapNavigator {
    override suspend fun buildRoute(
        destination: DestinationCandidate,
        origin: CurrentLocation?,
    ): Result<RoutePreview> {
        val startLatitude = origin?.latitude ?: 31.239691
        val startLongitude = origin?.longitude ?: 121.447004
        val targetLatitude = 31.244266
        val targetLongitude = 121.454323
        val steps = listOf(
            NavigationInstruction(
                stepNumber = 1,
                rawText = "从当前点出发，沿康定路直行 180 米",
                landmarkHint = "右侧会经过便利店",
                distanceMeters = 180,
                routePoints = listOf(
                    RoutePoint(startLatitude, startLongitude),
                    RoutePoint(31.241104, 121.449806),
                ),
            ),
            NavigationInstruction(
                stepNumber = 2,
                rawText = "在前方路口向右转进入江宁路",
                landmarkHint = "看到大药房再右转",
                distanceMeters = 220,
                routePoints = listOf(
                    RoutePoint(31.241104, 121.449806),
                    RoutePoint(31.242501, 121.451992),
                ),
            ),
            NavigationInstruction(
                stepNumber = 3,
                rawText = "继续前行 250 米，目的地在左前方",
                landmarkHint = "医院门口有醒目的红十字标识",
                distanceMeters = 250,
                routePoints = listOf(
                    RoutePoint(31.242501, 121.451992),
                    RoutePoint(destination.latitude, destination.longitude),
                ),
            ),
        )

        return Result.success(
            RoutePreview(
                destinationName = destination.name,
                distanceText = "约 650 米",
                durationText = "步行约 9 分钟",
                totalDistanceMeters = 650,
                areaHint = destination.detail.ifBlank { "静安区医院周边路线演示" },
                startLatitude = startLatitude,
                startLongitude = startLongitude,
                targetLatitude = destination.latitude,
                targetLongitude = destination.longitude,
                polylinePoints = listOf(
                    RoutePoint(startLatitude, startLongitude),
                    RoutePoint(destination.latitude, destination.longitude),
                ),
                steps = steps,
            )
        )
    }

    override suspend fun searchDestinations(
        keyword: String,
        origin: CurrentLocation?,
    ): Result<List<DestinationCandidate>> {
        val city = origin?.cityName ?: "上海"
        return Result.success(
            listOf(
                DestinationCandidate(
                    id = "demo-hospital",
                    name = keyword.ifBlank { "上海市第一人民医院" },
                    detail = "$city 静安区演示候选",
                    latitude = 31.244266,
                    longitude = 121.454323,
                    poiId = null,
                )
            )
        )
    }

    override suspend fun searchNearbyPlaces(
        keyword: String,
        origin: CurrentLocation?,
    ): Result<List<DestinationCandidate>> {
        val city = origin?.cityName ?: "上海"
        return Result.success(
            listOf(
                DestinationCandidate(
                    id = "nearby-1",
                    name = "${keyword}演示点一",
                    detail = "$city 距离约 180 米",
                    latitude = origin?.latitude?.plus(0.0012) ?: 31.240891,
                    longitude = origin?.longitude?.plus(0.0014) ?: 121.448404,
                ),
                DestinationCandidate(
                    id = "nearby-2",
                    name = "${keyword}演示点二",
                    detail = "$city 距离约 360 米",
                    latitude = origin?.latitude?.plus(0.0020) ?: 31.242091,
                    longitude = origin?.longitude?.plus(0.0022) ?: 121.449204,
                ),
                DestinationCandidate(
                    id = "nearby-3",
                    name = "${keyword}演示点三",
                    detail = "$city 距离约 520 米",
                    latitude = origin?.latitude?.plus(0.0031) ?: 31.243191,
                    longitude = origin?.longitude?.plus(0.0032) ?: 121.450204,
                ),
            )
        )
    }

    override fun navigationUpdates(route: RoutePreview): Flow<NavigationInstruction> = flow {
        route.steps.forEachIndexed { index, instruction ->
            if (index > 0) {
                delay(2200)
            }
            emit(instruction)
        }
    }
}
