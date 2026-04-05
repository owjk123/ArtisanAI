package com.artisanai.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ApiKeyManager {

    private const val PREFS_NAME = "artisan_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    // Agent（AI优化/反推）独立API
    private const val KEY_AGENT_API_KEY = "agent_api_key"
    private const val KEY_AGENT_BASE_URL = "agent_base_url"
    private const val DEFAULT_BASE_URL = "https://api.apiyi.com"

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun init(context: Context) {
        _apiKey.value = loadApiKey(context)
    }

    // ── 生图 API ────────────────────────────────────────────
    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, key.trim()).apply()
        _apiKey.value = key.trim()
    }

    fun loadApiKey(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""

    fun saveBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BASE_URL, url.trimEnd('/')).apply()
    }

    fun loadBaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun hasKey(context: Context) = loadApiKey(context).isNotBlank()

    // ── Agent API（AI优化/反推提示词）─────────────────────
    fun saveAgentApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AGENT_API_KEY, key.trim()).apply()
    }

    /** 若 Agent Key 未设置，回退到生图 Key */
    fun loadAgentApiKey(context: Context): String {
        val agentKey = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AGENT_API_KEY, "") ?: ""
        return agentKey.ifBlank { loadApiKey(context) }
    }

    fun saveAgentBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AGENT_BASE_URL, url.trimEnd('/')).apply()
    }

    /** 若 Agent URL 未设置，回退到生图 URL */
    fun loadAgentBaseUrl(context: Context): String {
        val agentUrl = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AGENT_BASE_URL, "") ?: ""
        return agentUrl.ifBlank { loadBaseUrl(context) }
    }

    fun hasAgentKey(context: Context) = loadAgentApiKey(context).isNotBlank()

    // ── Agent 自定义系统提示词（AI优化 + 反推 共用）────────
    private const val KEY_AGENT_SYSTEM_PROMPT = "agent_system_prompt"

    fun saveAgentSystemPrompt(context: Context, prompt: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AGENT_SYSTEM_PROMPT, prompt).apply()
    }

    fun loadAgentSystemPrompt(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AGENT_SYSTEM_PROMPT, "") ?: ""
}
