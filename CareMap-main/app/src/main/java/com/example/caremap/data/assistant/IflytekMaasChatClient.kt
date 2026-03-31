package com.example.caremap.data.assistant

import com.example.caremap.BuildConfig
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class IflytekMaasChatClient {
    suspend fun chat(
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.3,
        maxTokens: Int = 256,
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.IFLYTEK_MAAS_API_KEY.trim()
        val modelId = BuildConfig.IFLYTEK_MAAS_MODEL_ID.trim()
        val baseUrl = BuildConfig.IFLYTEK_MAAS_BASE_URL.trim().ifBlank { DEFAULT_BASE_URL }

        if (apiKey.isBlank() || modelId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("MaaS 配置缺失"))
        }

        runCatching {
            requestChatCompletion(
                apiKey = apiKey,
                modelId = modelId,
                baseUrl = baseUrl,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                temperature = temperature,
                maxTokens = maxTokens,
            )
        }
    }

    private fun requestChatCompletion(
        apiKey: String,
        modelId: String,
        baseUrl: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int,
    ): String {
        val endpoint = baseUrl.trimEnd('/') + CHAT_COMPLETIONS_PATH
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        return try {
            val body = JSONObject().apply {
                put("model", modelId)
                put("stream", false)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("search_disable", true)
                put(
                    "messages",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "system")
                                put("content", systemPrompt)
                            }
                        )
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", userPrompt)
                            }
                        )
                    }
                )
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseText = readStream(
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }
            )

            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode: ${extractErrorMessage(responseText)}")
            }

            val responseJson = JSONObject(responseText)
            val choice = responseJson.optJSONArray("choices")
                ?.optJSONObject(0)
                ?: throw IllegalStateException("响应中缺少 choices")
            val message = choice.optJSONObject("message")
                ?: throw IllegalStateException("响应中缺少 message")
            val content = message.optString("content").trim()
            if (content.isBlank()) {
                throw IllegalStateException("模型返回内容为空")
            }
            content
        } finally {
            connection.disconnect()
        }
    }

    private fun extractErrorMessage(responseText: String): String {
        return runCatching {
            val json = JSONObject(responseText)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: responseText
        }.getOrDefault(responseText)
    }

    private fun readStream(stream: InputStream): String {
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    line = reader.readLine()
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://maas-api.cn-huabei-1.xf-yun.com/v2"
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        const val TIMEOUT_MS = 15_000
    }
}
