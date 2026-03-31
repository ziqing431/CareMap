package com.example.caremap.feature.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.caremap.domain.model.DestinationCandidate
import androidx.core.content.ContextCompat
import com.example.caremap.domain.model.NavigationInstruction
import com.example.caremap.domain.model.RoutePoint
import com.example.caremap.domain.model.RoutePreview
import com.example.caremap.feature.assistant.AssistantOverlay
import com.example.caremap.feature.navigation.map.AmapRouteMap
import com.example.caremap.ui.theme.CareMapTheme

@Composable
fun NavigationScreen(
    uiState: NavigationUiState,
    onLocationPermissionResult: (Boolean) -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onDestinationCandidateSelected: (DestinationCandidate) -> Unit,
    onDestinationSelectionDismiss: () -> Unit,
    onDismissRerouteNotice: () -> Unit,
    onAssistantClick: () -> Unit,
    onAssistantVoicePermissionDenied: () -> Unit,
    onAssistantLocationPermissionResult: (Boolean) -> Unit,
    onAssistantRecommendationSelected: (DestinationCandidate) -> Unit,
    onDismissAssistantDialog: () -> Unit,
    onBackToHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val route = uiState.routePreview
    val dismissRerouteNotice by rememberUpdatedState(onDismissRerouteNotice)
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val isGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onLocationPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        onLocationPermissionResult(isGranted)
        if (!isGranted && !hasRequestedPermission) {
            hasRequestedPermission = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    LaunchedEffect(
        uiState.hasLocationPermission,
        uiState.currentLocation,
        uiState.isLocating,
    ) {
        if (uiState.hasLocationPermission && uiState.currentLocation == null && !uiState.isLocating) {
            onRequestCurrentLocation()
        }
    }

    LaunchedEffect(uiState.rerouteNotice) {
        if (uiState.rerouteNotice != null) {
            kotlinx.coroutines.delay(3500)
            dismissRerouteNotice()
        }
    }

    if (uiState.isChoosingDestination) {
        AlertDialog(
            onDismissRequest = onDestinationSelectionDismiss,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDestinationSelectionDismiss) {
                    Text("取消")
                }
            },
            title = {
                Text(
                    text = "请选择目的地",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    uiState.destinationCandidates.forEachIndexed { index, candidate ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDestinationCandidateSelected(candidate) }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = candidate.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (candidate.detail.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = candidate.detail,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index != uiState.destinationCandidates.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "正在前往 ${route?.destinationName.orEmpty()}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = route?.durationText.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            uiState.rerouteNotice?.let { notice ->
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "路线已更新",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        TextButton(
                            onClick = onDismissRerouteNotice,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("知道了")
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                ) {
                    route?.let {
                        AmapRouteMap(
                            routePreview = it,
                            currentLocation = uiState.currentLocation,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "地图暂不可用",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请先完成路线规划",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    if (uiState.isRerouting) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp,
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = "正在更新路线...",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "检测到当前位置已偏离原路线，正在重新规划更合适的步行路线",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "当前导航提示",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.currentRawInstruction,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "更好理解的提醒",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.currentFriendlyInstruction,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "定位状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.locationStatusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestCurrentLocation,
                        enabled = uiState.hasLocationPermission && !uiState.isLocating,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            text = if (uiState.isLocating) "定位中..." else "重新定位",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Button(
                onClick = onBackToHomeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(
                    text = if (uiState.isNavigating) "结束本次演示" else "返回首页",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }

        AssistantOverlay(
            uiState = uiState,
            onAssistantClick = onAssistantClick,
            onAssistantVoicePermissionDenied = onAssistantVoicePermissionDenied,
            onAssistantLocationPermissionResult = onAssistantLocationPermissionResult,
            onAssistantRecommendationSelected = onAssistantRecommendationSelected,
            onDismissAssistantDialog = onDismissAssistantDialog,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NavigationScreenPreview() {
    CareMapTheme {
        NavigationScreen(
            uiState = NavigationUiState(
                currentScreen = CareMapScreen.NAVIGATION,
                routePreview = RoutePreview(
                    destinationName = "上海市第一人民医院",
                    distanceText = "约 650 米",
                    durationText = "步行约 9 分钟",
                    totalDistanceMeters = 650,
                    areaHint = "静安区医院周边路线演示",
                    startLatitude = 31.239691,
                    startLongitude = 121.447004,
                    targetLatitude = 31.244266,
                    targetLongitude = 121.454323,
                    polylinePoints = listOf(
                        RoutePoint(31.239691, 121.447004),
                        RoutePoint(31.241104, 121.449806),
                        RoutePoint(31.242501, 121.451992),
                        RoutePoint(31.244266, 121.454323),
                    ),
                    steps = listOf(
                        NavigationInstruction(
                            stepNumber = 1,
                            rawText = "从当前点出发，沿康定路直行 180 米",
                            landmarkHint = "右侧会经过便利店",
                            distanceMeters = 180,
                            routePoints = listOf(
                                RoutePoint(31.239691, 121.447004),
                                RoutePoint(31.241104, 121.449806),
                            ),
                        )
                    ),
                ),
                currentRawInstruction = "从当前点出发，沿康定路直行 180 米",
                currentFriendlyInstruction = "先一直往前走，不着急，看到熟悉的店面再留意下一步提示。",
                rerouteNotice = "已为你重新规划步行路线",
                statusMessage = "第 1 步：右侧会经过便利店",
                isNavigating = true,
            ),
            onLocationPermissionResult = {},
            onRequestCurrentLocation = {},
            onDestinationCandidateSelected = {},
            onDestinationSelectionDismiss = {},
            onDismissRerouteNotice = {},
            onAssistantClick = {},
            onAssistantVoicePermissionDenied = {},
            onAssistantLocationPermissionResult = {},
            onAssistantRecommendationSelected = {},
            onDismissAssistantDialog = {},
            onBackToHomeClick = {},
        )
    }
}
