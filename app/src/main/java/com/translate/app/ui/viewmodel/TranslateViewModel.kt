package com.translate.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translate.app.SimpleTranslateApp
import com.translate.app.data.db.TranslationRecord
import com.translate.app.data.model.Language
import com.translate.app.data.model.TranslationEngineType
import com.translate.app.data.model.TranslationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TranslateViewModel : ViewModel() {

    private val repository = SimpleTranslateApp.instance.translationRepository
    private val prefRepo = SimpleTranslateApp.instance.preferenceRepository
    private val ttsManager = SimpleTranslateApp.instance.ttsManager

    // 输入与翻译状态
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _translationResult = MutableStateFlow<TranslationResult?>(null)
    val translationResult: StateFlow<TranslationResult?> = _translationResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 当前翻译结果的收藏状态
    private val _isCurrentResultFavorite = MutableStateFlow(false)
    val isCurrentResultFavorite: StateFlow<Boolean> = _isCurrentResultFavorite.asStateFlow()

    // 语种状态
    private val _sourceLanguage = MutableStateFlow(Language.fromCode(prefRepo.lastSourceLang))
    val sourceLanguage: StateFlow<Language> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow(
        if (prefRepo.lastTargetLang == Language.AUTO.code) Language.EN
        else Language.fromCode(prefRepo.lastTargetLang)
    )
    val targetLanguage: StateFlow<Language> = _targetLanguage.asStateFlow()

    // 历史与收藏
    private val _historyList = MutableStateFlow<List<TranslationRecord>>(emptyList())
    val historyList: StateFlow<List<TranslationRecord>> = _historyList.asStateFlow()

    private val _favoritesList = MutableStateFlow<List<TranslationRecord>>(emptyList())
    val favoritesList: StateFlow<List<TranslationRecord>> = _favoritesList.asStateFlow()

    // 引擎设置与测试
    private val _selectedEngine = MutableStateFlow(prefRepo.selectedEngineType)
    val selectedEngine: StateFlow<TranslationEngineType> = _selectedEngine.asStateFlow()

    private val _testResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testResult: StateFlow<Pair<Boolean, String>?> = _testResult.asStateFlow()

    // 全屏展示弹窗
    private val _fullscreenText = MutableStateFlow<String?>(null)
    val fullscreenText: StateFlow<String?> = _fullscreenText.asStateFlow()

    // TTS 状态
    val isSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking

    private var debounceJob: Job? = null

    init {
        loadHistory()
        loadFavorites()
    }

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
        debounceJob?.cancel()

        if (newText.trim().isEmpty()) {
            _translationResult.value = null
            _isLoading.value = false
            return
        }

        // 若源语言为【自动检测 (AUTO)】，根据输入文本特征进行智能判断与目标语言动态对调：
        // 中文 -> 目标语言设为英文 (Language.EN)；英文及其他语种 -> 目标语言设为中文 (Language.ZH)
        if (_sourceLanguage.value == Language.AUTO) {
            val autoTarget = Language.autoDetectTargetLanguage(newText, _targetLanguage.value)
            if (_targetLanguage.value != autoTarget) {
                _targetLanguage.value = autoTarget
                prefRepo.lastTargetLang = autoTarget.code
            }
        }

        debounceJob = viewModelScope.launch {
            delay(500) // 防抖 500ms
            performTranslate(newText)
        }
    }

    fun setSourceLanguage(lang: Language) {
        _sourceLanguage.value = lang
        prefRepo.lastSourceLang = lang.code
        val text = _inputText.value.trim()
        if (text.isNotEmpty() && lang == Language.AUTO) {
            val autoTarget = Language.autoDetectTargetLanguage(text, _targetLanguage.value)
            if (_targetLanguage.value != autoTarget) {
                _targetLanguage.value = autoTarget
                prefRepo.lastTargetLang = autoTarget.code
            }
        }
        triggerImmediateTranslate()
    }

    fun setTargetLanguage(lang: Language) {
        _targetLanguage.value = lang
        prefRepo.lastTargetLang = lang.code
        // 用户手动选择目标语言时，直接执行即时翻译，绝不覆盖用户手动选择的目标语言
        debounceJob?.cancel()
        val text = _inputText.value.trim()
        if (text.isNotEmpty()) {
            viewModelScope.launch {
                performTranslate(text)
            }
        }
    }

    fun swapLanguages() {
        val currentSrc = _sourceLanguage.value
        val currentTgt = _targetLanguage.value

        // 新源语言直接取当前目标语言
        val newSrc = currentTgt
        // 若源语言为 AUTO，避免对调后同为中文：若当前目标为中文则对调后目标为英文，反之为中文
        val newTgt = if (currentSrc == Language.AUTO) {
            if (currentTgt == Language.ZH) Language.EN else Language.ZH
        } else {
            currentSrc
        }

        _sourceLanguage.value = newSrc
        _targetLanguage.value = newTgt
        prefRepo.lastSourceLang = newSrc.code
        prefRepo.lastTargetLang = newTgt.code

        // 如果已有译文，将译文作为新的输入
        val currentResult = _translationResult.value
        if (currentResult != null && currentResult.translatedText.isNotBlank()) {
            _inputText.value = currentResult.translatedText
            triggerImmediateTranslate()
        }
    }

    fun triggerImmediateTranslate() {
        debounceJob?.cancel()
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            performTranslate(text)
        }
    }

    private suspend fun performTranslate(text: String) {
        _isLoading.value = true

        // 防冲突安全校验：若源语言为 AUTO，基于主体语言推导目标语言，彻底避免二值误判导致中译中或英译英
        if (_sourceLanguage.value == Language.AUTO) {
            val desiredTarget = Language.autoDetectTargetLanguage(text, _targetLanguage.value)
            if (_targetLanguage.value != desiredTarget) {
                _targetLanguage.value = desiredTarget
                prefRepo.lastTargetLang = desiredTarget.code
            }
        }

        val result = repository.translate(
            text = text,
            from = _sourceLanguage.value,
            to = _targetLanguage.value
        )
        _translationResult.value = result
        _isLoading.value = false

        // 检查当前翻译内容在数据库中是否已被收藏
        val history = repository.getHistory(text)
        val matched = history.firstOrNull { it.sourceText == text }
        _isCurrentResultFavorite.value = matched?.isFavorite ?: false

        loadHistory()
    }

    fun clearInput() {
        _inputText.value = ""
        _translationResult.value = null
        _isCurrentResultFavorite.value = false
        debounceJob?.cancel()
    }

    fun speak(text: String, language: Language, onError: ((String) -> Unit)? = null) {
        val resolvedLang = if (language == Language.AUTO) {
            Language.detectLanguage(text)
        } else {
            language
        }
        ttsManager.speak(text, resolvedLang, onError)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun toggleFavorite(record: TranslationRecord) {
        viewModelScope.launch {
            val newFav = !record.isFavorite
            repository.toggleFavorite(record.id, newFav)
            if (_translationResult.value?.originalText == record.sourceText) {
                _isCurrentResultFavorite.value = newFav
            }
            loadHistory()
            loadFavorites()
        }
    }

    fun toggleCurrentResultFavorite() {
        val res = _translationResult.value ?: return
        if (res.translatedText.isBlank()) return

        viewModelScope.launch {
            val records = repository.getHistory(res.originalText)
            val matched = records.firstOrNull { it.sourceText == res.originalText }
            if (matched != null) {
                val newFav = !matched.isFavorite
                repository.toggleFavorite(matched.id, newFav)
                _isCurrentResultFavorite.value = newFav
            } else {
                _isCurrentResultFavorite.value = !_isCurrentResultFavorite.value
            }
            loadHistory()
            loadFavorites()
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            repository.deleteRecord(id)
            loadHistory()
            loadFavorites()
        }
    }

    fun clearHistory(keepFavorites: Boolean = true) {
        viewModelScope.launch {
            repository.clearHistory(keepFavorites)
            loadHistory()
            loadFavorites()
        }
    }

    fun clearFavorites() {
        viewModelScope.launch {
            repository.clearFavorites()
            _isCurrentResultFavorite.value = false
            loadHistory()
            loadFavorites()
        }
    }

    fun loadHistory(keyword: String = "") {
        viewModelScope.launch {
            _historyList.value = repository.getHistory(keyword)
        }
    }

    fun loadFavorites(keyword: String = "") {
        viewModelScope.launch {
            _favoritesList.value = repository.getFavorites(keyword)
        }
    }

    fun selectHistoryItem(item: TranslationRecord) {
        _inputText.value = item.sourceText
        _sourceLanguage.value = Language.fromCode(item.sourceLang)
        _targetLanguage.value = Language.fromCode(item.targetLang)
        _isCurrentResultFavorite.value = item.isFavorite
        _translationResult.value = TranslationResult(
            originalText = item.sourceText,
            translatedText = item.translatedText,
            sourceLanguage = Language.fromCode(item.sourceLang),
            targetLanguage = Language.fromCode(item.targetLang),
            engineType = TranslationEngineType.fromId(item.engineType),
            isSuccess = true
        )
    }

    // 引擎管理与密钥保存
    fun setEngineType(type: TranslationEngineType) {
        _selectedEngine.value = type
        prefRepo.selectedEngineType = type
        _testResult.value = null
        triggerImmediateTranslate()
    }

    fun saveGoogleConfig(mode: String, proxyUrl: String) {
        prefRepo.googleMode = mode
        prefRepo.googleProxyUrl = proxyUrl
    }

    fun saveBaiduConfig(appId: String, secretKey: String) {
        prefRepo.baiduAppId = appId
        prefRepo.baiduSecretKey = secretKey
    }

    fun saveYoudaoConfig(appKey: String, appSecret: String) {
        prefRepo.youdaoAppKey = appKey
        prefRepo.youdaoAppSecret = appSecret
    }

    fun saveTencentConfig(secretId: String, secretKey: String) {
        prefRepo.tencentSecretId = secretId
        prefRepo.tencentSecretKey = secretKey
    }

    fun testConnection() {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.testCurrentEngine()
            _testResult.value = res
            _isLoading.value = false
        }
    }

    fun showFullscreen(text: String) {
        _fullscreenText.value = text
    }

    fun dismissFullscreen() {
        _fullscreenText.value = null
    }

    override fun onCleared() {
        ttsManager.stop()
        super.onCleared()
    }
}
