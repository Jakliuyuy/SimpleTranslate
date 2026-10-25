package com.translate.app.data.db

data class TranslationRecord(
    val id: Long = 0,
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val engineType: String,
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
