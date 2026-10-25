package com.translate.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.translate.app.data.api.ApiConfig
import com.translate.app.data.model.TranslationEngineType

class PreferenceRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("simple_translate_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENGINE_TYPE = "pref_engine_type"
        private const val KEY_BAIDU_APP_ID = "pref_baidu_app_id"
        private const val KEY_BAIDU_SECRET_KEY = "pref_baidu_secret_key"
        private const val KEY_YOUDAO_APP_KEY = "pref_youdao_app_key"
        private const val KEY_YOUDAO_APP_SECRET = "pref_youdao_app_secret"
        private const val KEY_TENCENT_SECRET_ID = "pref_tencent_secret_id"
        private const val KEY_TENCENT_SECRET_KEY = "pref_tencent_secret_key"
        private const val KEY_LAST_SOURCE_LANG = "pref_last_source_lang"
        private const val KEY_LAST_TARGET_LANG = "pref_last_target_lang"
    }

    var selectedEngineType: TranslationEngineType
        get() {
            val id = prefs.getString(KEY_ENGINE_TYPE, TranslationEngineType.BAIDU.id) ?: TranslationEngineType.BAIDU.id
            return TranslationEngineType.fromId(id)
        }
        set(value) {
            prefs.edit().putString(KEY_ENGINE_TYPE, value.id).apply()
        }

    // 百度配置（优先读取 SharedPreferences，为空则回退到 ApiConfig）
    var baiduAppId: String
        get() {
            val saved = prefs.getString(KEY_BAIDU_APP_ID, null)
            return if (!saved.isNullOrBlank()) saved else ApiConfig.DEFAULT_BAIDU_APP_ID
        }
        set(value) = prefs.edit().putString(KEY_BAIDU_APP_ID, value.trim()).apply()

    var baiduSecretKey: String
        get() {
            val saved = prefs.getString(KEY_BAIDU_SECRET_KEY, null)
            return if (!saved.isNullOrBlank()) saved else ApiConfig.DEFAULT_BAIDU_SECRET_KEY
        }
        set(value) = prefs.edit().putString(KEY_BAIDU_SECRET_KEY, value.trim()).apply()

    // 有道配置
    var youdaoAppKey: String
        get() {
            val saved = prefs.getString(KEY_YOUDAO_APP_KEY, null)
            return if (!saved.isNullOrBlank()) saved else ApiConfig.DEFAULT_YOUDAO_APP_KEY
        }
        set(value) = prefs.edit().putString(KEY_YOUDAO_APP_KEY, value.trim()).apply()

    var youdaoAppSecret: String
        get() {
            val saved = prefs.getString(KEY_YOUDAO_APP_SECRET, null)
            return if (!saved.isNullOrBlank()) saved else ApiConfig.DEFAULT_YOUDAO_APP_SECRET
        }
        set(value) = prefs.edit().putString(KEY_YOUDAO_APP_SECRET, value.trim()).apply()

    // 腾讯云配置
    var tencentSecretId: String
        get() {
            val saved = prefs.getString(KEY_TENCENT_SECRET_ID, null)
            return if (!saved.isNullOrBlank()) saved else ApiConfig.DEFAULT_TENCENT_SECRET_ID
        }
        set(value) = prefs.edit().putString(KEY_TENCENT_SECRET_ID, value.trim()).apply()

    var tencentSecretKey: String
        get() {
            val saved = prefs.getString(KEY_TENCENT_SECRET_KEY, null)
            return if (!saved.isNullOrBlank()) saved else ApiConfig.DEFAULT_TENCENT_SECRET_KEY
        }
        set(value) = prefs.edit().putString(KEY_TENCENT_SECRET_KEY, value.trim()).apply()

    // 语言偏好记忆
    var lastSourceLang: String
        get() = prefs.getString(KEY_LAST_SOURCE_LANG, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_LAST_SOURCE_LANG, value).apply()

    var lastTargetLang: String
        get() = prefs.getString(KEY_LAST_TARGET_LANG, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LAST_TARGET_LANG, value).apply()
}
