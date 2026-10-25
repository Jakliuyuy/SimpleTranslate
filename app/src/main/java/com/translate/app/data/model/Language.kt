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

        /**
         * 判定文本中是否包含汉字 (覆盖 CJK 统一表意文字基本区、扩展区及增补汉字)
         */
        fun containsChinese(text: String): Boolean {
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                if (codePoint in 0x4E00..0x9FFF ||
                    codePoint in 0x3400..0x4DBF ||
                    codePoint in 0x20000..0x2CEAF ||
                    (try { Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN } catch (_: Exception) { false })
                ) {
                    return true
                }
                i += Character.charCount(codePoint)
            }
            return false
        }

        /**
         * 根据文本特征推断其实际所属语种（支持中、英、日、韩、俄、阿、泰等），用于 AUTO 模式下的发音与语言解析
         */
        fun detectLanguage(text: String): Language {
            val clean = text.trim()
            if (clean.isEmpty()) return EN

            // 1. 日文平假名与片假名优先判定（日文中常包含汉字，若包含假名则必然为日文）
            if (clean.any { it.code in 0x3040..0x30FF || it.code in 0x31F0..0x31FF }) {
                return JA
            }

            // 2. 韩文
            if (clean.any { it.code in 0xAC00..0xD7AF || it.code in 0x1100..0x11FF || it.code in 0x3130..0x318F }) {
                return KO
            }

            // 3. 中文汉字
            if (containsChinese(clean)) {
                return ZH
            }

            // 4. 泰文
            if (clean.any { it.code in 0x0E00..0x0E7F }) {
                return TH
            }

            // 5. 阿拉伯文
            if (clean.any { it.code in 0x0600..0x06FF }) {
                return AR
            }

            // 6. 俄文 (西里尔字母)
            if (clean.any { it.code in 0x0400..0x04FF }) {
                return RU
            }

            // 7. 拉丁字母/法文/德文/西班牙语等外语，默认归属英文体系朗读
            val hasLatin = clean.any { Character.isLetter(it) }
            if (hasLatin) {
                return EN
            }

            return EN
        }

        /**
         * 根据输入文本特征，为自动检测 (AUTO) 模式推导合适的目标语言：
         * 1. 包含汉字 -> 输入为中文，目标语言动态对齐为英文 (Language.EN)
         * 2. 不含汉字且包含字母/外语字符 -> 输入为英文或其他语种，目标语言动态对齐为中文 (Language.ZH)
         * 3. 纯空白、数字或符号 -> 保持当前目标语言
         */
        fun autoDetectTargetLanguage(text: String, currentTarget: Language = ZH): Language {
            val clean = text.trim()
            if (clean.isEmpty()) return currentTarget
            if (containsChinese(clean)) {
                return EN
            }
            val hasLetterOrForeign = clean.any { Character.isLetter(it) }
            if (hasLetterOrForeign) {
                return ZH
            }
            return if (currentTarget == AUTO) ZH else currentTarget
        }
    }
}
