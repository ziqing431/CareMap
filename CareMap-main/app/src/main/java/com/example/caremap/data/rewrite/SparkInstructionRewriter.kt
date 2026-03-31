package com.example.caremap.data.rewrite

import android.util.Log
import com.example.caremap.data.assistant.IflytekMaasChatClient
import com.example.caremap.domain.model.FriendlyReminderContext
import com.example.caremap.domain.model.FriendlyReminderEventType
import com.example.caremap.domain.service.InstructionRewriter

class SparkInstructionRewriter(
    private val maasChatClient: IflytekMaasChatClient,
    private val fallback: RuleBasedInstructionRewriter = RuleBasedInstructionRewriter(),
) : InstructionRewriter {
    private val tag = "SparkReminder"

    override suspend fun rewrite(context: FriendlyReminderContext): Result<String> {
        val fallbackReminder = fallback.buildReminder(context)
        Log.d(
            tag,
            "Request spark reminder over HTTP. event=${context.eventType} step=${context.stepNumber} routeRemaining=${context.routeRemainingMeters} stepRemaining=${context.stepRemainingMeters}"
        )

        return maasChatClient.chat(
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = buildPrompt(context),
            temperature = 0.3,
            maxTokens = 160,
        ).mapCatching { content ->
            sanitizeContent(content).let { reminder ->
                if (isTooSimilarToRawInstruction(reminder, context.rawInstruction)) {
                    error("模型返回内容与原始导航语句过于接近")
                }
                Log.d(
                    tag,
                    "Spark reminder success. event=${context.eventType} step=${context.stepNumber} reminder=$reminder"
                )
                reminder
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Log.w(
                    tag,
                    "Spark reminder failed, fallback to rule reminder. event=${context.eventType} step=${context.stepNumber} reason=${error.message}"
                )
                Result.success(fallbackReminder)
            },
        )
    }

    private fun buildPrompt(context: FriendlyReminderContext): String {
        return buildString {
            appendLine("请把下面的导航上下文改写成适合老年人的中文提醒。")
            appendLine("要求：")
            appendLine("1. 只输出1到2句中文短句。")
            appendLine("2. 口语化、生活化，像家人在身边提醒。")
            appendLine("3. 要说清楚下一步动作，但不要机械复述方向、距离、转向。")
            appendLine("4. 严禁直接照抄或轻微改写原始导航语句。")
            appendLine("5. 如果原句像“向西南步行191米左转”，应改成类似“先往前走一小段，到前面路口再左转。”")
            appendLine("6. 如果涉及过街，要提醒注意安全、红绿灯或人行横道。")
            appendLine("7. 不要编号，不要Markdown，不要解释模型。")
            appendLine("事件类型：${eventLabel(context.eventType)}")
            appendLine("目的地：${context.destinationName}")
            appendLine("当前定位：${context.currentLocationText}")
            appendLine("原始导航语句：${context.rawInstruction}")
            appendLine("步骤序号：第 ${context.stepNumber} 步")
            appendLine("当前步骤总距离：${context.stepDistanceMeters} 米")
            appendLine("当前步骤剩余距离：${context.stepRemainingMeters} 米")
            appendLine("整条路线剩余距离：${context.routeRemainingMeters} 米")
            appendLine("道路或地标提示：${context.roadOrLandmarkHint}")
            appendLine("是否重规划后提醒：${if (context.isRerouted) "是" else "否"}")
            appendLine("请直接输出最终提醒。")
        }
    }

    private fun sanitizeContent(content: String): String {
        return content
            .replace("**", "")
            .replace(Regex("^[-*\\d.\\s]+"), "")
            .trim()
    }

    private fun isTooSimilarToRawInstruction(
        reminder: String,
        rawInstruction: String,
    ): Boolean {
        val normalizedReminder = normalizeForSimilarity(reminder)
        val normalizedRaw = normalizeForSimilarity(rawInstruction)
        if (normalizedReminder.isBlank() || normalizedRaw.isBlank()) return false
        if (normalizedReminder == normalizedRaw) return true
        if (normalizedReminder.contains(normalizedRaw) || normalizedRaw.contains(normalizedReminder)) return true

        val distance = levenshtein(normalizedReminder, normalizedRaw)
        val similarity = 1.0 - (distance.toDouble() / maxOf(normalizedReminder.length, normalizedRaw.length))
        return similarity >= SIMILARITY_THRESHOLD
    }

    private fun normalizeForSimilarity(text: String): String {
        return text
            .replace(Regex("[，。！？；：、,.!?;:\\s]"), "")
            .replace("米", "")
            .replace("步行", "")
            .replace("向", "")
            .trim()
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)

        left.forEachIndexed { leftIndex, leftChar ->
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                val insertion = current[rightIndex] + 1
                val deletion = previous[rightIndex + 1] + 1
                val substitution = previous[rightIndex] + if (leftChar == rightChar) 0 else 1
                current[rightIndex + 1] = minOf(insertion, deletion, substitution)
            }
            current.copyInto(previous)
        }
        return previous[right.length]
    }

    private fun eventLabel(eventType: FriendlyReminderEventType): String {
        return when (eventType) {
            FriendlyReminderEventType.STEP_ENTER -> "进入新步骤"
            FriendlyReminderEventType.STEP_APPROACH -> "即将到达当前步骤关键点"
            FriendlyReminderEventType.CROSSING_ALERT -> "前方可能需要过街"
            FriendlyReminderEventType.REROUTED -> "刚完成偏航重规划"
            FriendlyReminderEventType.ARRIVAL_APPROACH -> "快到目的地"
        }
    }

    private companion object {
        const val SIMILARITY_THRESHOLD = 0.72
        const val SYSTEM_PROMPT =
            "你是CareMap的导航提醒助手。你的用户多为老年人。请把导航信息改写成简短、口语化、让老人容易理解的中文提醒。" +
                "避免术语，避免多余解释，优先说清楚下一步怎么做；如果涉及过街，要提醒注意安全；不要使用Markdown、编号或表情。" +
                "严禁直接重复原始导航句子的方向、距离、转向结构，必须改成生活化表达。"
    }
}
