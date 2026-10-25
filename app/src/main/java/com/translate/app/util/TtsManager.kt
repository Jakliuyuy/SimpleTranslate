package com.translate.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.translate.app.data.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })
        }
    }

    /**
     * 播放文本语音朗读
     */
    fun speak(text: String, language: Language, onError: ((String) -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            onError?.invoke("语音引擎正在初始化，请稍后重试")
            return
        }

        val targetLocale = resolveLocale(text, language)

        val result = tts?.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // 尝试回退到通用语言
            tts?.setLanguage(Locale.US)
        }

        val utteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun resolveLocale(text: String, language: Language): Locale {
        if (language.ttsLocale != null) {
            return language.ttsLocale
        }
        // 如果是自动检测，根据文本特征推断
        val hasChinese = text.any { it.code in 0x4E00..0x9FA5 }
        if (hasChinese) return Locale.SIMPLIFIED_CHINESE

        val hasJapanese = text.any { it.code in 0x3040..0x30FF }
        if (hasJapanese) return Locale.JAPANESE

        val hasKorean = text.any { it.code in 0xAC00..0xD7AF }
        if (hasKorean) return Locale.KOREAN

        return Locale.US
    }
}
