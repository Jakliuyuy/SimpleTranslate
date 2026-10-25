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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

class YoudaoTranslationEngine(
    private val getAppKey: () -> String,
    private val getAppSecret: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : ITranslationEngine {

    override val engineType: TranslationEngineType = TranslationEngineType.YOUDAO

    companion object {
        private const val API_URL = "https://openapi.youdao.com/api"
    }

    override suspend fun translate(
        text: String,
        from: Language,
        to: Language
    ): TranslationResult = withContext(Dispatchers.IO) {
        val appKey = getAppKey().trim()
        val appSecret = getAppSecret().trim()

        if (appKey.isBlank() || appSecret.isBlank()) {
            return@withContext TranslationResult(
                originalText = text,
                translatedText = "",
                sourceLanguage = from,
                targetLanguage = to,
                engineType = engineType,
                isSuccess = false,
                errorMessage = "未配置有道智云 AppKey 或 AppSecret，请在设置中配置或在代码中填写。"
            )
        }

        val salt = UUID.randomUUID().toString()
        val curtime = (System.currentTimeMillis() / 1000).toString()

        // 有道 V3 签名规则对输入文本的截断处理
        val truncatedQ = if (text.length <= 20) {
            text
        } else {
            "${text.take(10)}${text.length}${text.takeLast(10)}"
        }

        val signStr = "$appKey$truncatedQ$salt$curtime$appSecret"
        val sign = CryptoUtils.sha256(signStr)

        val fromCode = from.toYoudaoCode()
        val toCode = if (to == Language.AUTO) "zh-CHS" else to.toYoudaoCode()

        val formBody = FormBody.Builder()
            .add("q", text)
            .add("from", fromCode)
            .add("to", toCode)
            .add("appKey", appKey)
            .add("salt", salt)
            .add("sign", sign)
            .add("signType", "v3")
            .add("curtime", curtime)
            .build()

        val request = Request.Builder()
            .url(API_URL)
            .post(formBody)
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

            val youdaoResponse = gson.fromJson(responseBody, YoudaoResponse::class.java)

            if (youdaoResponse.errorCode != null && youdaoResponse.errorCode != "0") {
                val errorMsg = mapErrorCode(youdaoResponse.errorCode)
                return@withContext TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = from,
                    targetLanguage = to,
                    engineType = engineType,
                    isSuccess = false,
                    errorMessage = "有道翻译错误 [${youdaoResponse.errorCode}]: $errorMsg"
                )
            }

            val transText = youdaoResponse.translation?.joinToString("\n") ?: ""
            val phonetic = youdaoResponse.basic?.let {
                it.phonetic ?: it.usPhonetic ?: it.ukPhonetic
            }
            val explains = youdaoResponse.basic?.explains ?: emptyList()

            TranslationResult(
                originalText = text,
                translatedText = transText,
                sourceLanguage = from,
                targetLanguage = to,
                engineType = engineType,
                phonetic = if (!phonetic.isNullOrBlank()) "/$phonetic/" else null,
                explains = explains,
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
            Pair(true, "有道智云连接成功！测试翻译结果: ${result.translatedText}")
        } else {
            Pair(false, result.errorMessage ?: "测试连接失败")
        }
    }

    private fun mapErrorCode(code: String): String = when (code) {
        "101" -> "缺少必填的参数"
        "102" -> "不支持的语言类型"
        "103" -> "翻译文本过长"
        "108" -> "appKey无效，请核对应用ID是否正确"
        "110" -> "无有效实例，请在有道控制台确认是否绑定了文本翻译服务"
        "202" -> "签名检验失败，请检查 appSecret 应用密钥是否正确（勿带前后空格）"
        "205" -> "接口不支持该语种方向"
        "301" -> "辞典查询失败"
        "401" -> "账户已经欠费，请前往有道智云充值或领取免费额度"
        "411" -> "访问频率受限，请稍后重试"
        else -> "未知错误代码: $code"
    }

    private data class YoudaoResponse(
        @SerializedName("errorCode")
        val errorCode: String?,
        val query: String?,
        val translation: List<String>?,
        val basic: BasicDictionary?
    )

    private data class BasicDictionary(
        val phonetic: String?,
        @SerializedName("uk-phonetic")
        val ukPhonetic: String?,
        @SerializedName("us-phonetic")
        val usPhonetic: String?,
        val explains: List<String>?
    )
}
