package com.example.caremap.feature.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.caremap.domain.model.DestinationCandidate
import com.example.caremap.feature.assistant.AssistantOverlay
import com.example.caremap.feature.navigation.NavigationUiState
import com.example.caremap.ui.theme.CareMapTheme

@Composable
fun HomeScreen(
    uiState: NavigationUiState,
    onDestinationChange: (String) -> Unit,
    onVoiceInputClick: () -> Unit,
    onVoicePermissionDenied: () -> Unit,
    onAssistantClick: () -> Unit,
    onAssistantVoicePermissionDenied: () -> Unit,
    onAssistantLocationPermissionResult: (Boolean) -> Unit,
    onAssistantRecommendationSelected: (DestinationCandidate) -> Unit,
    onDismissAssistantDialog: () -> Unit,
    onEmergencyCallClick: () -> Unit,
    onDismissEmergencyDialog: () -> Unit,
    onEditFamilyPhoneClick: () -> Unit,
    onFamilyPhoneDraftChange: (String) -> Unit,
    onSaveFamilyPhoneClick: () -> Unit,
    onEmergencyNumberSelected: (phoneNumber: String, label: String) -> Unit,
    onPendingDialHandled: () -> Unit,
    onDirectCallPermissionDenied: () -> Unit,
    onEmergencyCallFailed: (String) -> Unit,
    onStartNavigationClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var awaitingCallPermission by remember { mutableStateOf<Pair<String, String>?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onVoiceInputClick()
        } else {
            onVoicePermissionDenied()
        }
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pendingCall = awaitingCallPermission
        awaitingCallPermission = null
        if (granted && pendingCall != null) {
            launchDirectCall(
                context = context,
                phoneNumber = pendingCall.first,
                onSuccess = onPendingDialHandled,
                onFailure = onEmergencyCallFailed,
            )
        } else {
            onDirectCallPermissionDenied()
        }
    }

    if (uiState.isEmergencyDialogVisible) {
        EmergencyCallDialog(
            familyPhoneNumber = uiState.familyPhoneNumber,
            familyPhoneDraft = uiState.familyPhoneDraft,
            isEditingFamilyPhone = uiState.isEditingFamilyPhone,
            onDismiss = onDismissEmergencyDialog,
            onEditFamilyPhoneClick = onEditFamilyPhoneClick,
            onFamilyPhoneDraftChange = onFamilyPhoneDraftChange,
            onSaveFamilyPhoneClick = onSaveFamilyPhoneClick,
            onEmergencyNumberSelected = onEmergencyNumberSelected,
        )
    }

    uiState.pendingDialNumber?.let { phoneNumber ->
        val label = uiState.pendingDialLabel ?: "紧急号码"
        DirectCallConfirmDialog(
            label = label,
            phoneNumber = phoneNumber,
            onDismiss = onPendingDialHandled,
            onConfirm = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE,
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    launchDirectCall(
                        context = context,
                        phoneNumber = phoneNumber,
                        onSuccess = onPendingDialHandled,
                        onFailure = onEmergencyCallFailed,
                    )
                } else {
                    awaitingCallPermission = phoneNumber to label
                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
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
                text = "CareMap 关怀导航",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "只做两件事：帮您说出目的地，帮您看懂导航。",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "目的地",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.destinationText,
                        onValueChange = onDestinationChange,
                        label = { Text("请输入医院、商场或家人地址") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(20.dp),
                        singleLine = false,
                    )
                }
            }

            Button(
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        onVoiceInputClick()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(
                    text = if (uiState.isListening) "正在听您说话..." else "说目的地",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            Button(
                onClick = onEmergencyCallClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(
                    text = "紧急呼叫",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            Button(
                onClick = onStartNavigationClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(
                    text = "开始导航",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "当前状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    uiState.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onDismissError,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("知道了")
                        }
                    }
                }
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
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun EmergencyCallDialog(
    familyPhoneNumber: String,
    familyPhoneDraft: String,
    isEditingFamilyPhone: Boolean,
    onDismiss: () -> Unit,
    onEditFamilyPhoneClick: () -> Unit,
    onFamilyPhoneDraftChange: (String) -> Unit,
    onSaveFamilyPhoneClick: () -> Unit,
    onEmergencyNumberSelected: (phoneNumber: String, label: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "紧急呼叫",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "请选择要拨打的号码。",
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (isEditingFamilyPhone) {
                    OutlinedTextField(
                        value = familyPhoneDraft,
                        onValueChange = onFamilyPhoneDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("家人电话号码") },
                        placeholder = { Text("请输入家人手机号或座机号") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                    )
                    Button(
                        onClick = onSaveFamilyPhoneClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("保存家人电话")
                    }
                } else {
                    Button(
                        onClick = {
                            onEmergencyNumberSelected(familyPhoneNumber, "家人电话")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("家人电话  $familyPhoneNumber")
                    }
                    OutlinedButton(
                        onClick = onEditFamilyPhoneClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("修改家人电话")
                    }
                }

                EmergencyNumberButton(
                    label = "110 报警",
                    phoneNumber = "110",
                    onEmergencyNumberSelected = onEmergencyNumberSelected,
                )
                EmergencyNumberButton(
                    label = "120 急救",
                    phoneNumber = "120",
                    onEmergencyNumberSelected = onEmergencyNumberSelected,
                )
                EmergencyNumberButton(
                    label = "119 消防",
                    phoneNumber = "119",
                    onEmergencyNumberSelected = onEmergencyNumberSelected,
                )
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

@Composable
private fun EmergencyNumberButton(
    label: String,
    phoneNumber: String,
    onEmergencyNumberSelected: (phoneNumber: String, label: String) -> Unit,
) {
    Button(
        onClick = { onEmergencyNumberSelected(phoneNumber, label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun DirectCallConfirmDialog(
    label: String,
    phoneNumber: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "确认直接拨出",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = "将立即拨打 $label（$phoneNumber）。确认后会直接呼出，请确认周围环境安全。",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("立即拨打")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("取消")
            }
        },
    )
}

private fun launchDirectCall(
    context: android.content.Context,
    phoneNumber: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
) {
    val callIntent = Intent(Intent.ACTION_CALL).apply {
        data = Uri.parse("tel:$phoneNumber")
    }

    val packageManager = context.packageManager
    if (callIntent.resolveActivity(packageManager) == null) {
        onFailure("当前设备不支持直接拨打电话")
        return
    }

    try {
        context.startActivity(callIntent)
        onSuccess()
    } catch (_: SecurityException) {
        onFailure("未获得电话权限，无法直接拨出")
    } catch (_: ActivityNotFoundException) {
        onFailure("没有找到可用于拨号的系统应用")
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    CareMapTheme {
        HomeScreen(
            uiState = NavigationUiState(
                destinationText = "上海市第一人民医院",
                familyPhoneNumber = "13800138000",
                statusMessage = "已识别目的地，请点击开始导航",
            ),
            onDestinationChange = {},
            onVoiceInputClick = {},
            onVoicePermissionDenied = {},
            onAssistantClick = {},
            onAssistantVoicePermissionDenied = {},
            onAssistantLocationPermissionResult = {},
            onAssistantRecommendationSelected = {},
            onDismissAssistantDialog = {},
            onEmergencyCallClick = {},
            onDismissEmergencyDialog = {},
            onEditFamilyPhoneClick = {},
            onFamilyPhoneDraftChange = {},
            onSaveFamilyPhoneClick = {},
            onEmergencyNumberSelected = { _, _ -> },
            onPendingDialHandled = {},
            onDirectCallPermissionDenied = {},
            onEmergencyCallFailed = {},
            onStartNavigationClick = {},
            onDismissError = {},
        )
    }
}
