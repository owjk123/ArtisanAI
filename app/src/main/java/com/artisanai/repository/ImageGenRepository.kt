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
import com.artisanai.data.model.AspectRatio
import com.artisanai.data.model.EditTurn
import com.artisanai.data.model.ImageSize
import com.artisanai.data.model.ThinkingLevel

class ImageGenRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(3, 90, TimeUnit.SECONDS)) // 90s idle，减少stale连接
        .build()

    private val gson = Gson()
    private val apiKey get() = ApiKeyManager.loadApiKey(context)
    private val endpointUrl get() =
        "${ApiKeyManager.loadBaseUrl(context)}/v1beta/models/gemini-3.1-flash-image-preview:generateContent"

    suspend fun generateImage(
        prompt: String,
        referenceImages: List<String> = emptyList(),   // 风格参考图（多张）
        aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
        imageSize: ImageSize = ImageSize.SIZE_2K,
        thinkingLevel: ThinkingLevel = ThinkingLevel.MINIMAL,
        useGrounding: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("请先在设置中填写 API Key"))
        try {
            executeRequest(buildBody(prompt, aspectRatio, imageSize, thinkingLevel, useGrounding, referenceImages))
        } catch (e: Exception) {
            Log.e("ImageGenRepo", "generateImage failed", e)
            Result.failure(e)
        }
    }

    /**
     * 图片链式编辑：每次用上一轮结果图（或源图）+ 新指令。
     * 不传递全部历史大图，避免请求体过大导致超时/卡住。
     */
    suspend fun multiTurnEditImage(
        completedTurns: List<EditTurn>,
        newInstruction: String,
        sourceImageBase64: String?,
        aspectRatio: AspectRatio = AspectRatio.SQUARE_1_1,
        imageSize: ImageSize = ImageSize.SIZE_2K,
        thinkingLevel: ThinkingLevel = ThinkingLevel.MINIMAL
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("请先在设置中填写 API Key"))
        try {
            // 用最近一轮结果图作为基础；没有历史则用用户上传的源图
            val baseImage = completedTurns.lastOrNull { it.resultImageBase64 != null }
                ?.resultImageBase64 ?: sourceImageBase64
            executeRequest(buildBody(newInstruction, aspectRatio, imageSize, thinkingLevel, false, listOfNotNull(baseImage)))
        } catch (e: Exception) {
            Log.e("ImageGenRepo", "multiTurnEditImage failed", e)
            Result.failure(e)
        }
    }

    private fun buildBody(
        prompt: String, aspectRatio: AspectRatio, imageSize: ImageSize,
        thinkingLevel: ThinkingLevel, useGrounding: Boolean,
        referenceImages: List<String> = emptyList()
    ): String {
        val body = JsonObject()
        val parts = com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("text", prompt) })
            // 全部参考图（风格参考或编辑基础图）依次附入
            referenceImages.forEach { img ->
                add(JsonObject().apply {
                    add("inlineData", JsonObject().apply {
                        addProperty("mimeType", "image/jpeg")
                        addProperty("data", img)
                    })
                })
            }
        }
        body.add("contents", com.google.gson.JsonArray().apply {
            add(JsonObject().apply { add("parts", parts) })
        })
        body.add("generationConfig", JsonObject().apply {
            add("responseModalities", com.google.gson.JsonArray().apply { add("IMAGE") })
            add("imageConfig", JsonObject().apply {
                addProperty("aspectRatio", aspectRatio.value)
                addProperty("imageSize", imageSize.value)
            })
            if (thinkingLevel != ThinkingLevel.NONE) {
                add("thinkingConfig", JsonObject().apply {
                    addProperty("thinkingLevel", thinkingLevel.value)
                })
            }
        })
        if (useGrounding) {
            body.add("tools", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { add("google_search", JsonObject()) })
            })
        }
        return gson.toJson(body)
    }

    private fun executeRequest(bodyJson: String): Result<String> {
        val response = client.newCall(
            Request.Builder()
                .url(endpointUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        val responseBody = response.body?.string()
        if (!response.isSuccessful || responseBody == null) {
            val errMsg = when (response.code) {
                401 -> "API Key 无效，请在设置中重新填写"
                429 -> "请求过于频繁，请稍后再试"
                500, 503 -> "服务器繁忙，请稍后再试"
                else -> "请求失败 (${response.code})"
            }
            return Result.failure(Exception(errMsg))
        }
        // 校验是否为合法 JSON（防止后台恢复后 stale 连接返回 HTML/网关错误）
        if (!responseBody.trimStart().startsWith("{")) {
            Log.e("ImageGenRepo", "Non-JSON response (first 300 chars): ${responseBody.take(300)}")
            return Result.failure(Exception("服务器返回异常响应，请重试"))
        }
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = json.getAsJsonArray("candidates")
                ?: return Result.failure(Exception("API 返回空结果"))
            if (candidates.size() == 0) return Result.failure(Exception("可能触发了安全过滤，请修改提示词"))
            val content = candidates[0].asJsonObject.getAsJsonObject("content")
                ?: return Result.failure(Exception("API 返回结构异常（无 content）"))
            val parts = content.getAsJsonArray("parts")
                ?: return Result.failure(Exception("API 返回结构异常（无 parts）"))
            for (part in parts) {
                val p = part.asJsonObject
                if (p.has("inlineData")) {
                    return Result.success(p.getAsJsonObject("inlineData").get("data").asString)
                }
            }
            Result.failure(Exception("响应中未找到图像数据"))
        } catch (e: Exception) {
            Log.e("ImageGenRepo", "Parse error, body (first 300): ${responseBody.take(300)}", e)
            Result.failure(Exception("解析响应失败: ${e.message}"))
        }
    }
}
