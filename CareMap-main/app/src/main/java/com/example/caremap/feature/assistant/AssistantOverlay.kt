package com.example.caremap.feature.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.caremap.domain.model.DestinationCandidate
import com.example.caremap.feature.navigation.NavigationUiState

@Composable
fun AssistantOverlay(
    uiState: NavigationUiState,
    onAssistantClick: () -> Unit,
    onAssistantVoicePermissionDenied: () -> Unit,
    onAssistantLocationPermissionResult: (Boolean) -> Unit,
    onAssistantRecommendationSelected: (DestinationCandidate) -> Unit,
    onDismissAssistantDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onAssistantClick()
        } else {
            onAssistantVoicePermissionDenied()
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val isGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onAssistantLocationPermissionResult(isGranted)
    }

    LaunchedEffect(uiState.shouldRequestAssistantLocationPermission) {
        if (uiState.shouldRequestAssistantLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    if (uiState.isAssistantDialogVisible) {
        AssistantDialog(
            question = uiState.assistantQuestionText,
            answer = uiState.assistantAnswerText,
            isLoading = uiState.isAssistantLoading,
            errorMessage = uiState.assistantErrorMessage,
            recommendations = uiState.assistantRecommendations,
            onDismiss = onDismissAssistantDialog,
            onRecommendationSelected = onAssistantRecommendationSelected,
        )
    }

    ExtendedFloatingActionButton(
        onClick = {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                onAssistantClick()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        content = {
            Text(
                text = if (uiState.isAssistantListening) "正在听..." else "AI 助手",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun AssistantDialog(
    question: String,
    answer: String,
    isLoading: Boolean,
    errorMessage: String?,
    recommendations: List<DestinationCandidate>,
    onDismiss: () -> Unit,
    onRecommendationSelected: (DestinationCandidate) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "AI 小助手",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (question.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "你刚才说的是",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = question,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                if (isLoading) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text(
                            text = answer.ifBlank { "正在整理回答..." },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    Text(
                        text = answer,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (!isLoading && recommendations.isNotEmpty()) {
                    Text(
                        text = "你可以直接去这里",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    recommendations.take(3).forEach { candidate ->
                        Button(
                            onClick = { onRecommendationSelected(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = candidate.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (candidate.detail.isNotBlank()) {
                                    Text(
                                        text = candidate.detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Text(
                                    text = "设为目的地并开始导航",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("关闭")
            }
        },
    )
}
