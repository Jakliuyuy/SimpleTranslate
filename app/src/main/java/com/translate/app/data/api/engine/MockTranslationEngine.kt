package com.translate.app.data.api.engine

import com.translate.app.data.api.ITranslationEngine
import com.translate.app.data.model.Language
import com.translate.app.data.model.TranslationEngineType
import com.translate.app.data.model.TranslationResult
import kotlinx.coroutines.delay

class MockTranslationEngine : ITranslationEngine {

    override val engineType: TranslationEngineType = TranslationEngineType.MOCK

    // 常用短语与词库
    private val dictionary = mapOf(
        "你好" to mapOf(
            "en" to "Hello",
            "ja" to "こんにちは",
            "ko" to "안녕하세요",
            "fr" to "Bonjour",
            "de" to "Hallo",
            "es" to "Hola",
            "ru" to "Здравствуйте"
        ),
        "谢谢" to mapOf(
            "en" to "Thank you",
            "ja" to "ありがとうございます",
            "ko" to "감사합니다",
            "fr" to "Merci",
            "de" to "Danke",
            "es" to "Gracias",
            "ru" to "Спасибо"
        ),
        "再见" to mapOf(
            "en" to "Goodbye",
            "ja" to "さようなら",
            "ko" to "안녕히 가세요",
            "fr" to "Au revoir",
            "de" to "Auf Wiedersehen",
            "es" to "Adiós",
            "ru" to "До свидания"
        ),
        "早上好" to mapOf(
            "en" to "Good morning",
            "ja" to "おはようございます",
            "ko" to "좋은 아침입니다",
            "fr" to "Bonjour",
            "de" to "Guten Morgen",
            "es" to "Buenos días",
            "ru" to "Доброе утро"
        ),
        "晚安" to mapOf(
            "en" to "Good night",
            "ja" to "おやすみなさい",
            "ko" to "안녕히 주무세요",
            "fr" to "Bonne nuit",
            "de" to "Gute Nacht",
            "es" to "Buenas noches",
            "ru" to "Спокойной ночи"
        ),
        "对不起" to mapOf(
            "en" to "Sorry",
            "ja" to "すみません",
            "ko" to "죄송합니다",
            "fr" to "Pardon",
            "de" to "Entschuldigung",
            "es" to "Lo siento",
            "ru" to "Извините"
        ),
        "翻译" to mapOf(
            "en" to "Translate",
            "ja" to "翻訳",
            "ko" to "번역",
            "fr" to "Traduire",
            "de" to "Übersetzen",
            "es" to "Traducir",
            "ru" to "Переводить"
        ),
        "世界" to mapOf(
            "en" to "World",
            "ja" to "世界",
            "ko" to "세계",
            "fr" to "Monde",
            "de" to "Welt",
            "es" to "Mundo",
            "ru" to "Мир"
        ),
        "中国" to mapOf(
            "en" to "China",
            "ja" to "中国",
            "ko" to "중국",
            "fr" to "Chine",
            "de" to "China",
            "es" to "China",
            "ru" to "Китай"
        ),
        "朋友" to mapOf(
            "en" to "Friend",
            "ja" to "友達",
            "ko" to "친구",
            "fr" to "Ami",
            "de" to "Freund",
            "es" to "Amigo",
            "ru" to "Друг"
        )
    )

    // 反向词典（英文到中文等）
    private val englishToChinese = mapOf(
        "hello" to "你好",
        "hi" to "嗨 / 你好",
        "thank you" to "谢谢你",
        "thanks" to "谢谢",
        "goodbye" to "再见",
        "bye" to "再见",
        "good morning" to "早上好",
        "good night" to "晚安",
        "sorry" to "对不起 / 抱歉",
        "translate" to "翻译",
        "world" to "世界",
        "china" to "中国",
        "friend" to "朋友",
        "apple" to "苹果",
        "book" to "书本 / 预订",
        "love" to "爱 / 喜爱",
        "peace" to "和平",
        "welcome" to "欢迎"
    )

    // 单词发音与释义词典
    private val wordDetails = mapOf(
        "translate" to Pair(
            "/trænzˈleɪt/",
            listOf("v. 翻译；解释；转化", "n. 翻译结果")
        ),
        "hello" to Pair(
            "/həˈləʊ/",
            listOf("int. 喂；你好", "n. 招呼；问候")
        ),
        "world" to Pair(
            "/wɜːld/",
            listOf("n. 世界；地球；世人；领域")
        ),
        "friend" to Pair(
            "/frend/",
            listOf("n. 朋友；支持者；同盟者", "v. 与…做朋友")
        ),
        "翻译" to Pair(
            "fān yì",
            listOf("v. 把一种语言文字的意思表达为另一种语言文字", "n. 翻译者；译本")
        ),
        "你好" to Pair(
            "nǐ hǎo",
            listOf("polite. 打招呼的问候语")
        )
    )

    override suspend fun translate(
        text: String,
        from: Language,
        to: Language
    ): TranslationResult {
        // 模拟短网络延迟，使交互体验更真实
        delay(200)

        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        // 1. 精确匹配
        var translated = ""
        val targetCode = if (to == Language.AUTO) "zh" else to.code

        if (dictionary.containsKey(trimmed)) {
            val transMap = dictionary[trimmed]
            translated = transMap?.get(targetCode) ?: transMap?.get("en") ?: trimmed
        } else if (englishToChinese.containsKey(lower)) {
            translated = englishToChinese[lower] ?: trimmed
        } else {
            // 2. 模拟智能语义翻译
            translated = if (targetCode == "zh") {
                // 目标是中文
                "【模拟译文】$trimmed"
            } else if (targetCode == "en") {
                // 目标是英文
                "【Demo Translation】$trimmed"
            } else {
                "[$targetCode] $trimmed"
            }
        }

        // 查找是否有音标与详细词典释义
        val details = wordDetails[lower] ?: wordDetails[trimmed]

        return TranslationResult(
            originalText = text,
            translatedText = translated,
            sourceLanguage = from,
            targetLanguage = to,
            engineType = engineType,
            phonetic = details?.first,
            explains = details?.second ?: emptyList(),
            isSuccess = true
        )
    }

    override suspend fun testConnection(): Pair<Boolean, String> {
        return Pair(true, "内置演示引擎已就绪，无需配置密钥即可直接体验！")
    }
}
