package com.example.caremap.data.assistant

import android.util.Log
import com.example.caremap.domain.model.AssistantRequest
import com.example.caremap.domain.model.AssistantResponse
import com.example.caremap.domain.model.DestinationCandidate
import com.example.caremap.domain.service.AssistantService
import com.example.caremap.domain.service.MapNavigator
import kotlin.math.roundToInt

class DefaultAssistantService(
    private val mapNavigator: MapNavigator,
    private val maasChatClient: IflytekMaasChatClient,
) : AssistantService {
    private val tag = "AiAssistant"

    override suspend fun answer(request: AssistantRequest): Result<AssistantResponse> {
        val intent = AssistantIntentClassifier.classify(request.questionText)
        return when (intent.type) {
            AssistantIntentType.NEARBY_SEARCH -> answerNearbySearch(request, intent.nearbyKeyword.orEmpty())
            AssistantIntentType.NAVIGATION_QA -> answerNavigationQa(request)
            AssistantIntentType.UNSUPPORTED -> Result.success(
                AssistantResponse(
                    answerText = "我现在主要能帮你看附近地点和解释当前导航。你可以问我附近有没有超市、药店，或者问我下一步怎么走。",
                )
            )
        }
    }

    private suspend fun answerNearbySearch(
        request: AssistantRequest,
        keyword: String,
    ): Result<AssistantResponse> {
        val location = request.currentLocation
            ?: return Result.success(
                AssistantResponse(
                    answerText = "请先允许定位，我才能帮你查附近的$keyword。",
                )
            )

        val nearbyResult = mapNavigator.searchNearbyPlaces(keyword, location)
        return nearbyResult.fold(
            onSuccess = { candidates ->
                if (candidates.isEmpty()) {
                    Result.success(
                        AssistantResponse(
                            answerText = "我暂时没有在你附近找到合适的$keyword。你可以换个说法，或者再往前走一点后让我重试。",
                        )
                    )
                } else {
                    val fallbackAnswer = buildNearbyFallbackAnswer(keyword, candidates)
                    val prompt = buildNearbyPrompt(
                        request = request,
                        keyword = keyword,
                        candidates = candidates,
                    )
                    maasChatClient.chat(
                        systemPrompt = NEARBY_SYSTEM_PROMPT,
                        userPrompt = prompt,
                        maxTokens = 220,
                    ).fold(
                        onSuccess = { answer ->
                            Result.success(
                                AssistantResponse(
                                    answerText = sanitizeAnswer(answer, fallbackAnswer),
                                    recommendedDestinations = candidates.take(3),
                                )
                            )
                        },
                        onFailure = { error ->
                            Log.w(tag, "Nearby assistant fallback. keyword=$keyword reason=${error.message}")
                            Result.success(
                                AssistantResponse(
                                    answerText = fallbackAnswer,
                                    recommendedDestinations = candidates.take(3),
                                )
                            )
                        },
                    )
                }
            },
            onFailure = { error ->
                Result.success(
                    AssistantResponse(
                        answerText = error.message ?: "附近地点检索失败，请稍后重试。",
                    )
                )
            },
        )
    }

    private suspend fun answerNavigationQa(
        request: AssistantRequest,
    ): Result<AssistantResponse> {
        if (!request.isNavigating) {
            return Result.success(
                AssistantResponse(
                    answerText = "你现在还没有开始导航。先设置目的地并开始导航后，我才能告诉你下一步怎么走或离终点还有多远。",
                )
            )
        }

        val fallbackAnswer = buildNavigationFallbackAnswer(request)
        val prompt = buildNavigationPrompt(request)
        return maasChatClient.chat(
            systemPrompt = NAVIGATION_SYSTEM_PROMPT,
            userPrompt = prompt,
            maxTokens = 180,
        ).fold(
            onSuccess = { answer ->
                Result.success(
                    AssistantResponse(
                        answerText = sanitizeAnswer(answer, fallbackAnswer),
                    )
                )
            },
            onFailure = { error ->
                Log.w(tag, "Navigation assistant fallback. reason=${error.message}")
                Result.success(
                    AssistantResponse(answerText = fallbackAnswer)
                )
            },
        )
    }

    private fun buildNearbyPrompt(
        request: AssistantRequest,
        keyword: String,
        candidates: List<DestinationCandidate>,
    ): String {
        return buildString {
            appendLine("请基于给定的真实地图结果，回答用户关于附近地点的问题。")
            appendLine("要求：")
            appendLine("1. 只根据提供的地点列表回答，不要虚构新地点。")
            appendLine("2. 用简短、口语化、适合老年人的中文。")
            appendLine("3. 优先说离用户近、名字容易识别的地点。")
            appendLine("4. 最多提到3个地点。")
            appendLine("5. 如果地点列表里已经带了距离，就可以自然说明近一点、再远一点，但不要编造精确路线。")
            appendLine("用户问题：${request.questionText}")
            appendLine("当前位置：${request.currentLocation?.addressText.orEmpty()}")
            appendLine("要找的类型：$keyword")
            appendLine("真实地点列表：")
            candidates.take(5).forEachIndexed { index, candidate ->
                appendLine("${index + 1}. ${candidate.name}｜${candidate.detail}")
            }
            appendLine("请直接输出最终回答。")
        }
    }

    private fun buildNavigationPrompt(request: AssistantRequest): String {
        return buildString {
            appendLine("请根据当前真实导航状态，回答用户的问题。")
            appendLine("要求：")
            appendLine("1. 只根据提供的导航上下文回答，不要编造路线或地点。")
            appendLine("2. 语言简短、口语化、适合老年人。")
            appendLine("3. 回答用户最关心的下一步动作或剩余距离。")
            appendLine("用户问题：${request.questionText}")
            appendLine("当前页面：${request.pageContext}")
            appendLine("当前位置：${request.currentLocation?.addressText.orEmpty()}")
            appendLine("目的地：${request.destinationName}")
            appendLine("下一条原始导航：${request.nextRawInstruction}")
            appendLine("当前友好提醒：${request.currentFriendlyInstruction}")
            appendLine("剩余距离：${formatDistance(request.routeRemainingMeters)}")
            appendLine("是否正在重新规划：${if (request.isRerouting) "是" else "否"}")
            appendLine("请直接输出最终回答。")
        }
    }

    private fun buildNearbyFallbackAnswer(
        keyword: String,
        candidates: List<DestinationCandidate>,
    ): String {
        val topCandidates = candidates.take(3)
        val summary = topCandidates.joinToString("；") { candidate ->
            "${candidate.name}（${candidate.detail}）"
        }
        return "我帮你看了下，附近比较合适的${keyword}有：$summary。你可以直接点下面的地点开始导航。"
    }

    private fun buildNavigationFallbackAnswer(request: AssistantRequest): String {
        val remainingText = formatDistance(request.routeRemainingMeters)
        return when {
            request.questionText.contains("多远") || request.questionText.contains("多久") ||
                request.questionText.contains("终点") || request.questionText.contains("目的地") -> {
                if (remainingText != null) {
                    "离终点大约还有$remainingText。你继续按当前提示慢慢走就行。"
                } else {
                    "我暂时还拿不到剩余距离，但会继续按当前路线为你提示。"
                }
            }

            request.currentFriendlyInstruction.isNotBlank() -> request.currentFriendlyInstruction
            request.nextRawInstruction.isNotBlank() -> "当前提示是：${request.nextRawInstruction}。你照着这一步继续走就行。"
            else -> "路线还在更新中，你先停一下看路口，我马上继续给你提示。"
        }
    }

    private fun sanitizeAnswer(answer: String, fallback: String): String {
        val cleaned = answer
            .replace("**", "")
            .replace(Regex("^[-*\\d.\\s]+"), "")
            .trim()
        return cleaned.ifBlank { fallback }
    }

    private fun formatDistance(distanceMeters: Int?): String? {
        if (distanceMeters == null) return null
        return if (distanceMeters >= 1000) {
            "${((distanceMeters / 100.0).roundToInt() / 10.0)} 公里"
        } else {
            "$distanceMeters 米"
        }
    }

    private companion object {
        const val NEARBY_SYSTEM_PROMPT =
            "你是CareMap的AI小助手。你的用户多为老年人。请根据真实地图结果，用简短、温和、实用的中文回答。" +
                "不能虚构地点，不能编造路线，不能说出列表里没有的店。"

        const val NAVIGATION_SYSTEM_PROMPT =
            "你是CareMap的AI小助手。请把当前导航状态解释成简短、口语化、适合老年人的中文。" +
                "不能编造路线信息，优先说清楚下一步该怎么走。"
    }
}
