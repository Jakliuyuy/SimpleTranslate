package com.translate.app.data.model

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val engineType: TranslationEngineType,
    val phonetic: String? = null,
    val explains: List<String> = emptyList(),
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
