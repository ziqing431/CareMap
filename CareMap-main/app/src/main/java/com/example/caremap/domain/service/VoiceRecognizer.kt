package com.example.caremap.domain.service

interface VoiceRecognizer {
    suspend fun listenOnce(): Result<String>

    fun stopListening()
}
