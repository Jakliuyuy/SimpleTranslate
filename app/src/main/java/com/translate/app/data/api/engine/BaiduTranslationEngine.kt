package com.translate.app.data.api.engine

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.translate.app.data.api.ITranslationEngine
import com.translate.app.data.model.Language
import com.translate.app.data.model.TranslationEngineType
import com.translate.app.data.model.TranslationResult
import com.translate.app.util.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class BaiduTranslationEngine(
    private val getAppId: () -> String,
    private val getSecretKey: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : ITranslationEngine {

    override val engineType: TranslationEngineType = TranslationEngineType.BAIDU

    companion object {
        private const val VIP_API_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate"
        private const val AI_API_URL = "https://fanyi-api.baidu.com/ait/api/aiTextTranslate"

        /**
         * 智能清洗 API 密钥：支持自动去除外层引号、Bearer 前缀、key: 前缀以及粘连的 "key" 字母。
         */
        fun sanitizeKey(key: String): String {
            var result = key.trim()
            // 剥离外层单双引号
            if ((result.startsWith("\"") && result.endsWith("\"")) ||
                (result.startsWith("'") && result.endsWith("'")) ||
                (result.startsWith("“") && result.endsWith("”"))
            ) {
                result = result.substring(1, result.length - 1).trim()
            }
            // 剥离 Bearer 前缀
            if (result.startsWith("Bearer ", ignoreCase = true)) {
                result = result.substring(7).trim()
            }
            // 剥离 key: 或 key= 前缀
            if (result.startsWith("key:", ignoreCase = true) || result.startsWith("key=", ignoreCase = true)) {
                result = result.substring(4).trim()
            }
            // 剥离粘连的 "key" 前缀 (例如 "keywMHD_...")
            if (result.length > 20 && result.startsWith("key", ignoreCase = true) && !result.startsWith("key_")) {
                result = result.substring(3).trim()
            }
            return result
        }
    }

    override suspend fun translate(
        text: String,
        from: Language,
        to: Language
    ): TranslationResult = withContext(Dispatchers.IO) {
        val appId = getAppId().trim()
        val secretKey = sanitizeKey(getSecretKey())

        if (secretKey.isBlank()) {
            return@withContext TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLanguage = from,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = false,
                errorMessage = "未配置百度翻译 API Key / 密钥，请在【设置】中配置或在代码中填写。"
            )
        }

        if (appId.isBlank()) {
            val keyMasked = if (secretKey.length > 8) "${secretKey.take(4)}...${secretKey.takeLast(4)}" else secretKey
            return@withContext TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLanguage = from,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = false,
                errorMessage = "已检测到百度翻译密钥 ($keyMasked)，但尚未配置 APP ID。\n百度开放平台规范要求请求必须携带您的【APP ID】（通常为 17 位纯数字）。请前往百度翻译开放平台 (fanyi-api.baidu.com) ->【开发者信息】获取并在本应用【设置】中填入即可使用！"
            )
        }

        val fromCode = from.toBaiduCode()
        val toCode = if (to == Language.AUTO) "zh" else to.toBaiduCode()

        // 优先尝试大模型翻译 API (基于 Authorization: Bearer Token 鉴权)
        val aiResult = tryAiTranslate(text, fromCode, toCode, appId, secretKey, from, to)
        if (aiResult.isSuccess) {
            return@withContext aiResult
        }

        // 若大模型接口未开通或异常，回退到通用文本翻译 API (MD5 签名鉴权)
        val vipResult = tryVipTranslate(text, fromCode, toCode, appId, secretKey, from, to)
        if (vipResult.isSuccess) {
            return@withContext vipResult
        }

        // 如果两者均未成功，优先展示针对性强、不具误导性的错误提示
        val combinedError = buildString {
            val aiMsg = aiResult.errorMessage
            val vipMsg = vipResult.errorMessage
            if (!aiMsg.isNullOrBlank() && aiMsg != "大模型翻译未响应") {
                append("大模型翻译: ").append(aiMsg)
                if (!vipMsg.isNullOrBlank() && vipMsg != aiMsg) {
                    append("；通用接口回退: ").append(vipMsg)
                }
            } else {
                append(vipMsg ?: "百度翻译请求失败")
            }
        }

        TranslationResult(
            originalText = text,
            translatedText = "",
            sourceLanguage = from,
            targetLanguage = to,
            engineType = engineType,
            isSuccess = false,
            errorMessage = combinedError
        )
    }

    private fun tryAiTranslate(
        text: String,
        fromCode: String,
        toCode: String,
        appId: String,
        apiKey: String,
        from: Language,
        to: Language
    ): TranslationResult {
        try {
            val payload = mapOf(
                "appid" to appId,
                "from" to fromCode,
                "to" to toCode,
                "q" to text
            )
            val jsonBody = gson.toJson(payload)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(AI_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
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
                    errorMessage = "HTTP 错误: ${response.code}"
                )
            }

            val baiduResponse = gson.fromJson(responseBody, BaiduResponse::class.java)
            if (!baiduResponse.errorCode.isNullOrEmpty() && baiduResponse.errorCode != "52000") {
                return TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = false,
                    errorMessage = mapErrorCode(baiduResponse.errorCode, baiduResponse.errorMsg)
                )
            }

            val transText = baiduResponse.transResult?.joinToString("\n") { it.dst }
            if (!transText.isNullOrBlank()) {
                return TranslationResult(
                    originalText = text,
                    translatedText = transText,
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = true
                )
            }
        } catch (_: Exception) {
            // 大模型调用异常直接返回失败，继续由通用接口承接
        }

        return TranslationResult(
            originalText = text,
            translatedText = "",
            sourceLanguage = from,
            targetLanguage = to,
            engineType = engineType,
            isSuccess = false,
            errorMessage = "大模型翻译未响应"
        )
    }

    private fun tryVipTranslate(
        text: String,
        fromCode: String,
        toCode: String,
        appId: String,
        secretKey: String,
        from: Language,
        to: Language
    ): TranslationResult {
        val salt = System.currentTimeMillis().toString()
        val signInput = "$appId$text$salt$secretKey"
        val sign = CryptoUtils.md5(signInput)

        val formBody = FormBody.Builder()
            .add("q", text)
            .add("from", fromCode)
            .add("to", toCode)
            .add("appid", appId)
            .add("salt", salt)
            .add("sign", sign)
            .build()

        val request = Request.Builder()
            .url(VIP_API_URL)
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
                    errorMessage = "HTTP 错误: ${response.code}"
                )
            }

            val baiduResponse = gson.fromJson(responseBody, BaiduResponse::class.java)
            if (!baiduResponse.errorCode.isNullOrEmpty() && baiduResponse.errorCode != "52000") {
                val errorMsg = mapErrorCode(baiduResponse.errorCode, baiduResponse.errorMsg)
                return TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = false,
                    errorMessage = "百度翻译错误 [${baiduResponse.errorCode}]: $errorMsg"
                )
            }

            val transText = baiduResponse.transResult?.joinToString("\n") { it.dst } ?: ""
            TranslationResult(
                originalText = text,
                translatedText = transText,
                sourceLanguage = from,
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
                errorMessage = "网络请求异常: ${e.localizedMessage ?: "连接失败"}"
            )
        }
    }

    override suspend fun testConnection(): Pair<Boolean, String> {
        val result = translate("hello", Language.EN, Language.ZH)
        return if (result.isSuccess) {
            Pair(true, "百度翻译连接成功！测试翻译结果: ${result.translatedText}")
        } else {
            Pair(false, result.errorMessage ?: "测试连接失败")
        }
    }

    private fun mapErrorCode(code: String, defaultMsg: String?): String = when (code) {
        "52001" -> "请求超时，请重试"
        "52002" -> "系统错误，请重试"
        "52003" -> "未授权用户，请检查 App ID 是否正确或通用文本翻译服务是否开通"
        "54000" -> "必填参数为空 (如缺少 appid 或待翻译文本)"
        "54001" -> "签名或 Token 错误，请检查密钥是否正确（注意不要包含多余空格）"
        "54003" -> "访问频率受限，请稍后再试"
        "54004" -> "账户余额不足，请前往百度翻译开放平台充值或开通免费额度"
        "54005" -> "长文本请求过于频繁"
        "58000" -> "客户端 IP 非法，请检查平台 IP 白名单设置"
        "58001" -> "不支持的译文语言方向"
        else -> defaultMsg ?: "未知错误 ($code)"
    }

    private data class BaiduResponse(
        val from: String?,
        val to: String?,
        @SerializedName("trans_result")
        val transResult: List<TransResultItem>?,
        @SerializedName("error_code")
        val errorCode: String?,
        @SerializedName("error_msg")
        val errorMsg: String?
    )

    private data class TransResultItem(
        val src: String,
        val dst: String
    )
}
