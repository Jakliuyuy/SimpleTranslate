package com.translate.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.translate.app.data.api.engine.GoogleTranslationEngine
import com.translate.app.data.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.util.Locale

/**
 * 高保真 Text-To-Speech (TTS) 语音朗读管理引擎
 *
 * 核心升级（本地 + 在线双模兜底）：
 * 1. 本地 TTS 优先：检测系统内置 TTS 引擎与离线语言包，正常情况下本地毫秒级响应
 * 2. 在线网络发音双模兜底 (Fallback)：
 *    - 当系统未安装 TTS 引擎、未完成初始化、不支持该语种或缺少离线数据包 (LANG_MISSING_DATA / LANG_NOT_SUPPORTED) 时，
 *      立即自动无缝切换到高保真网络音频流播放（有道公共发音 + SimpleTranslate 中转代理 TTS + Google TTS 免 Key 接口），
 *      彻底解决国产安卓 ROM（小米/澎湃OS、华为、OPPO、vivo等）点击喇叭静默无声的问题！
 * 3. 规范使用 STREAM_MUSIC 媒体音频流，并智能检测媒体音量为 0 / 静音模式并提供友好提醒
 * 4. 支持长文本智能分句分片连续播放，突破系统与接口长度限制
 */
class TtsManager(
    private val context: Context,
    private val getProxyUrl: () -> String = {
        try {
            com.translate.app.SimpleTranslateApp.instance.preferenceRepository.googleProxyUrl
        } catch (_: Exception) {
            "http://129.151.9.55:8787"
        }
    }
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // 在线音频播放器
    private var mediaPlayer: MediaPlayer? = null
    // 当前朗读全局会话 ID（每次新发音/停止递增，防止异步竞态与过期回调）
    private var currentSessionId: Long = 0L

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

    // 本地发音活跃会话（用于 onError 降级与看门狗）
    private var activeLocalSession: ActiveSpeakSession? = null
    private var watchdogRunnable: Runnable? = null

    private data class PendingSpeakRequest(
        val text: String,
        val language: Language,
        val onError: ((String) -> Unit)?
    )

    private data class ActiveSpeakSession(
        val sessionId: Long,
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
                    // 本地引擎已真正开始发声，取消假死看门狗
                    cancelWatchdog()
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    cancelWatchdog()
                    if (utteranceId?.endsWith("_last") == true) {
                        _isSpeaking.value = false
                        activeLocalSession = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handleLocalTtsError()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    handleLocalTtsError()
                }
            })

            // 检查是否有排队等待播放的文本
            pendingRequest?.let { pending ->
                pendingRequest = null
                speak(pending.text, pending.language, pending.onError)
            }
        } else {
            isInitialized = false
            // 若本地 TTS 初始化失败，若有排队请求直接切换为在线播放兜底
            pendingRequest?.let { pending ->
                pendingRequest = null
                speakOnline(pending.text, pending.language, pending.onError)
            }
        }
    }

    /**
     * 当本地 TTS 引擎在合成或发音过程中报错时，立即无缝降级走在线网络发音
     */
    private fun handleLocalTtsError() {
        cancelWatchdog()
        val session = activeLocalSession ?: run {
            _isSpeaking.value = false
            return
        }
        activeLocalSession = null

        mainHandler.post {
            if (session.sessionId == currentSessionId) {
                // 本地引擎异步失败，无缝降级在线网络发音
                speakOnline(session.text, session.language, session.onError)
            }
        }
    }

    /**
     * 启动本地 TTS 假死监控看门狗 (800ms)
     * 若部分国内厂商 ROM 的 TTS 假死未报错也未回调 onStart，看门狗自动介入无缝切换在线发音
     */
    private fun startWatchdog(session: ActiveSpeakSession) {
        cancelWatchdog()
        val runnable = Runnable {
            if (session.sessionId == currentSessionId && _isSpeaking.value) {
                try {
                    tts?.stop()
                } catch (_: Exception) {}
                activeLocalSession = null
                speakOnline(session.text, session.language, session.onError)
            }
        }
        watchdogRunnable = runnable
        mainHandler.postDelayed(runnable, 800)
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    /**
     * 播放文本语音朗读（本地 TTS + 在线网络发音双模兜底）
     *
     * @param text 待朗读文本
     * @param language 语种
     * @param onError 错误回调（返回友好提示）
     */
    fun speak(text: String, language: Language, onError: ((String) -> Unit)? = null) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        // 停止之前的播放
        stop()

        val sessionId = ++currentSessionId

        // 检查媒体音量与静音状态，友好提示
        checkVolumeAndNotify(onError)

        // 1. 如果本地 TTS 未初始化或者不可用，直接无缝走在线网络发音兜底
        if (!isInitialized || tts == null) {
            speakOnline(cleanText, language, onError)
            return
        }

        val engine = tts ?: run {
            speakOnline(cleanText, language, onError)
            return
        }

        val targetLocale = resolveLocale(cleanText, language)

        // 2. 检查本地语种支持状态
        val availability = try {
            engine.isLanguageAvailable(targetLocale)
        } catch (_: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }

        val effectiveLocale: Locale? = when (availability) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> targetLocale

            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                // 尝试回退 Locale
                val fallbackLocale = resolveFallbackLocale(targetLocale)
                val fbAvail = try {
                    engine.isLanguageAvailable(fallbackLocale)
                } catch (_: Exception) {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }
                if (fbAvail == TextToSpeech.LANG_AVAILABLE || fbAvail == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                    fallbackLocale
                } else {
                    null // 本地 TTS 完全不支持或缺少离线语音包
                }
            }
            else -> null
        }

        // 若本地 TTS 缺少离线语音包或不支持该语言，立即无缝自动切换到在线网络发音
        if (effectiveLocale == null) {
            speakOnline(cleanText, language, onError)
            return
        }

        // 尝试设置本地语言
        try {
            val setLangResult = engine.setLanguage(effectiveLocale)
            if (setLangResult == TextToSpeech.LANG_MISSING_DATA || setLangResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                speakOnline(cleanText, language, onError)
                return
            }
        } catch (_: Exception) {
            speakOnline(cleanText, language, onError)
            return
        }

        // 本地 TTS 播放
        val chunks = splitTextIntoChunks(cleanText, 500)
        _isSpeaking.value = true

        val session = ActiveSpeakSession(sessionId, cleanText, language, onError)
        activeLocalSession = session
        startWatchdog(session)

        val baseUtteranceId = "tts_${System.currentTimeMillis()}"
        var hasError = false
        chunks.forEachIndexed { index, chunk ->
            if (hasError) return@forEachIndexed
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = if (index == chunks.size - 1) "${baseUtteranceId}_last" else "${baseUtteranceId}_$index"

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = engine.speak(chunk, queueMode, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                hasError = true
            }
        }

        if (hasError) {
            cancelWatchdog()
            activeLocalSession = null
            _isSpeaking.value = false
            speakOnline(cleanText, language, onError)
        }
    }

    /**
     * 在线网络音频流高保真发音（多源容错兜底）
     */
    fun speakOnline(text: String, language: Language, onError: ((String) -> Unit)? = null) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        val sessionId = ++currentSessionId
        cancelWatchdog()
        activeLocalSession = null

        val chunks = splitTextIntoChunks(cleanText, 150)
        if (chunks.isEmpty()) return

        _isSpeaking.value = true
        playOnlineChunk(chunks, 0, sessionId, language, onError)
    }

    private fun playOnlineChunk(
        chunks: List<String>,
        chunkIndex: Int,
        sessionId: Long,
        language: Language,
        onError: ((String) -> Unit)?
    ) {
        if (sessionId != currentSessionId) return

        if (chunkIndex >= chunks.size) {
            _isSpeaking.value = false
            return
        }

        val chunk = chunks[chunkIndex]
        val candidateUrls = buildOnlineAudioUrls(chunk, language, getProxyUrl())
        if (candidateUrls.isEmpty()) {
            _isSpeaking.value = false
            onError?.invoke("未找到适用的发音资源")
            return
        }

        tryPlaySource(chunks, chunkIndex, candidateUrls, 0, sessionId, language, onError)
    }

    private fun tryPlaySource(
        chunks: List<String>,
        chunkIndex: Int,
        candidateUrls: List<String>,
        sourceIndex: Int,
        sessionId: Long,
        language: Language,
        onError: ((String) -> Unit)?
    ) {
        if (sessionId != currentSessionId) return

        if (sourceIndex >= candidateUrls.size) {
            // 当前切片的所有音频源均失败
            if (chunkIndex == 0) {
                _isSpeaking.value = false
                safeReleasePlayer()
                onError?.invoke("网络发音暂不可用，请检查网络连接")
            } else {
                playOnlineChunk(chunks, chunkIndex + 1, sessionId, language, onError)
            }
            return
        }

        val url = candidateUrls[sourceIndex]

        mainHandler.post {
            if (sessionId != currentSessionId) return@post

            safeReleasePlayer()
            try {
                val player = MediaPlayer()
                mediaPlayer = player

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    player.setAudioStreamType(AudioManager.STREAM_MUSIC)
                }

                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                player.setDataSource(context.applicationContext, Uri.parse(url), headers)

                player.setOnPreparedListener { mp ->
                    if (sessionId == currentSessionId && mediaPlayer === mp) {
                        try {
                            mp.start()
                            _isSpeaking.value = true
                        } catch (_: Exception) {
                            tryPlaySource(chunks, chunkIndex, candidateUrls, sourceIndex + 1, sessionId, language, onError)
                        }
                    }
                }

                player.setOnCompletionListener { mp ->
                    if (sessionId == currentSessionId && mediaPlayer === mp) {
                        playOnlineChunk(chunks, chunkIndex + 1, sessionId, language, onError)
                    }
                }

                player.setOnErrorListener { mp, _, _ ->
                    if (sessionId == currentSessionId && mediaPlayer === mp) {
                        tryPlaySource(chunks, chunkIndex, candidateUrls, sourceIndex + 1, sessionId, language, onError)
                    }
                    true
                }

                player.prepareAsync()
            } catch (_: Exception) {
                tryPlaySource(chunks, chunkIndex, candidateUrls, sourceIndex + 1, sessionId, language, onError)
            }
        }
    }

    /**
     * 安全解绑监听并释放 MediaPlayer
     */
    private fun safeReleasePlayer() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        try {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            if (player.isPlaying) {
                player.stop()
            }
            player.reset()
            player.release()
        } catch (_: Exception) {}
    }

    /**
     * 检查当前媒体音量并给出友好提醒
     */
    private fun checkVolumeAndNotify(onError: ((String) -> Unit)?) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (volume == 0) {
                    onError?.invoke("提示：当前媒体音量为 0，请调大音量收听朗读")
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 停止朗读
     */
    fun stop() {
        pendingRequest = null
        currentSessionId++ // 使正在等待的在线切片和回调失效
        cancelWatchdog()
        activeLocalSession = null

        try {
            tts?.stop()
        } catch (_: Exception) {}

        mainHandler.post {
            safeReleasePlayer()
        }

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

        mainHandler.post {
            safeReleasePlayer()
        }
    }

    /**
     * 根据语言模型与文本内容智能匹配最佳发音 Locale
     */
    private fun resolveLocale(text: String, language: Language): Locale {
        val effectiveLang = if (language == Language.AUTO) {
            Language.detectLanguage(text)
        } else {
            language
        }

        if (effectiveLang.ttsLocale != null) {
            return effectiveLang.ttsLocale
        }

        // 精确匹配语种代码
        when (effectiveLang.code.lowercase()) {
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
         * 构建在线发音音频流候选 URL 列表（多源智能容错优先级）
         */
        fun buildOnlineAudioUrls(chunk: String, language: Language, proxyBaseUrl: String): List<String> {
            val clean = chunk.trim()
            if (clean.isEmpty()) return emptyList()

            val urls = mutableListOf<String>()
            val encoded = try {
                URLEncoder.encode(clean, "UTF-8")
            } catch (_: Exception) {
                clean
            }
            val targetLang = if (language == Language.AUTO) Language.detectLanguage(clean) else language
            val googleLang = GoogleTranslationEngine.toGoogleLanguageCode(targetLang)

            // 1. 有道词典在线发音（仅支持短词/短语，不超过 2 个单词且字符数 <= 25 且无长句标点，国内免梯子秒开）
            val hasSentencePunct = clean.any { it in "。！？!?；;，,……\n" }
            val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val isShortWord = !hasSentencePunct && words.size <= 2 && clean.length <= 25

            if (isShortWord) {
                when (targetLang.code.lowercase()) {
                    "en" -> urls.add("https://dict.youdao.com/dictvoice?audio=$encoded&type=2")
                    "zh", "zh-cn" -> urls.add("https://dict.youdao.com/dictvoice?audio=$encoded&le=zh")
                    "ja" -> urls.add("https://dict.youdao.com/dictvoice?audio=$encoded&le=jap")
                    "ko" -> urls.add("https://dict.youdao.com/dictvoice?audio=$encoded&le=ko")
                }
            }

            // 2. SimpleTranslate 中转代理 TTS 音频流（国内免梯子直连中转，长句短句多语种全覆盖）
            val trimmedProxy = proxyBaseUrl.trim().trimEnd('/')
            if (trimmedProxy.isNotBlank() && (trimmedProxy.startsWith("http://") || trimmedProxy.startsWith("https://"))) {
                urls.add("$trimmedProxy/api/tts?text=$encoded&lang=$googleLang")
            }

            // 3. Google 官方公开免 Key TTS 音频流 (全语种长句直连备用)
            urls.add("https://translate.google.com/translate_tts?ie=UTF-8&tl=$googleLang&client=tw-ob&q=$encoded")

            return urls
        }

        /**
         * 将长文本按照标点符号合理分句切片，保留标点符号停顿，防止超过系统或接口限制
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
                if ((isPunctuation && sb.length >= 60) || sb.length >= maxChunkSize) {
                    val currentStr = sb.toString()
                    if (isPunctuation || sb.length < maxChunkSize) {
                        val part = currentStr.trim()
                        if (part.isNotEmpty()) chunks.add(part)
                        sb.clear()
                    } else {
                        // 超过最大长度但没有句子结束标点：寻找最近的逗号、空格等边界，避免切断单词
                        val lastBreak = currentStr.lastIndexOfAny(charArrayOf(' ', ',', '，', '、', '\t'))
                        if (lastBreak > 20) {
                            val part = currentStr.substring(0, lastBreak).trim()
                            if (part.isNotEmpty()) chunks.add(part)
                            val remainder = currentStr.substring(lastBreak).trimStart()
                            sb.clear()
                            sb.append(remainder)
                        } else {
                            val part = currentStr.trim()
                            if (part.isNotEmpty()) chunks.add(part)
                            sb.clear()
                        }
                    }
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
