package com.translate.app.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translate.app.SimpleTranslateApp
import com.translate.app.data.model.ImageTranslateUiState
import com.translate.app.data.model.Language
import com.translate.app.data.model.TranslatedBlock
import com.translate.app.util.ImageBitmapRenderer
import com.translate.app.util.ImageOverlayHelper
import com.translate.app.util.OcrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 原位实景图片翻译 (In-place Image Translation) 专属 ViewModel
 */
class ImageTranslateViewModel : ViewModel() {

    private val repository = SimpleTranslateApp.instance.translationRepository
    private val ttsManager = SimpleTranslateApp.instance.ttsManager

    private val _uiState = MutableStateFlow(ImageTranslateUiState())
    val uiState: StateFlow<ImageTranslateUiState> = _uiState.asStateFlow()

    private var translateJob: Job? = null

    /**
     * 加载图片并启动 OCR 识别与自动批量翻译
     */
    fun loadImageAndTranslate(
        context: Context,
        uri: Uri,
        initialSourceLang: Language = Language.AUTO,
        initialTargetLang: Language = Language.ZH
    ) {
        translateJob?.cancel()
        _uiState.value = ImageTranslateUiState(
            isLoading = true,
            loadingMessage = "正在读取图片并高精度识别文字...",
            imageUri = uri,
            sourceLanguage = initialSourceLang,
            targetLanguage = initialTargetLang
        )

        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            OcrManager.recognizeBlocks(
                context = appContext,
                imageUri = uri,
                onSuccess = { blocks, bitmap ->
                    // 智能推断语种
                    val combinedText = blocks.joinToString("\n") { it.originalText }
                    var effSource = initialSourceLang
                    var effTarget = initialTargetLang

                    if (effSource == Language.AUTO) {
                        effTarget = Language.autoDetectTargetLanguage(combinedText, effTarget)
                        val detected = Language.detectLanguage(combinedText)
                        effSource = if (detected != Language.AUTO) detected else Language.EN
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            originalBitmap = bitmap,
                            blocks = blocks,
                            sourceLanguage = effSource,
                            targetLanguage = effTarget,
                            totalBlockCount = blocks.size,
                            translatedBlockCount = 0
                        )
                    }

                    // 启动各文本块批量翻译
                    startBatchTranslation(blocks, effSource, effTarget)
                },
                onError = { errorMsg ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorMsg
                        )
                    }
                }
            )
        }
    }

    /**
     * 批量并发翻译所有文本块并动态更新原位覆写数据
     */
    fun startBatchTranslation(
        blocks: List<TranslatedBlock>,
        from: Language,
        to: Language
    ) {
        translateJob?.cancel()
        translateJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isRetranslating = true,
                    loadingMessage = "正在原位翻译 (${from.displayName} → ${to.displayName})...",
                    totalBlockCount = blocks.size,
                    translatedBlockCount = 0
                )
            }

            // 文本去重缓存
            val translationCache = ConcurrentHashMap<String, String>()
            val semaphore = Semaphore(4) // 限制并发数为 4，既极速响应又彻底杜绝 API 429 限流
            var completedCount = 0

            val deferredTranslations = blocks.map { block ->
                async {
                    val raw = block.originalText.trim()
                    if (raw.isBlank()) {
                        return@async block.copy(translatedText = "", isTranslating = false)
                    }

                    semaphore.withPermit {
                        val cached = translationCache[raw]
                        val translatedText = if (cached != null) {
                            cached
                        } else {
                            try {
                                val res = repository.translate(
                                    text = raw,
                                    from = from,
                                    to = to,
                                    saveToHistory = false
                                )
                                if (res.isSuccess && res.translatedText.isNotBlank()) {
                                    translationCache[raw] = res.translatedText
                                    res.translatedText
                                } else {
                                    raw
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                raw
                            }
                        }

                        synchronized(this@ImageTranslateViewModel) {
                            completedCount++
                            val currentCompleted = completedCount
                            _uiState.update { state ->
                                val updatedBlocks = state.blocks.map { b ->
                                    if (b.id == block.id) b.copy(translatedText = translatedText, isTranslating = false)
                                    else b
                                }
                                state.copy(
                                    blocks = updatedBlocks,
                                    translatedBlockCount = currentCompleted
                                )
                            }
                        }

                        block.copy(translatedText = translatedText, isTranslating = false)
                    }
                }
            }

            deferredTranslations.awaitAll()

            _uiState.update {
                it.copy(
                    isRetranslating = false,
                    loadingMessage = ""
                )
            }
        }
    }

    /**
     * 切换【显示译文】/【显示原文】
     */
    fun toggleOverlayMode() {
        _uiState.update { it.copy(showTranslationOverlay = !it.showTranslationOverlay) }
    }

    /**
     * 明确设置覆盖层显示状态
     */
    fun setOverlayMode(showTranslation: Boolean) {
        _uiState.update { it.copy(showTranslationOverlay = showTranslation) }
    }

    /**
     * 选中或取消选中文本块
     */
    fun selectBlock(block: TranslatedBlock?) {
        _uiState.update { it.copy(selectedBlockId = block?.id) }
    }

    /**
     * 切换目标语言并立即重新翻译
     */
    fun setTargetLanguage(newTarget: Language) {
        if (_uiState.value.targetLanguage == newTarget) return
        _uiState.update { it.copy(targetLanguage = newTarget) }
        retranslateCurrentBlocks()
    }

    /**
     * 切换源语言并立即重新翻译
     */
    fun setSourceLanguage(newSource: Language) {
        if (_uiState.value.sourceLanguage == newSource) return
        _uiState.update { it.copy(sourceLanguage = newSource) }
        retranslateCurrentBlocks()
    }

    /**
     * 一键互换源语言与目标语言并重新翻译 (Google 翻译同款)
     */
    fun swapLanguages() {
        val currentSource = _uiState.value.sourceLanguage
        val currentTarget = _uiState.value.targetLanguage

        val newSource = currentTarget
        val newTarget = if (currentSource == Language.AUTO) {
            Language.ZH
        } else {
            currentSource
        }

        _uiState.update {
            it.copy(
                sourceLanguage = newSource,
                targetLanguage = newTarget
            )
        }
        retranslateCurrentBlocks()
    }

    /**
     * 重新翻译当前所有块
     */
    fun retranslateCurrentBlocks() {
        val currentBlocks = _uiState.value.blocks
        if (currentBlocks.isEmpty()) return
        startBatchTranslation(
            blocks = currentBlocks,
            from = _uiState.value.sourceLanguage,
            to = _uiState.value.targetLanguage
        )
    }

    /**
     * 复制全部文本（原文与译文结构化呈现）到系统剪贴板
     */
    fun copyAllText(context: Context) {
        val blocks = _uiState.value.blocks
        if (blocks.isEmpty()) {
            Toast.makeText(context, "当前无提取的文本内容", Toast.LENGTH_SHORT).show()
            return
        }
        val formatted = ImageOverlayHelper.formatAllBlocksText(blocks, includeTranslation = true)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Image Translation", formatted)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制全部图片文字及对应译文", Toast.LENGTH_SHORT).show()
    }

    /**
     * 朗读指定文本
     */
    fun speakText(text: String, language: Language) {
        if (text.isBlank()) return
        ttsManager.speak(text, language)
    }

    /**
     * 将覆写了译文的图片合成并保存到系统相册
     */
    fun saveTranslatedImageToGallery(context: Context, onComplete: (Boolean, String) -> Unit) {
        val state = _uiState.value
        val bitmap = state.originalBitmap
        if (bitmap == null || state.blocks.isEmpty()) {
            onComplete(false, "图片尚未准备就绪")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val synthesizedBitmap = ImageBitmapRenderer.renderOverlayOnBitmap(bitmap, state.blocks)
                val savedUri = ImageBitmapRenderer.saveBitmapToGallery(context, synthesizedBitmap)
                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        onComplete(true, "翻译图片已成功保存至系统相册！")
                    } else {
                        onComplete(false, "保存图片失败，请检查存储权限")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "保存异常: ${e.localizedMessage ?: "未知错误"}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        translateJob?.cancel()
    }
}
