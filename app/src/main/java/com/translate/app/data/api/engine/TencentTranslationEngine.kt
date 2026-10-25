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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class TencentTranslationEngine(
    private val getSecretId: () -> String,
    private val getSecretKey: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : ITranslationEngine {

    override val engineType: TranslationEngineType = TranslationEngineType.TENCENT

    companion object {
        private const val HOST = "tmt.tencentcloudapi.com"
        private const val SERVICE = "tmt"
        private const val VERSION = "2018-03-21"
        private const val ACTION = "TextTranslate"
        private const val REGION = "ap-guangzhou"
    }

    override suspend fun translate(
        text: String,
        from: Language,
        to: Language
    ): TranslationResult = withContext(Dispatchers.IO) {
        val secretId = getSecretId().trim()
        val secretKey = getSecretKey().trim()

        if (secretId.isBlank() || secretKey.isBlank()) {
            return@withContext TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLanguage = from,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = false,
                errorMessage = "未配置腾讯云 SecretId 或 SecretKey，请在设置中配置或在代码中填写。"
            )
        }

        val fromCode = from.toTencentCode()
        val toCode = if (to == Language.AUTO) "zh" else to.toTencentCode()

        val payloadMap = mapOf(
            "SourceText" to text,
            "Source" to fromCode,
            "Target" to toCode,
            "ProjectId" to 0
        )
        val payloadJson = gson.toJson(payloadMap)

        val timestamp = System.currentTimeMillis() / 1000
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = sdf.format(Date(timestamp * 1000))

        // 计算 TC3-HMAC-SHA256 签名
        val canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:$HOST\nx-tc-action:${ACTION.lowercase()}\n"
        val signedHeaders = "content-type;host;x-tc-action"
        val hashedPayload = CryptoUtils.sha256(payloadJson)
        val canonicalRequest = "POST\n/\n\n$canonicalHeaders\n$signedHeaders\n$hashedPayload"

        val credentialScope = "$date/$SERVICE/tc3_request"
        val hashedCanonicalRequest = CryptoUtils.sha256(canonicalRequest)
        val stringToSign = "TC3-HMAC-SHA256\n$timestamp\n$credentialScope\n$hashedCanonicalRequest"

        val kKey = "TC3$secretKey".toByteArray(Charsets.UTF_8)
        val kDate = CryptoUtils.hmacSha256(kKey, date.toByteArray(Charsets.UTF_8))
        val kService = CryptoUtils.hmacSha256(kDate, SERVICE.toByteArray(Charsets.UTF_8))
        val kSigning = CryptoUtils.hmacSha256(kService, "tc3_request".toByteArray(Charsets.UTF_8))
        val signature = CryptoUtils.hmacSha256Hex(kSigning, stringToSign)

        val authorization = "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = payloadJson.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://$HOST")
            .post(body)
            .addHeader("Host", HOST)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("X-TC-Action", ACTION)
            .addHeader("X-TC-Version", VERSION)
            .addHeader("X-TC-Timestamp", timestamp.toString())
            .addHeader("X-TC-Region", REGION)
            .addHeader("Authorization", authorization)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = false,
                    errorMessage = "HTTP 错误: ${response.code}"
                )
            }

            val tencentResp = gson.fromJson(responseBody, TencentResponseWrapper::class.java)
            val err = tencentResp.response?.error
            if (err != null) {
                return@withContext TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = false,
                    errorMessage = "腾讯云错误 [${err.code}]: ${err.message}"
                )
            }

            val targetText = tencentResp.response?.targetText ?: ""
            TranslationResult(
                originalText = text,
                translatedText = targetText,
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
            Pair(true, "腾讯云机器翻译连接成功！测试翻译结果: ${result.translatedText}")
        } else {
            Pair(false, result.errorMessage ?: "测试连接失败")
        }
    }

    private data class TencentResponseWrapper(
        @SerializedName("Response")
        val response: TencentResponseBody?
    )

    private data class TencentResponseBody(
        @SerializedName("TargetText")
        val targetText: String?,
        @SerializedName("Source")
        val source: String?,
        @SerializedName("Target")
        val target: String?,
        @SerializedName("Error")
        val error: TencentError?
    )

    private data class TencentError(
        @SerializedName("Code")
        val code: String?,
        @SerializedName("Message")
        val message: String?
    )
}
