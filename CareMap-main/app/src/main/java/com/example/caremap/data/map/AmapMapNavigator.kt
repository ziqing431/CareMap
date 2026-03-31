package com.example.caremap.data.map

import android.content.Context
import android.util.Log
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.poisearch.PoiSearchV2
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.RouteSearchV2
import com.amap.api.services.route.WalkPath
import com.example.caremap.domain.model.CurrentLocation
import com.example.caremap.domain.model.DestinationCandidate
import com.example.caremap.domain.model.NavigationInstruction
import com.example.caremap.domain.model.RoutePoint
import com.example.caremap.domain.model.RoutePreview
import com.example.caremap.domain.service.MapNavigator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class AmapMapNavigator(
    context: Context,
) : MapNavigator {
    private val appContext = context.applicationContext
    private val tag = "AmapMapNavigator"

    override suspend fun buildRoute(
        destination: DestinationCandidate,
        origin: CurrentLocation?,
    ): Result<RoutePreview> = withContext(Dispatchers.IO) {
        if (origin == null) {
            return@withContext Result.failure(IllegalStateException("请先完成定位，再开始导航"))
        }

        try {
            val path = calculateWalkPath(origin, destination)
                ?: return@withContext Result.failure(
                    IllegalStateException("未找到可用的步行路线")
                )
            val instructions = buildInstructions(path)
            val polylinePoints = buildPolyline(path)
            Log.d(
                tag,
                "Route built. destination=${destination.name}, distance=${path.distance}, duration=${path.duration}, steps=${instructions.size}, polyline=${polylinePoints.size}"
            )

            Result.success(
                RoutePreview(
                    destinationName = destination.name,
                    distanceText = formatDistance(path.distance),
                    durationText = formatDuration(path.duration),
                    totalDistanceMeters = path.distance.toInt(),
                    areaHint = destination.detail,
                    startLatitude = origin.latitude,
                    startLongitude = origin.longitude,
                    targetLatitude = destination.latitude,
                    targetLongitude = destination.longitude,
                    polylinePoints = polylinePoints.ifEmpty {
                        listOf(
                            RoutePoint(origin.latitude, origin.longitude),
                            RoutePoint(destination.latitude, destination.longitude),
                        )
                    },
                    steps = instructions.ifEmpty {
                        listOf(
                            NavigationInstruction(
                                stepNumber = 1,
                                rawText = "沿推荐步行路线前往 ${destination.name}",
                                landmarkHint = "${formatDistance(path.distance)} · ${formatDuration(path.duration)}",
                                distanceMeters = path.distance.toInt(),
                                routePoints = polylinePoints,
                            )
                        )
                    },
                )
            )
        } catch (error: AMapException) {
            Log.e(tag, "Route planning failed with AMapException for ${destination.name}", error)
            Result.failure(IllegalStateException("路径规划失败：${error.errorMessage}", error))
        } catch (error: Exception) {
            Log.e(tag, "Route planning failed for ${destination.name}", error)
            Result.failure(error)
        }
    }

    override suspend fun searchDestinations(
        keyword: String,
        origin: CurrentLocation?,
    ): Result<List<DestinationCandidate>> = withContext(Dispatchers.IO) {
        if (origin == null) {
            return@withContext Result.failure(IllegalStateException("请先完成定位，再搜索目的地"))
        }

        try {
            val candidates = mutableListOf<DestinationCandidate>()
            val query = PoiSearchV2.Query(keyword, "", origin.cityName.orEmpty()).apply {
                setPageSize(5)
                setPageNum(0)
                setCityLimit(!origin.cityName.isNullOrBlank())
                setLocation(LatLonPoint(origin.latitude, origin.longitude))
                setDistanceSort(true)
            }
            val poiSearch = PoiSearchV2(appContext, query)
            poiSearch.searchPOI().pois.orEmpty()
                .filter { it.latLonPoint != null }
                .forEachIndexed { index, poi ->
                    candidates += DestinationCandidate(
                        id = poi.poiId?.ifBlank { "poi-$index" } ?: "poi-$index",
                        name = poi.title.ifBlank { keyword },
                        detail = listOf(poi.cityName, poi.adName, poi.snippet)
                            .filter { !it.isNullOrBlank() }
                            .joinToString(" "),
                        latitude = poi.latLonPoint.latitude,
                        longitude = poi.latLonPoint.longitude,
                        poiId = poi.poiId,
                    )
                }

            if (candidates.isNotEmpty()) {
                Log.d(tag, "Found ${candidates.size} POI candidates for $keyword")
                return@withContext Result.success(candidates.distinctBy { it.id })
            }

            val geocodeSearch = GeocodeSearch(appContext)
            val geocodeQuery = GeocodeQuery(keyword, origin.cityName.orEmpty())
            val geocodeResults = geocodeSearch.getFromLocationName(geocodeQuery)
            val geocodeCandidates = geocodeResults.mapIndexedNotNull { index, address ->
                val point = address.latLonPoint ?: return@mapIndexedNotNull null
                DestinationCandidate(
                    id = "geo-$index-${point.latitude}-${point.longitude}",
                    name = keyword,
                    detail = address.formatAddress ?: keyword,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    poiId = null,
                )
            }

            if (geocodeCandidates.isNotEmpty()) {
                Log.d(tag, "Found ${geocodeCandidates.size} geocode candidates for $keyword")
                Result.success(geocodeCandidates)
            } else {
                Result.failure(IllegalStateException("未找到目的地，请换一个更具体的地点名称"))
            }
        } catch (error: AMapException) {
            Log.e(tag, "Destination search failed for $keyword", error)
            Result.failure(IllegalStateException("目的地搜索失败：${error.errorMessage}", error))
        } catch (error: Exception) {
            Log.e(tag, "Destination search failed for $keyword", error)
            Result.failure(error)
        }
    }

    override suspend fun searchNearbyPlaces(
        keyword: String,
        origin: CurrentLocation?,
    ): Result<List<DestinationCandidate>> = withContext(Dispatchers.IO) {
        if (origin == null) {
            return@withContext Result.failure(IllegalStateException("请先完成定位，再搜索附近地点"))
        }

        try {
            val query = PoiSearchV2.Query(keyword, "", origin.cityName.orEmpty()).apply {
                setPageSize(5)
                setPageNum(0)
                setCityLimit(false)
                setLocation(LatLonPoint(origin.latitude, origin.longitude))
                setDistanceSort(true)
            }

            val poiSearch = PoiSearchV2(appContext, query)
            val originPoint = LatLonPoint(origin.latitude, origin.longitude)
            val candidates = poiSearch.searchPOI().pois.orEmpty()
                .filter { poi -> poi.latLonPoint != null }
                .mapIndexedNotNull { index, poi ->
                    val point = poi.latLonPoint ?: return@mapIndexedNotNull null
                    val distanceMeters = distanceMeters(originPoint, point).toInt()
                    if (distanceMeters > 1000) return@mapIndexedNotNull null

                    DestinationCandidate(
                        id = poi.poiId?.ifBlank { "nearby-$index" } ?: "nearby-$index",
                        name = poi.title.ifBlank { keyword },
                        detail = listOf(
                            "距离约 ${distanceMeters.coerceAtLeast(1)} 米",
                            poi.adName,
                            poi.snippet,
                        ).filter { !it.isNullOrBlank() }.joinToString(" · "),
                        latitude = point.latitude,
                        longitude = point.longitude,
                        poiId = poi.poiId,
                    )
                }
                .sortedBy { candidate ->
                    distanceMeters(
                        origin.latitude,
                        origin.longitude,
                        candidate.latitude,
                        candidate.longitude,
                    )
                }
                .take(5)

            if (candidates.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            Log.d(tag, "Found ${candidates.size} nearby POI candidates for $keyword")
            Result.success(candidates)
        } catch (error: AMapException) {
            Log.e(tag, "Nearby search failed for $keyword", error)
            Result.failure(IllegalStateException("附近地点检索失败：${error.errorMessage}", error))
        } catch (error: Exception) {
            Log.e(tag, "Nearby search failed for $keyword", error)
            Result.failure(error)
        }
    }

    override fun navigationUpdates(route: RoutePreview): Flow<NavigationInstruction> = flow {
        route.steps.forEach { emit(it) }
    }

    private fun buildInstructions(path: WalkPath): List<NavigationInstruction> {
        val steps = path.steps ?: emptyList()
        return steps.mapIndexed { index, step ->
            val roadName = step.road?.takeIf { it.isNotBlank() } ?: "当前道路"
            val instructionText = step.instruction?.takeIf { it.isNotBlank() }
                ?: "沿$roadName 步行 ${step.distance.toInt()} 米"
            NavigationInstruction(
                stepNumber = index + 1,
                rawText = instructionText,
                landmarkHint = buildStepHint(step),
                distanceMeters = step.distance.toInt(),
                routePoints = (step.polyline ?: emptyList()).map { point ->
                    RoutePoint(
                        latitude = point.latitude,
                        longitude = point.longitude,
                    )
                },
            )
        }
    }

    private fun buildPolyline(path: WalkPath): List<RoutePoint> {
        val points = mutableListOf<RoutePoint>()
        val fullPathPolyline = path.polyline ?: emptyList()
        if (fullPathPolyline.isNotEmpty()) {
            fullPathPolyline.forEach { point ->
                val routePoint = RoutePoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                )
                val last = points.lastOrNull()
                if (last == null || last.latitude != routePoint.latitude || last.longitude != routePoint.longitude) {
                    points += routePoint
                }
            }
            Log.d(tag, "Using path.polyline with ${points.size} route points")
            return points
        }

        (path.steps ?: emptyList()).forEach { step ->
            (step.polyline ?: emptyList()).forEach { point ->
                val routePoint = RoutePoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                )
                val last = points.lastOrNull()
                if (last == null || last.latitude != routePoint.latitude || last.longitude != routePoint.longitude) {
                    points += routePoint
                }
            }
        }
        if (points.isNotEmpty()) {
            Log.d(tag, "Using step.polyline with ${points.size} route points")
        } else {
            Log.w(tag, "No route polyline returned by AMap")
        }
        return points
    }

    private fun calculateWalkPath(
        origin: CurrentLocation,
        destination: DestinationCandidate,
    ): WalkPath? {
        val fromPoint = LatLonPoint(origin.latitude, origin.longitude)
        val toPoint = LatLonPoint(destination.latitude, destination.longitude)

        try {
            val fromAndTo = RouteSearchV2.FromAndTo(fromPoint, toPoint).apply {
                if (!destination.poiId.isNullOrBlank()) {
                    setDestinationPoiID(destination.poiId)
                }
            }
            val walkQuery = RouteSearchV2.WalkRouteQuery(fromAndTo).apply {
                setIndoor(false)
                setShowFields(RouteSearchV2.ShowFields.NAVI or RouteSearchV2.ShowFields.POLINE)
            }
            val result = RouteSearchV2(appContext).calculateWalkRoute(walkQuery)
            val path = result.paths?.firstOrNull()
            if (path != null) {
                val hasPolyline = buildPolyline(path).isNotEmpty()
                Log.d(tag, "RouteSearchV2 succeeded for ${destination.name}, hasPolyline=$hasPolyline")
                if (hasPolyline) {
                    return path
                }
                Log.w(tag, "RouteSearchV2 returned path without polyline for ${destination.name}, fallback to legacy RouteSearch")
            }
            Log.w(tag, "RouteSearchV2 returned no path for ${destination.name}")
        } catch (error: AMapException) {
            Log.w(tag, "RouteSearchV2 failed for ${destination.name}: ${error.errorMessage}")
        }

        val fromAndTo = RouteSearch.FromAndTo(fromPoint, toPoint).apply {
            if (!destination.poiId.isNullOrBlank()) {
                setDestinationPoiID(destination.poiId)
            }
        }
        val legacyResult = RouteSearch(appContext).calculateWalkRoute(
            RouteSearch.WalkRouteQuery(fromAndTo).apply {
                setExtensions("all")
            }
        )
        val legacyPath = legacyResult.paths?.firstOrNull()
        if (legacyPath != null) {
            Log.d(tag, "Legacy RouteSearch succeeded for ${destination.name}")
        } else {
            Log.w(tag, "Legacy RouteSearch returned no path for ${destination.name}")
        }
        return legacyPath
    }

    private fun buildStepHint(step: com.amap.api.services.route.WalkStep): String {
        return listOf(
            step.road?.takeIf { it.isNotBlank() },
            "${step.distance.toInt()} 米",
        ).joinToString(" · ")
    }

    private fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters >= 1000) {
            String.format(Locale.US, "约 %.1f 公里", distanceMeters / 1000f)
        } else {
            "约 ${distanceMeters.toInt()} 米"
        }
    }

    private fun formatDuration(durationSeconds: Long): String {
        val minutes = (durationSeconds / 60L).coerceAtLeast(1L)
        return "步行约 ${minutes} 分钟"
    }

    private fun distanceMeters(
        fromPoint: LatLonPoint,
        toPoint: LatLonPoint,
    ): Double {
        return distanceMeters(
            fromPoint.latitude,
            fromPoint.longitude,
            toPoint.latitude,
            toPoint.longitude,
        )
    }

    private fun distanceMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double,
    ): Double {
        val earthRadius = 6_371_000.0
        val lat1 = latitude1 * PI / 180.0
        val lat2 = latitude2 * PI / 180.0
        val deltaLat = (latitude2 - latitude1) * PI / 180.0
        val deltaLon = (longitude2 - longitude1) * PI / 180.0

        val haversine = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        val angularDistance = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
        return earthRadius * angularDistance
    }
}
