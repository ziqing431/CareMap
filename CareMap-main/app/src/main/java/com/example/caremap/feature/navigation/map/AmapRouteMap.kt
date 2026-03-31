package com.example.caremap.feature.navigation.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.example.caremap.BuildConfig
import com.example.caremap.domain.model.CurrentLocation
import com.example.caremap.domain.model.RoutePreview

@Composable
fun AmapRouteMap(
    routePreview: RoutePreview,
    currentLocation: CurrentLocation?,
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.HAS_AMAP_API_KEY) {
        MissingKeyNotice(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        TextureMapView(context).apply {
            onCreate(null)
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            val aMap = view.map
            val startPoint = currentLocation?.let {
                LatLng(it.latitude, it.longitude)
            } ?: LatLng(routePreview.startLatitude, routePreview.startLongitude)
            val targetPoint = LatLng(routePreview.targetLatitude, routePreview.targetLongitude)
            val routePolyline = routePreview.polylinePoints
                .map { LatLng(it.latitude, it.longitude) }
                .ifEmpty { listOf(startPoint, targetPoint) }

            aMap.clear()
            aMap.uiSettings.apply {
                isZoomControlsEnabled = false
                isCompassEnabled = true
                isScaleControlsEnabled = true
                isMyLocationButtonEnabled = false
            }

            aMap.addMarker(
                MarkerOptions()
                    .position(startPoint)
                    .title(if (currentLocation == null) "演示起点" else "我的位置")
                    .snippet(currentLocation?.addressText ?: "当前演示位置")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            aMap.addMarker(
                MarkerOptions()
                    .position(targetPoint)
                    .title(routePreview.destinationName)
                    .snippet(routePreview.areaHint)
            )
            aMap.addPolyline(
                PolylineOptions()
                    .addAll(routePolyline)
                    .width(18f)
            )

            val bounds = LatLngBounds.builder()
            routePolyline.forEach { bounds.include(it) }
            bounds.include(startPoint)
            bounds.include(targetPoint)

            view.post {
                aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80))
            }
        }
    )
}

@Composable
private fun MissingKeyNotice(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Text(
                text = "未检测到高德 Key。\n请在 local.properties 中添加 amap.api.key=你的Key",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
