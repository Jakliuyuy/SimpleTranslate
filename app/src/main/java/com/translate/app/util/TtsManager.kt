package com.translate.app.util

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.translate.app.data.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 高保真 Text-To-Speech (TTS) 语音朗读管理引擎
 *
 * 核心强化点：
 * 1. 异步就绪容错队列：引擎未完成初始化时发起朗读，自动排队在就绪后立即发音，拒绝“正在初始化”阻断
 * 2. 全面支持多语种精准发音匹配：涵盖中简、中繁、英、日、韩、法、德、俄、西、意、葡等全量语种
 * 3. 语言包缺失智能检测与友好提示
 * 4. 长文本自动分句分段连续播放，突破 Android 4000 字符上限
 * 5. 健全的错误监听与错误码友好中文化
 */
class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // 语速设置 (0.5f ~ 2.0f，默认 1.0f)
    var speechRate: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    // 音调设置 (0.5f ~ 2.0f，默认 1.0f)
    var pitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    // 暂存待播放的请求 (若初始化较慢时触发)
    private var pendingRequest: PendingSpeakRequest? = null

    private data class PendingSpeakRequest(
        val text: String,
        val language: Language,
        val onError: ((String) -> Unit)?
    )

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            isInitialized = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(pitch)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    // 若分片全部播完（仅在末尾分片或唯一分片时），重置朗读状态
                    if (utteranceId?.endsWith("_last") == true) {
                        _isSpeaking.value = false
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })

            // 检查是否有排队等待播放的文本
            pendingRequest?.let { pending ->
                pendingRequest = null
                speak(pending.text, pending.language, pending.onError)
            }
        } else {
            isInitialized = false
            pendingRequest?.onError?.invoke("系统语音引擎初始化失败，请在系统设置中检查 TTS 服务")
            pendingRequest = null
        }
    }

    /**
     * 播放文本语音朗读
     *
     * @param text 待朗读文本
     * @param language 语种
     * @param onError 错误回调（返回友好提示）
     */
    fun speak(text: String, language: Language, onError: ((String) -> Unit)? = null) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        if (!isInitialized || tts == null) {
            // 引擎还在加载中，记录待播放请求，初始化完成后自动发音
            pendingRequest = PendingSpeakRequest(cleanText, language, onError)
            return
        }

        val targetLocale = resolveLocale(cleanText, language)
        val engine = tts ?: return

        // 检查目标语种在当前设备 TTS 中的支持状态
        val availability = engine.isLanguageAvailable(targetLocale)
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            // 尝试回退
            val fallbackLocale = resolveFallbackLocale(targetLocale)
            val fbAvailability = engine.isLanguageAvailable(fallbackLocale)
            if (fbAvailability == TextToSpeech.LANG_NOT_SUPPORTED || fbAvailability == TextToSpeech.LANG_MISSING_DATA) {
                onError?.invoke("当前系统语音引擎不支持 ${language.displayName} 朗读，请在系统【设置 -> 无障碍 -> 文字转语音】中安装该语种语音包")
                return
            }
            engine.language = fallbackLocale
        } else if (availability == TextToSpeech.LANG_MISSING_DATA) {
            onError?.invoke("当前设备缺少 ${language.displayName} 的语音数据包，请在系统 TTS 设置中下载更新")
            // 仍尝试设置，系统可能会在线拉取或回退
            engine.language = targetLocale
        } else {
            engine.language = targetLocale
        }

        // 处理长文本分段朗读（避免超过系统限制 4000 字符）
        val chunks = splitTextIntoChunks(cleanText, 500)
        _isSpeaking.value = true

        val baseUtteranceId = "tts_${System.currentTimeMillis()}"
        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = if (index == chunks.size - 1) "${baseUtteranceId}_last" else "${baseUtteranceId}_$index"

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = engine.speak(chunk, queueMode, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                _isSpeaking.value = false
                onError?.invoke("语音播放失败，请稍后重试")
                return
            }
        }
    }

    /**
     * 停止朗读
     */
    fun stop() {
        pendingRequest = null
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    /**
     * 释放 TTS 资源
     */
    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }

    /**
     * 根据语言模型与文本内容智能匹配最佳发音 Locale
     */
    private fun resolveLocale(text: String, language: Language): Locale {
        if (language.ttsLocale != null) {
            return language.ttsLocale
        }

        // 精确匹配语种代码
        when (language.code.lowercase()) {
            "zh", "zh-cn", "zh-hans" -> return Locale.SIMPLIFIED_CHINESE
            "cht", "zh-tw", "zh-hk", "zh-hant" -> return Locale.TRADITIONAL_CHINESE
            "en", "en-us", "en-gb" -> return Locale.US
            "ja" -> return Locale.JAPANESE
            "ko" -> return Locale.KOREAN
            "fr" -> return Locale.FRANCE
            "de" -> return Locale.GERMANY
            "ru" -> return Locale("ru", "RU")
            "es" -> return Locale("es", "ES")
            "it" -> return Locale.ITALIAN
            "pt" -> return Locale("pt", "PT")
            "vi" -> return Locale("vi", "VN")
            "th" -> return Locale("th", "TH")
            "ar" -> return Locale("ar")
        }

        // 若为 auto 自动识别，根据文本字符集推断语种
        val hasChinese = text.any { it.code in 0x4E00..0x9FA5 }
        if (hasChinese) return Locale.SIMPLIFIED_CHINESE

        val hasJapanese = text.any { it.code in 0x3040..0x30FF }
        if (hasJapanese) return Locale.JAPANESE

        val hasKorean = text.any { it.code in 0xAC00..0xD7AF }
        if (hasKorean) return Locale.KOREAN

        val hasCyrillic = text.any { it.code in 0x0400..0x04FF }
        if (hasCyrillic) return Locale("ru", "RU")

        val hasArabic = text.any { it.code in 0x0600..0x06FF }
        if (hasArabic) return Locale("ar")

        return Locale.US
    }

    /**
     * 回退 Locale
     */
    private fun resolveFallbackLocale(primary: Locale): Locale {
        return when (primary.language) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.US
        }
    }

    companion object {
        /**
         * 将长文本按照标点符号合理分句切片，保留标点符号停顿，防止超过系统限制 (如 Android 4000 字符限制)
         */
        fun splitTextIntoChunks(text: String, maxChunkSize: Int = 500): List<String> {
            if (text.length <= maxChunkSize) {
                return listOf(text)
            }

            val chunks = mutableListOf<String>()
            val sb = StringBuilder()

            for (ch in text) {
                sb.append(ch)
                val isPunctuation = ch in "。！？!?；;…\n"

                // 遇到断句标点且累积长度达到合适规模，或者单段逼近 maxChunkSize 时切分
                if ((isPunctuation && sb.length >= 80) || sb.length >= maxChunkSize) {
                    val part = sb.toString().trim()
                    if (part.isNotEmpty()) {
                        chunks.add(part)
                    }
                    sb.clear()
                }
            }

            if (sb.isNotEmpty()) {
                val remaining = sb.toString().trim()
                if (remaining.isNotEmpty()) {
                    chunks.add(remaining)
                }
            }

            return if (chunks.isEmpty()) listOf(text) else chunks
        }
    }
}
