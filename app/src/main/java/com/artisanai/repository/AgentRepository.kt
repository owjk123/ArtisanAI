package com.artisanai.repository

import android.content.Context
import android.util.Log
import com.artisanai.util.ApiKeyManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AgentRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val apiKey get() = ApiKeyManager.loadAgentApiKey(context)

    private fun candidateBaseUrls(): List<String> {
        val primary = ApiKeyManager.loadAgentBaseUrl(context).trimEnd('/')
        val presetUrls = ApiKeyManager.PRESETS.map { it.url.trimEnd('/') }
        return if (primary in presetUrls) listOf(primary) + presetUrls.filter { it != primary } else listOf(primary)
    }
    private val model = "gemini-3.1-flash-lite-preview"

    private val customSystemPrompt get() = ApiKeyManager.loadAgentSystemPrompt(context)
        .let { if (it.isBlank()) "" else "\n\nAdditional instructions:\n$it" }

    /** 润色提示词：中文描述 → 专业英文提示词 */
    suspend fun polishPrompt(userInput: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("请先填写 API Key"))
        val system = """You are an expert AI image prompt engineer.
Transform the user's description into a professional English image generation prompt.
Rules:
- Output ONLY the prompt, no explanations or prefixes
- Include: subject, style, lighting, composition, quality boosters (masterpiece, ultra detailed, etc.)
- Keep it concise and powerful, under 200 words
- Output the prompt text only""" + customSystemPrompt
        callChat(system, "Convert to image prompt: $userInput")
    }

    /** 反推提示词：参考图 → 英文提示词（独立，不含用户描述） */
    suspend fun reversePromptFromImage(imageBase64: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("请先填写 API Key"))
        try {
            val system = """You are an expert AI image reverse engineer.
Analyze the image and output a precise English image generation prompt.
Rules:
- Output ONLY the prompt keywords/phrases, comma-separated
- Cover: subject, style, color palette, lighting, composition, technical parameters
- Include quality boosters
- 100-150 words maximum
- No explanations, just the prompt""" + customSystemPrompt

            val body = JsonObject().apply {
                addProperty("model", model)
                addProperty("max_tokens", 500)
                add("messages", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", system)
                    })
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("content", com.google.gson.JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("type", "image_url")
                                add("image_url", JsonObject().apply {
                                    addProperty("url", "data:image/jpeg;base64,$imageBase64")
                                })
                            })
                            add(JsonObject().apply {
                                addProperty("type", "text")
                                addProperty("text", "Analyze this image and output the image generation prompt.")
                            })
                        })
                    })
                })
            }
            parseChat(postJson(gson.toJson(body)))
        } catch (e: Exception) {
            Log.e("AgentRepo", "reversePrompt failed", e)
            Result.failure(e)
        }
    }

    private fun callChat(system: String, user: String): Result<String> {
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 500)
            addProperty("temperature", 0.7)
            add("messages", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { addProperty("role","system"); addProperty("content", system) })
                add(JsonObject().apply { addProperty("role","user");   addProperty("content", user) })
            })
        }
        return parseChat(postJson(gson.toJson(body)))
    }

    private fun postJson(json: String): String? {
        var last: String? = null
        for ((idx, base) in candidateBaseUrls().withIndex()) {
            try {
                val response = client.newCall(
                    Request.Builder()
                        .url("$base/v1/chat/completions")
                        .header("Authorization", "Bearer $apiKey")
                        .header("Content-Type", "application/json")
                        .post(json.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()
                if (response.isSuccessful) {
                    if (idx > 0) Log.i("AgentRepo", "Fallback succeeded on $base")
                    return response.body?.string()
                }
                // 业务错误（401/403/400）不 fallback
                if (response.code in listOf(400, 401, 403)) return null
                last = "HTTP ${response.code} on $base"
            } catch (e: java.io.IOException) {
                Log.w("AgentRepo", "Endpoint $base unreachable, try next. ${e.message}")
                last = e.message
            }
        }
        Log.e("AgentRepo", "All endpoints failed. Last: $last")
        return null
    }

    private fun parseChat(body: String?): Result<String> {
        if (body == null) return Result.failure(Exception("请求失败"))
        return try {
            val text = gson.fromJson(body, JsonObject::class.java)
                .getAsJsonArray("choices")[0]
                .asJsonObject.getAsJsonObject("message")
                .get("content").asString.trim()
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(Exception("解析失败: ${e.message}"))
        }
    }
}
