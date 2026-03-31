package com.example.caremap.domain.service

import com.example.caremap.domain.model.AssistantRequest
import com.example.caremap.domain.model.AssistantResponse

interface AssistantService {
    suspend fun answer(request: AssistantRequest): Result<AssistantResponse>
}
