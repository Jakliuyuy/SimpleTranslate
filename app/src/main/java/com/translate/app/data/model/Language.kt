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
         * 判定字符是否为汉字 CodePoint (覆盖 CJK 统一表意文字基本区、扩展区及增补汉字)
         */
        fun isHanCodePoint(codePoint: Int): Boolean {
            return codePoint in 0x4E00..0x9FFF ||
                   codePoint in 0x3400..0x4DBF ||
                   codePoint in 0x20000..0x2CEAF ||
                   (try { Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN } catch (_: Exception) { false })
        }

        /**
         * 判定字符是否为拉丁字母 (覆盖标准英文字母 A-Z, a-z 及法、德、西等扩展拉丁字母)
         */
        fun isLatinCodePoint(codePoint: Int): Boolean {
            return (codePoint in 0x0041..0x005A) ||
                   (codePoint in 0x0061..0x007A) ||
                   (codePoint in 0x00C0..0x024F) ||
                   (try { Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN } catch (_: Exception) { false })
        }

        /**
         * 语种核心字符频次统计数据模型
         */
        data class LanguageCharStats(
            val chineseCount: Int = 0,
            val latinCount: Int = 0,
            val kanaCount: Int = 0,
            val hangulCount: Int = 0,
            val cyrillicCount: Int = 0,
            val arabicCount: Int = 0,
            val thaiCount: Int = 0,
            val otherLetterCount: Int = 0
        ) {
            val totalLetterOrCharCount: Int
                get() = chineseCount + latinCount + kanaCount + hangulCount + cyrillicCount + arabicCount + thaiCount + otherLetterCount
        }

        /**
         * 统计文本中各语种核心字符的频次分布
         */
        fun analyzeCharStats(text: String): LanguageCharStats {
            var chinese = 0
            var latin = 0
            var kana = 0
            var hangul = 0
            var cyrillic = 0
            var arabic = 0
            var thai = 0
            var other = 0

            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                when {
                    // 1. 日文假名 (平假名 & 片假名 & 假名扩展)
                    cp in 0x3040..0x309F || cp in 0x30A0..0x30FF || cp in 0x31F0..0x31FF -> kana++
                    // 2. 韩文 (Hangul)
                    cp in 0xAC00..0xD7AF || cp in 0x1100..0x11FF || cp in 0x3130..0x318F -> hangul++
                    // 3. 中文汉字
                    isHanCodePoint(cp) -> chinese++
                    // 4. 西里尔字母 (俄文)
                    cp in 0x0400..0x04FF -> cyrillic++
                    // 5. 阿拉伯文
                    cp in 0x0600..0x06FF -> arabic++
                    // 6. 泰文
                    cp in 0x0E00..0x0E7F -> thai++
                    // 7. 拉丁字母 (英文及拉丁语系)
                    isLatinCodePoint(cp) -> latin++
                    // 8. 其他字母 (如希腊字母等)
                    Character.isLetter(cp) -> other++
                }
                i += Character.charCount(cp)
            }

            return LanguageCharStats(
                chineseCount = chinese,
                latinCount = latin,
                kanaCount = kana,
                hangulCount = hangul,
                cyrillicCount = cyrillic,
                arabicCount = arabic,
                thaiCount = thai,
                otherLetterCount = other
            )
        }

        /**
         * 判定文本中是否包含汉字
         */
        fun containsChinese(text: String): Boolean {
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                if (isHanCodePoint(codePoint)) {
                    return true
                }
                i += Character.charCount(codePoint)
            }
            return false
        }

        /**
         * 主体字符频次与权重占比算法（Dominant Language Detection）
         * 彻底告别单字符命中导致的二值误判，精准识别文本/图片的核心主导语种。
         */
        fun detectLanguage(text: String): Language {
            val clean = text.trim()
            if (clean.isEmpty()) return EN

            val stats = analyzeCharStats(clean)
            if (stats.totalLetterOrCharCount == 0) return EN

            // 1. 日文假名判定：
            // 日语中平假名与片假名为日文独有（中文绝对不含假名）。
            // 当假名存在且占汉字与假名总数的 15% 以上，或假名数 >= 3，或纯假名时，精准判定为日文
            val totalCJK = stats.kanaCount + stats.chineseCount
            val kanaRatio = if (totalCJK > 0) stats.kanaCount.toFloat() / totalCJK else 0f
            if (stats.kanaCount > 0 && (kanaRatio >= 0.15f || stats.kanaCount >= 3 || stats.chineseCount == 0)) {
                return JA
            }

            // 2. 韩文 (Hangul) 判定：
            // 韩文谚文为韩语特有。当存在韩文且未被海量汉字稀释时判定为韩文
            val totalHangulCJK = stats.hangulCount + stats.chineseCount
            val hangulRatio = if (totalHangulCJK > 0) stats.hangulCount.toFloat() / totalHangulCJK else 0f
            if (stats.hangulCount > 0 && (hangulRatio >= 0.15f || stats.hangulCount >= 3 || stats.chineseCount == 0)) {
                return KO
            }

            // 3. 俄文 (西里尔字母)
            if (stats.cyrillicCount > 0 && stats.cyrillicCount >= stats.latinCount && stats.cyrillicCount >= stats.chineseCount) {
                return RU
            }

            // 4. 阿拉伯文
            if (stats.arabicCount > 0 && stats.arabicCount >= stats.latinCount && stats.arabicCount >= stats.chineseCount) {
                return AR
            }

            // 5. 泰文
            if (stats.thaiCount > 0 && stats.thaiCount >= stats.latinCount && stats.thaiCount >= stats.chineseCount) {
                return TH
            }

            // 6. 中文与英文/拉丁语系主体比较 (Dominant Language Detection)
            // 汉字表意词素加权系数为 2.8，英文字母加权系数为 1.0
            val weightedChinese = stats.chineseCount * 2.8f
            val weightedLatin = stats.latinCount * 1.0f

            if (weightedChinese > weightedLatin) {
                return ZH
            }

            if (stats.latinCount > 0) {
                return EN
            }

            if (stats.chineseCount > 0) {
                return ZH
            }

            if (stats.otherLetterCount > 0) {
                return EN
            }

            return EN
        }

        /**
         * 根据主体语言判定结果为自动检测 (AUTO) 模式推导合适的目标语言：
         * 1. 中文主体 (chineseCount > latinCount 加权占优) -> 目标语言对齐为英文 (Language.EN)，中译英
         * 2. 英文主体 (latinCount >= chineseCount 加权占优) -> 目标语言对齐为中文 (Language.ZH)，英译中
         * 3. 日文、韩文、俄文、泰文、阿拉伯文等其他外语 -> 目标语言对齐为中文 (Language.ZH)
         * 4. 纯空白、纯数字或纯符号 -> 保持当前目标语言
         */
        fun autoDetectTargetLanguage(text: String, currentTarget: Language = ZH): Language {
            val clean = text.trim()
            if (clean.isEmpty()) return currentTarget

            val stats = analyzeCharStats(clean)
            if (stats.totalLetterOrCharCount == 0) {
                return if (currentTarget == AUTO) ZH else currentTarget
            }

            val dominant = detectLanguage(clean)
            return when (dominant) {
                ZH -> EN
                EN -> ZH
                JA -> ZH
                KO -> ZH
                RU -> ZH
                AR -> ZH
                TH -> ZH
                else -> ZH
            }
        }
    }
}
