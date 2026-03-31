package com.example.caremap.data.voice

import com.example.caremap.domain.service.VoiceRecognizer
import kotlinx.coroutines.delay

class FakeVoiceRecognizer : VoiceRecognizer {
    private var cancelled = false

    override suspend fun listenOnce(): Result<String> {
        cancelled = false
        delay(1200)
        return if (cancelled) {
            Result.failure(IllegalStateException("Voice input cancelled"))
        } else {
            Result.success("上海市第一人民医院")
        }
    }

    override fun stopListening() {
        cancelled = true
    }
}
