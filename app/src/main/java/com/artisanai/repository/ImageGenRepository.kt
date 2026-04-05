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
        .build()

    private val gson = Gson()
    private val apiKey get() = ApiKeyManager.loadApiKey(context)
    private val endpointUrl get() =
        "${ApiKeyManager.loadBaseUrl(context)}/v1beta/models/gemini-3.1-flash-image-preview:generateContent"

    suspend fun generateImage(
        prompt: String,
        aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
        imageSize: ImageSize = ImageSize.SIZE_2K,
        thinkingLevel: ThinkingLevel = ThinkingLevel.MINIMAL,
        useGrounding: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("请先在设置中填写 API Key"))
        try {
            executeRequest(buildBody(prompt, aspectRatio, imageSize, thinkingLevel, useGrounding, null))
        } catch (e: Exception) {
            Log.e("ImageGenRepo", "generateImage failed", e)
            Result.failure(e)
        }
    }

    suspend fun editImage(
        prompt: String,
        imageBase64: String,
        aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
        imageSize: ImageSize = ImageSize.SIZE_2K,
        thinkingLevel: ThinkingLevel = ThinkingLevel.MINIMAL,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("请先在设置中填写 API Key"))
        try {
            executeRequest(buildBody(prompt, aspectRatio, imageSize, thinkingLevel, false, imageBase64))
        } catch (e: Exception) {
            Log.e("ImageGenRepo", "editImage failed", e)
            Result.failure(e)
        }
    }

    /** 多轮图片编辑：传入历史轮次 + 新指令 */
    suspend fun multiTurnEditImage(
        completedTurns: List<EditTurn>,  // 已完成的轮次（含结果图）
        newInstruction: String,
        sourceImageBase64: String?,      // 第一轮需要源图
        aspectRatio: AspectRatio = AspectRatio.SQUARE_1_1,
        imageSize: ImageSize = ImageSize.SIZE_2K,
        thinkingLevel: ThinkingLevel = ThinkingLevel.MINIMAL
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("请先在设置中填写 API Key"))
        try {
            val contents = com.google.gson.JsonArray()

            // 历史轮次
            completedTurns.forEach { turn ->
                // user turn
                contents.add(JsonObject().apply {
                    addProperty("role", "user")
                    add("parts", com.google.gson.JsonArray().apply {
                        turn.inputImageBase64?.let { img ->
                            add(JsonObject().apply {
                                add("inlineData", JsonObject().apply {
                                    addProperty("mimeType", "image/jpeg")
                                    addProperty("data", img)
                                })
                            })
                        }
                        add(JsonObject().apply { addProperty("text", turn.userText) })
                    })
                })
                // model turn
                turn.resultImageBase64?.let { result ->
                    contents.add(JsonObject().apply {
                        addProperty("role", "model")
                        add("parts", com.google.gson.JsonArray().apply {
                            add(JsonObject().apply {
                                add("inlineData", JsonObject().apply {
                                    addProperty("mimeType", "image/jpeg")
                                    addProperty("data", result)
                                })
                            })
                        })
                    })
                }
            }

            // 新的 user turn
            contents.add(JsonObject().apply {
                addProperty("role", "user")
                add("parts", com.google.gson.JsonArray().apply {
                    // 若没有历史且有源图，附在新指令上
                    if (completedTurns.isEmpty()) {
                        sourceImageBase64?.let { img ->
                            add(JsonObject().apply {
                                add("inlineData", JsonObject().apply {
                                    addProperty("mimeType", "image/jpeg")
                                    addProperty("data", img)
                                })
                            })
                        }
                    }
                    add(JsonObject().apply { addProperty("text", newInstruction) })
                })
            })

            val body = JsonObject().apply {
                add("contents", contents)
                add("generationConfig", JsonObject().apply {
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
            }
            executeRequest(gson.toJson(body))
        } catch (e: Exception) {
            Log.e("ImageGenRepo", "multiTurnEditImage failed", e)
            Result.failure(e)
        }
    }

    private fun buildBody(
        prompt: String, aspectRatio: AspectRatio, imageSize: ImageSize,
        thinkingLevel: ThinkingLevel, useGrounding: Boolean, imageBase64: String?
    ): String {
        val body = JsonObject()
        val parts = com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("text", prompt) })
            if (imageBase64 != null) {
                add(JsonObject().apply {
                    add("inlineData", JsonObject().apply {
                        addProperty("mimeType", "image/jpeg")
                        addProperty("data", imageBase64)
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
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = json.getAsJsonArray("candidates")
                ?: return Result.failure(Exception("API 返回空结果"))
            if (candidates.size() == 0) return Result.failure(Exception("可能触发了安全过滤，请修改提示词"))
            val parts = candidates[0].asJsonObject
                .getAsJsonObject("content").getAsJsonArray("parts")
            for (part in parts) {
                val p = part.asJsonObject
                if (p.has("inlineData")) {
                    return Result.success(p.getAsJsonObject("inlineData").get("data").asString)
                }
            }
            Result.failure(Exception("响应中未找到图像数据"))
        } catch (e: Exception) {
            Result.failure(Exception("解析响应失败: ${e.message}"))
        }
    }
}
