package com.example.caremap.feature.navigation

import com.example.caremap.domain.model.CurrentLocation
import com.example.caremap.domain.model.DestinationCandidate
import com.example.caremap.domain.model.RoutePreview

enum class CareMapScreen {
    HOME,
    NAVIGATION,
}

data class NavigationUiState(
    val currentScreen: CareMapScreen = CareMapScreen.HOME,
    val destinationText: String = "",
    val familyPhoneNumber: String = "",
    val familyPhoneDraft: String = "",
    val isEmergencyDialogVisible: Boolean = false,
    val isEditingFamilyPhone: Boolean = false,
    val pendingDialNumber: String? = null,
    val pendingDialLabel: String? = null,
    val isListening: Boolean = false,
    val isAssistantListening: Boolean = false,
    val isAssistantLoading: Boolean = false,
    val isAssistantDialogVisible: Boolean = false,
    val assistantQuestionText: String = "",
    val assistantAnswerText: String = "",
    val assistantRecommendations: List<DestinationCandidate> = emptyList(),
    val pendingAssistantQuery: String? = null,
    val assistantErrorMessage: String? = null,
    val shouldRequestAssistantLocationPermission: Boolean = false,
    val isNavigating: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isLocating: Boolean = false,
    val currentLocation: CurrentLocation? = null,
    val locationStatusMessage: String = "尚未获取当前位置",
    val destinationCandidates: List<DestinationCandidate> = emptyList(),
    val isChoosingDestination: Boolean = false,
    val routePreview: RoutePreview? = null,
    val isRerouting: Boolean = false,
    val rerouteNotice: String? = null,
    val currentRawInstruction: String = "",
    val currentFriendlyInstruction: String = "",
    val statusMessage: String = "请输入或语音说出目的地",
    val errorMessage: String? = null,
)
