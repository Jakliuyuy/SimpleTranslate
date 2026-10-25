package com.translate.app.data.model

import java.util.Locale

data class Language(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val ttsLocale: Locale?
) {
    /**
     * 转换为百度翻译 API 语言代码规范
     */
    fun toBaiduCode(): String = when (code) {
        AUTO.code -> "auto"
        ZH.code -> "zh"
        EN.code -> "en"
        JA.code -> "jp"
        KO.code -> "kor"
        FR.code -> "fra"
        ES.code -> "spa"
        RU.code -> "ru"
        DE.code -> "de"
        IT.code -> "it"
        PT.code -> "pt"
        VI.code -> "vie"
        TH.code -> "th"
        AR.code -> "ara"
        else -> code
    }

    /**
     * 转换为有道智云 API 语言代码规范
     */
    fun toYoudaoCode(): String = when (code) {
        AUTO.code -> "auto"
        ZH.code -> "zh-CHS"
        EN.code -> "en"
        JA.code -> "ja"
        KO.code -> "ko"
        FR.code -> "fr"
        ES.code -> "es"
        RU.code -> "ru"
        DE.code -> "de"
        IT.code -> "it"
        PT.code -> "pt"
        VI.code -> "vi"
        TH.code -> "th"
        AR.code -> "ar"
        else -> code
    }

    /**
     * 转换为腾讯云 TMT API 语言代码规范
     */
    fun toTencentCode(): String = when (code) {
        AUTO.code -> "auto"
        ZH.code -> "zh"
        EN.code -> "en"
        JA.code -> "ja"
        KO.code -> "ko"
        FR.code -> "fr"
        ES.code -> "es"
        RU.code -> "ru"
        DE.code -> "de"
        IT.code -> "it"
        PT.code -> "pt"
        VI.code -> "vi"
        TH.code -> "th"
        AR.code -> "ar"
        else -> code
    }

    companion object {
        val AUTO = Language("auto", "自动检测", "Auto Detect", null)
        val ZH = Language("zh", "中文 (简体)", "中文", Locale.SIMPLIFIED_CHINESE)
        val EN = Language("en", "英语", "English", Locale.US)
        val JA = Language("ja", "日语", "日本語", Locale.JAPAN)
        val KO = Language("ko", "韩语", "한국어", Locale.KOREA)
        val FR = Language("fr", "法语", "Français", Locale.FRANCE)
        val DE = Language("de", "德语", "Deutsch", Locale.GERMANY)
        val ES = Language("es", "西班牙语", "Español", Locale("es", "ES"))
        val RU = Language("ru", "俄语", "Русский", Locale("ru", "RU"))
        val IT = Language("it", "意大利语", "Italiano", Locale.ITALY)
        val PT = Language("pt", "葡萄牙语", "Português", Locale("pt", "PT"))
        val VI = Language("vi", "越南语", "Tiếng Việt", Locale("vi", "VN"))
        val TH = Language("th", "泰语", "ไทย", Locale("th", "TH"))
        val AR = Language("ar", "阿拉伯语", "العربية", Locale("ar"))

        val SUPPORTED_TARGET_LANGUAGES = listOf(
            ZH, EN, JA, KO, FR, DE, ES, RU, IT, PT, VI, TH, AR
        )

        val SUPPORTED_SOURCE_LANGUAGES = listOf(
            AUTO, ZH, EN, JA, KO, FR, DE, ES, RU, IT, PT, VI, TH, AR
        )

        fun fromCode(code: String): Language {
            return SUPPORTED_SOURCE_LANGUAGES.firstOrNull {
                it.code.equals(code, ignoreCase = true)
            } ?: EN
        }
    }
}
