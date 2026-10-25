package com.translate.app.data.api

import com.translate.app.data.model.Language
import com.translate.app.data.model.TranslationEngineType
import com.translate.app.data.model.TranslationResult

interface ITranslationEngine {
    val engineType: TranslationEngineType

    /**
     * 执行翻译
     * @param text 待翻译文本
     * @param from 源语言
     * @param to 目标语言
     * @return 翻译结果结构体
     */
    suspend fun translate(
        text: String,
        from: Language,
        to: Language
    ): TranslationResult

    /**
     * 测试当前配置与网络连通性
     */
    suspend fun testConnection(): Pair<Boolean, String>
}
