package com.translate.app.data.repository

import com.translate.app.data.api.EngineFactory
import com.translate.app.data.db.TranslationDbHelper
import com.translate.app.data.db.TranslationRecord
import com.translate.app.data.model.Language
import com.translate.app.data.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranslationRepository(
    private val engineFactory: EngineFactory,
    private val dbHelper: TranslationDbHelper,
    val preferenceRepository: PreferenceRepository
) {

    suspend fun translate(
        text: String,
        from: Language,
        to: Language,
        saveToHistory: Boolean = true
    ): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult(
                originalText = "",
                translatedText = "",
                sourceLanguage = from,
                targetLanguage = to,
                engineType = preferenceRepository.selectedEngineType,
                isSuccess = true
            )
        }

        val engine = engineFactory.getCurrentEngine()
        val result = engine.translate(trimmed, from, to)

        if (result.isSuccess && result.translatedText.isNotBlank() && saveToHistory) {
            val record = TranslationRecord(
                sourceText = trimmed,
                translatedText = result.translatedText,
                sourceLang = from.code,
                targetLang = to.code,
                engineType = engine.engineType.id,
                isFavorite = false
            )
            dbHelper.insertOrUpdate(record)
        }

        result
    }

    suspend fun getHistory(keyword: String = ""): List<TranslationRecord> = withContext(Dispatchers.IO) {
        dbHelper.queryRecords(onlyFavorites = false, keyword = keyword)
    }

    suspend fun getFavorites(keyword: String = ""): List<TranslationRecord> = withContext(Dispatchers.IO) {
        dbHelper.queryRecords(onlyFavorites = true, keyword = keyword)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dbHelper.toggleFavorite(id, isFavorite)
    }

    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.deleteRecord(id)
    }

    suspend fun clearHistory(keepFavorites: Boolean = true) = withContext(Dispatchers.IO) {
        dbHelper.clearAll(keepFavorites)
    }

    suspend fun testCurrentEngine(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        engineFactory.getCurrentEngine().testConnection()
    }
}
