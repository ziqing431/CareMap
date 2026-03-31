package com.example.caremap.domain.service

import com.example.caremap.domain.model.FriendlyReminderContext

interface InstructionRewriter {
    suspend fun rewrite(context: FriendlyReminderContext): Result<String>
}
