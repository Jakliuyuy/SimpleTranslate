package com.translate.app.data.api

import com.translate.app.data.api.engine.BaiduTranslationEngine
import com.translate.app.data.api.engine.GoogleTranslationEngine
import com.translate.app.data.api.engine.MockTranslationEngine
import com.translate.app.data.api.engine.TencentTranslationEngine
import com.translate.app.data.api.engine.YoudaoTranslationEngine
import com.translate.app.data.model.TranslationEngineType
import com.translate.app.data.repository.PreferenceRepository

class EngineFactory(private val preferenceRepository: PreferenceRepository) {

    private val mockEngine = MockTranslationEngine()

    private val googleEngine = GoogleTranslationEngine(
        getMode = { preferenceRepository.googleMode },
        getProxyUrl = { preferenceRepository.googleProxyUrl }
    )

    private val baiduEngine = BaiduTranslationEngine(
        getAppId = { preferenceRepository.baiduAppId },
        getSecretKey = { preferenceRepository.baiduSecretKey }
    )

    private val youdaoEngine = YoudaoTranslationEngine(
        getAppKey = { preferenceRepository.youdaoAppKey },
        getAppSecret = { preferenceRepository.youdaoAppSecret }
    )

    private val tencentEngine = TencentTranslationEngine(
        getSecretId = { preferenceRepository.tencentSecretId },
        getSecretKey = { preferenceRepository.tencentSecretKey }
    )

    fun getEngine(type: TranslationEngineType): ITranslationEngine {
        return when (type) {
            TranslationEngineType.GOOGLE -> googleEngine
            TranslationEngineType.BAIDU -> baiduEngine
            TranslationEngineType.YOUDAO -> youdaoEngine
            TranslationEngineType.TENCENT -> tencentEngine
            TranslationEngineType.MOCK -> mockEngine
        }
    }

    fun getCurrentEngine(): ITranslationEngine {
        return getEngine(preferenceRepository.selectedEngineType)
    }
}
