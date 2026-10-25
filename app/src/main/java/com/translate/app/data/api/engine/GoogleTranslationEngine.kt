package com.translate.app.data.api.engine

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.translate.app.data.api.ITranslationEngine
import com.translate.app.data.model.Language
import com.translate.app.data.model.TranslationEngineType
import com.translate.app.data.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Google 翻译引擎（免 Key 内置双模支持）
 *
 * 模式 1: "proxy" - 服务器中转代理模式（默认）
 * 客户端向部署在美国等境外机房的专属中转服务 (默认 129.151.9.55:8787) 请求，
 * 由服务端直连 Google 官方接口获取译文后返回，彻底解决国内网络无法直连 Google 的痛点。
 *
 * 模式 2: "direct" - 官方接口直连模式
 * 客户端直接请求 Google 翻译公开免 Key 接口 (translate.googleapis.com)，
 * 适合身处海外或开启了全局梯子/代理的用户。
 */
class GoogleTranslationEngine(
    private val getMode: () -> String = { "proxy" },
    private val getProxyUrl: () -> String = { "http://129.151.9.55:8787" },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : ITranslationEngine {

    override val engineType: TranslationEngineType = TranslationEngineType.GOOGLE

    companion object {
        private const val GOOGLE_DIRECT_API = "https://translate.googleapis.com/translate_a/single"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        /**
         * 语种代码映射为 Google 翻译标准代码
         */
        fun toGoogleLanguageCode(language: Language): String {
            return when (language.code) {
                "auto" -> "auto"
                "zh" -> "zh-CN"
                "cht" -> "zh-TW"
                "en" -> "en"
                "ja" -> "ja"
                "ko" -> "ko"
                "fr" -> "fr"
                "es" -> "es"
                "ru" -> "ru"
                "de" -> "de"
                "it" -> "it"
                "pt" -> "pt"
                else -> language.code
            }
        }
    }

    override suspend fun translate(
        text: String,
        from: Language,
        to: Language
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLanguage = from,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = true
            )
        }

        val mode = getMode().trim().lowercase()
        val fromCode = toGoogleLanguageCode(from)
        val toCode = if (to == Language.AUTO) "zh-CN" else toGoogleLanguageCode(to)

        if (mode == "direct") {
            translateDirect(text, fromCode, toCode, from, to)
        } else {
            translateViaProxy(text, fromCode, toCode, from, to)
        }
    }

    /**
     * 模式 1: 通过美国云服务器中转代理执行翻译
     */
    private fun translateViaProxy(
        text: String,
        fromCode: String,
        toCode: String,
        from: Language,
        to: Language
    ): TranslationResult {
        val proxyBase = getProxyUrl().trim().trimEnd('/')
        if (proxyBase.isBlank()) {
            return TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLanguage = from,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = false,
                errorMessage = "未配置中转代理服务器地址，请在【设置】中配置或切换至直连模式。"
            )
        }

        val endpoint = "$proxyBase/api/translate"
        try {
            val payload = mapOf(
                "text" to text,
                "from" to fromCode,
                "to" to toCode
            )
            val jsonBody = gson.toJson(payload)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("User-Agent", USER_AGENT)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = false,
                    errorMessage = "中转服务器响应错误 (HTTP ${response.code}): ${response.message}"
                )
            }

            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            val success = jsonObject.has("success") && jsonObject.get("success").asBoolean
            if (!success) {
                val errorMsg = if (jsonObject.has("error")) jsonObject.get("error").asString else "中转服务处理失败"
                return TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = false,
                    errorMessage = errorMsg
                )
            }

            val dataObj = jsonObject.getAsJsonObject("data")
            val translatedText = dataObj.get("translatedText")?.takeIf { !it.isJsonNull }?.asString ?: ""
            val detectedSource = dataObj.get("detectedSource")?.takeIf { !it.isJsonNull }?.asString

            val finalSource = if (from == Language.AUTO && !detectedSource.isNullOrBlank()) {
                Language.fromCode(detectedSource)
            } else {
                from
            }

            return TranslationResult(
                originalText = text,
                translatedText = translatedText,
                sourceLanguage = finalSource,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = true
            )
        } catch (e: Exception) {
            return TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLanguage = from,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = false,
                errorMessage = "连接 Google 中转服务器失败 (${e.localizedMessage ?: "网络不可达"})\n请检查网络连接或在设置中确认中转服务器地址是否有效。"
            )
        }
    }

    /**
     * 模式 2: 直接请求 Google 官方免 Key 端点
     */
    private fun translateDirect(
        text: String,
        fromCode: String,
        toCode: String,
        from: Language,
        to: Language
    ): TranslationResult {
        val formBody = FormBody.Builder()
            .add("q", text)
            .build()

        val url = "$GOOGLE_DIRECT_API?client=gtx&sl=$fromCode&tl=$toCode&dt=t"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .post(formBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = false,
                    errorMessage = "Google 官方接口 HTTP 错误: ${response.code} (若在国内请切换为【服务器中转代理】模式)"
                )
            }

            val jsonArray = gson.fromJson(responseBody, JsonArray::class.java)
            if (jsonArray == null || jsonArray.size() == 0 || !jsonArray.get(0).isJsonArray) {
                return TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = false,
                    errorMessage = "Google 翻译未返回有效数据"
                )
            }

            val sentences = jsonArray.get(0).asJsonArray
            val stringBuilder = StringBuilder()
            for (i in 0 until sentences.size()) {
                val element = sentences.get(i)
                if (element.isJsonArray) {
                    val sentenceObj = element.asJsonArray
                    if (sentenceObj.size() > 0 && !sentenceObj.get(0).isJsonNull) {
                        stringBuilder.append(sentenceObj.get(0).asString)
                    }
                }
            }
            val translatedText = stringBuilder.toString()

            var detectedSource = fromCode
            if (jsonArray.size() > 2 && !jsonArray.get(2).isJsonNull && jsonArray.get(2).isJsonPrimitive) {
                detectedSource = jsonArray.get(2).asString
            }

            val finalSource = if (from == Language.AUTO && detectedSource.isNotBlank()) {
                Language.fromCode(detectedSource)
            } else {
                from
            }

            TranslationResult(
                originalText = text,
                translatedText = translatedText,
                sourceLanguage = finalSource,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = true
            )
        } catch (e: Exception) {
            TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLanguage = from,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = false,
                errorMessage = "Google 官方直连异常: ${e.localizedMessage ?: "网络超时"}\n（提示：中国大陆网络无法直连 Google，请在【设置】中切换为【服务器中转代理】模式）"
            )
        }
    }

    override suspend fun testConnection(): Pair<Boolean, String> {
        val mode = getMode().trim().lowercase()
        val testText = "Hello SimpleTranslate"
        val result = translate(testText, Language.EN, Language.ZH)

        return if (result.isSuccess) {
            val modeName = if (mode == "direct") "官方直连模式" else "服务器中转模式"
            Pair(true, "Google 翻译 ($modeName) 连通成功！\n测试译文: ${result.translatedText}")
        } else {
            Pair(false, result.errorMessage ?: "Google 翻译测试连接失败")
        }
    }
}
