package com.example.caremap.data.assistant

data class AssistantIntent(
    val type: AssistantIntentType,
    val nearbyKeyword: String? = null,
)

enum class AssistantIntentType {
    NEARBY_SEARCH,
    NAVIGATION_QA,
    UNSUPPORTED,
}

object AssistantIntentClassifier {
    private val nearbyKeywordMappings = listOf(
        "超市" to listOf("超市", "商超"),
        "便利店" to listOf("便利店", "小卖部", "便利超市"),
        "药店" to listOf("药店", "药房", "大药房"),
        "厕所" to listOf("厕所", "卫生间", "洗手间", "公厕"),
        "医院" to listOf("医院", "诊所", "卫生服务中心"),
        "地铁站" to listOf("地铁", "地铁站"),
        "公交站" to listOf("公交站", "公交车站", "公交"),
        "银行" to listOf("银行", "atm", "取款机"),
        "菜市场" to listOf("菜市场", "市场"),
        "餐馆" to listOf("饭店", "餐馆", "吃饭", "餐厅"),
    )

    private val nearbyTriggers = listOf(
        "附近",
        "周边",
        "旁边",
        "离我最近",
        "帮我找",
        "帮我查",
        "哪里有",
    )

    private val navigationQaKeywords = listOf(
        "导航",
        "路线",
        "终点",
        "目的地",
        "下一步",
        "怎么走",
        "往哪走",
        "左转",
        "右转",
        "还有多远",
        "还要多久",
        "到哪了",
        "到没到",
        "前面怎么走",
    )

    fun classify(question: String): AssistantIntent {
        val normalizedQuestion = normalize(question)
        if (normalizedQuestion.isBlank()) {
            return AssistantIntent(AssistantIntentType.UNSUPPORTED)
        }

        val nearbyKeyword = nearbyKeywordMappings.firstOrNull { (_, aliases) ->
            aliases.any { alias -> normalizedQuestion.contains(alias) }
        }?.first

        val wantsNearby = nearbyTriggers.any { normalizedQuestion.contains(it) } || nearbyKeyword != null
        if (wantsNearby && nearbyKeyword != null) {
            return AssistantIntent(
                type = AssistantIntentType.NEARBY_SEARCH,
                nearbyKeyword = nearbyKeyword,
            )
        }

        if (navigationQaKeywords.any { normalizedQuestion.contains(it) }) {
            return AssistantIntent(AssistantIntentType.NAVIGATION_QA)
        }

        return AssistantIntent(AssistantIntentType.UNSUPPORTED)
    }

    private fun normalize(question: String): String {
        return question.trim().lowercase()
    }
}
