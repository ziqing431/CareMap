package com.example.caremap.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.example.caremap.domain.model.CurrentLocation
import com.example.caremap.domain.service.LocationTracker
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class AmapLocationTracker(
    context: Context,
) : LocationTracker {
    private val appContext = context.applicationContext
    private val tag = "AmapLocationTracker"

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Result<CurrentLocation> =
        suspendCancellableCoroutine { continuation ->
            val locationClient = AMapLocationClient(appContext)
            val locationOption = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isNeedAddress = true
                isOnceLocation = true
                isOnceLocationLatest = true
                httpTimeOut = 15_000
            }

            lateinit var listener: AMapLocationListener
            listener = AMapLocationListener { location ->
                if (continuation.isCompleted) return@AMapLocationListener

                val result = location.toCurrentLocation()
                    ?.let { Result.success(it) }
                    ?: Result.failure(
                        IllegalStateException(
                            if (location == null) {
                                "定位结果为空"
                            } else {
                                "定位失败(${location.errorCode})：${location.errorInfo}"
                            }
                        )
                    )

                locationClient.stopLocation()
                locationClient.unRegisterLocationListener(listener)
                locationClient.onDestroy()
                continuation.resume(result)
            }

            locationClient.setLocationOption(locationOption)
            locationClient.setLocationListener(listener)
            locationClient.startLocation()

            continuation.invokeOnCancellation {
                locationClient.stopLocation()
                locationClient.unRegisterLocationListener(listener)
                locationClient.onDestroy()
            }
        }

    @SuppressLint("MissingPermission")
    override fun observeLocationUpdates(intervalMillis: Long): Flow<CurrentLocation> = callbackFlow {
        val locationClient = AMapLocationClient(appContext)
        val locationOption = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isNeedAddress = true
            isOnceLocation = false
            interval = intervalMillis
            httpTimeOut = 15_000
        }

        lateinit var listener: AMapLocationListener
        listener = AMapLocationListener { location ->
            val currentLocation = location.toCurrentLocation()
            if (currentLocation != null) {
                trySend(currentLocation)
            } else if (location != null) {
                Log.w(tag, "Continuous location failed(${location.errorCode}): ${location.errorInfo}")
            } else {
                Log.w(tag, "Continuous location returned null")
            }
        }

        locationClient.setLocationOption(locationOption)
        locationClient.setLocationListener(listener)
        locationClient.startLocation()

        awaitClose {
            locationClient.stopLocation()
            locationClient.unRegisterLocationListener(listener)
            locationClient.onDestroy()
        }
    }

    private fun AMapLocation?.toCurrentLocation(): CurrentLocation? {
        if (this == null || errorCode != 0) return null

        val addressText = listOf(
            poiName,
            aoiName,
            address,
        ).firstOrNull { !it.isNullOrBlank() } ?: "已获取当前位置"

        return CurrentLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            addressText = addressText,
            cityName = city,
        )
    }
}
