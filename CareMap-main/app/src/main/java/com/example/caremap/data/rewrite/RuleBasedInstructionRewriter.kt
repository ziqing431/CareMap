package com.example.caremap.data.rewrite

import com.example.caremap.domain.model.FriendlyReminderContext
import com.example.caremap.domain.model.FriendlyReminderEventType
import com.example.caremap.domain.service.InstructionRewriter
import kotlin.math.max

class RuleBasedInstructionRewriter : InstructionRewriter {
    override suspend fun rewrite(context: FriendlyReminderContext): Result<String> {
        return Result.success(buildReminder(context))
    }

    fun buildReminder(context: FriendlyReminderContext): String {
        val landmark = context.roadOrLandmarkHint.ifBlank { "当前道路" }
        val routeRemaining = formatDistance(context.routeRemainingMeters)
        val stepRemaining = formatDistance(max(context.stepRemainingMeters, 0))
        val roadPhrase = if (landmark.contains("米")) {
            landmark
        } else {
            "沿着$landmark"
        }

        return when (context.eventType) {
            FriendlyReminderEventType.STEP_ENTER -> {
                when {
                    isRightTurn(context) ->
                        "接下来要往右边拐，先慢一点走，留意前面的路口。现在先$roadPhrase，离这一步结束还有$stepRemaining。"
                    isLeftTurn(context) ->
                        "接下来要往左边拐，先顺着现在这段路走，快到路口时再慢一点。离这一步结束还有$stepRemaining。"
                    else ->
                        "先继续往前走，别着急，顺着${landmark}走就行。离终点大约还有$routeRemaining。"
                }
            }

            FriendlyReminderEventType.STEP_APPROACH -> {
                when {
                    isRightTurn(context) ->
                        "前面快到路口了，准备往右边转。先看清路口方向，再顺着提示走。"
                    isLeftTurn(context) ->
                        "前面就是要转弯的路口，准备往左边走，注意别走过了。"
                    else ->
                        "前面这一步快走完了，继续顺着${landmark}往前，马上会有下一步提示。"
                }
            }

            FriendlyReminderEventType.CROSSING_ALERT ->
                "前面可能需要过马路，先看红绿灯和左右来车，尽量走人行横道，确认安全再过去。"

            FriendlyReminderEventType.REROUTED ->
                "我已经按你现在的位置重新规划了路线，不用往回找，顺着新的方向继续走就行。离终点大约还有$routeRemaining。"

            FriendlyReminderEventType.ARRIVAL_APPROACH ->
                "已经快到了，先留意${
                    if (landmark.isNotBlank()) landmark else context.destinationName
                }附近的入口和门牌，别走太快。"
        }
    }

    private fun isRightTurn(context: FriendlyReminderContext): Boolean {
        val content = "${context.rawInstruction} ${context.roadOrLandmarkHint}"
        return content.contains("右转") || content.contains("右前方")
    }

    private fun isLeftTurn(context: FriendlyReminderContext): Boolean {
        val content = "${context.rawInstruction} ${context.roadOrLandmarkHint}"
        return content.contains("左转") || content.contains("左前方")
    }

    private fun formatDistance(distanceMeters: Int): String {
        return if (distanceMeters >= 1000) {
            String.format("%.1f 公里", distanceMeters / 1000f)
        } else {
            "${distanceMeters.coerceAtLeast(0)} 米"
        }
    }
}
