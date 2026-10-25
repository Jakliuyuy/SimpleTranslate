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
            if (code.isBlank()) return EN
            val clean = code.trim().lowercase()

            // 1. 直接精确匹配
            SUPPORTED_SOURCE_LANGUAGES.firstOrNull { it.code.equals(clean, ignoreCase = true) }?.let { return it }

            // 2. 常见区域代码与平台 API 别名映射
            val alias = when (clean) {
                "zh-cn", "zh-hans", "zh-sg", "zh-cmn", "zh-cmn-hans" -> "zh"
                "zh-tw", "zh-hk", "zh-mo", "zh-hant", "cht" -> "zh"
                "en-us", "en-gb", "en-au", "en-ca" -> "en"
                "ja-jp", "jp" -> "ja"
                "ko-kr", "kor" -> "ko"
                "fr-fr", "fra" -> "fr"
                "de-de" -> "de"
                "es-es", "spa" -> "es"
                "ru-ru" -> "ru"
                "it-it" -> "it"
                "pt-pt", "pt-br" -> "pt"
                "vi-vn", "vie" -> "vi"
                "th-th" -> "th"
                "ar-sa", "ar-ae", "ara" -> "ar"
                else -> null
            }
            if (alias != null) {
                SUPPORTED_SOURCE_LANGUAGES.firstOrNull { it.code.equals(alias, ignoreCase = true) }?.let { return it }
            }

            // 3. 按语言前缀提取 (如 "es-419" -> "es", "en_US" -> "en")
            val prefix = clean.split('-', '_')[0]
            SUPPORTED_SOURCE_LANGUAGES.firstOrNull { it.code.equals(prefix, ignoreCase = true) }?.let { return it }

            // 4. 包含 "zh" 判定为中文，否则保底英文
            return if (clean.contains("zh")) ZH else EN
        }
    }
}
