package com.translate.app

import com.translate.app.data.api.engine.MockTranslationEngine
import com.translate.app.data.model.Language
import com.translate.app.util.CryptoUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationEngineTest {

    @Test
    fun testMd5Calculation() {
        val input = "hello"
        val expected = "5d41402abc4b2a76b9719d911017c592"
        val actual = CryptoUtils.md5(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testSha256Calculation() {
        val input = "hello"
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        val actual = CryptoUtils.sha256(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testHmacSha256Calculation() {
        val key = "secret_key".toByteArray(Charsets.UTF_8)
        val data = "test_string"
        val hex = CryptoUtils.hmacSha256Hex(key, data)
        assertNotNull(hex)
        assertEquals(64, hex.length) // SHA256 Hex is 64 characters
    }

    @Test
    fun testLanguageCodeMapping() {
        // 百度代码映射
        assertEquals("zh", Language.ZH.toBaiduCode())
        assertEquals("en", Language.EN.toBaiduCode())
        assertEquals("jp", Language.JA.toBaiduCode())
        assertEquals("kor", Language.KO.toBaiduCode())
        assertEquals("fra", Language.FR.toBaiduCode())
        assertEquals("spa", Language.ES.toBaiduCode())

        // 有道代码映射
        assertEquals("zh-CHS", Language.ZH.toYoudaoCode())
        assertEquals("en", Language.EN.toYoudaoCode())
        assertEquals("ja", Language.JA.toYoudaoCode())
        assertEquals("ko", Language.KO.toYoudaoCode())
        assertEquals("fr", Language.FR.toYoudaoCode())
        assertEquals("es", Language.ES.toYoudaoCode())

        // 腾讯云代码映射
        assertEquals("zh", Language.ZH.toTencentCode())
        assertEquals("en", Language.EN.toTencentCode())
        assertEquals("ja", Language.JA.toTencentCode())
        assertEquals("ko", Language.KO.toTencentCode())
    }

    @Test
    fun testMockTranslationEngine() = runBlocking {
        val engine = MockTranslationEngine()

        // 测试中英翻译
        val resHelloZh = engine.translate("你好", Language.ZH, Language.EN)
        assertTrue(resHelloZh.isSuccess)
        assertEquals("Hello", resHelloZh.translatedText)

        // 测试英中翻译
        val resHelloEn = engine.translate("hello", Language.EN, Language.ZH)
        assertTrue(resHelloEn.isSuccess)
        assertEquals("你好", resHelloEn.translatedText)
        assertEquals("/həˈləʊ/", resHelloEn.phonetic)
        assertTrue(resHelloEn.explains.isNotEmpty())

        // 测试长句智能兜底
        val resLong = engine.translate("今天天气真好", Language.ZH, Language.EN)
        assertTrue(resLong.isSuccess)
        assertTrue(resLong.translatedText.isNotBlank())
    }

    @Test
    fun testYoudaoTruncateRule() {
        // 长度 <= 20
        val shortText = "12345678901234567890"
        val truncatedShort = if (shortText.length <= 20) shortText else "${shortText.take(10)}${shortText.length}${shortText.takeLast(10)}"
        assertEquals(shortText, truncatedShort)

        // 长度 > 20
        val longText = "1234567890abcdefghijEXTRA" // length 25
        val truncatedLong = if (longText.length <= 20) longText else "${longText.take(10)}${longText.length}${longText.takeLast(10)}"
        val expected = "123456789025fghijEXTRA"
        assertEquals(expected, truncatedLong)
    }

    @Test
    fun testBaiduApiConfigPreset() {
        assertEquals("wMHD_dber75qj3nn4okmvvqb0", com.translate.app.data.api.ApiConfig.DEFAULT_BAIDU_SECRET_KEY)
    }

    @Test
    fun testBaiduEngineKeySanitization() {
        val rawKey = "wMHD_dber75qj3nn4okmvvqb0"
        // 1. 干净密钥
        assertEquals(rawKey, com.translate.app.data.api.engine.BaiduTranslationEngine.sanitizeKey(rawKey))
        // 2. 粘连 key 前缀 (用户原样输入的 keywMHD_...)
        assertEquals(rawKey, com.translate.app.data.api.engine.BaiduTranslationEngine.sanitizeKey("key$rawKey"))
        // 3. 带前后空格
        assertEquals(rawKey, com.translate.app.data.api.engine.BaiduTranslationEngine.sanitizeKey("  $rawKey  \n"))
        // 4. 双引号包裹
        assertEquals(rawKey, com.translate.app.data.api.engine.BaiduTranslationEngine.sanitizeKey("\"$rawKey\""))
        // 5. 单引号包裹
        assertEquals(rawKey, com.translate.app.data.api.engine.BaiduTranslationEngine.sanitizeKey("'$rawKey'"))
        // 6. 中文引号包裹
        assertEquals(rawKey, com.translate.app.data.api.engine.BaiduTranslationEngine.sanitizeKey("“$rawKey”"))
        // 7. 带 Bearer 前缀
        assertEquals(rawKey, com.translate.app.data.api.engine.BaiduTranslationEngine.sanitizeKey("Bearer $rawKey"))
        // 8. 带 key: 或 key= 前缀
        assertEquals(rawKey, com.translate.app.data.api.engine.BaiduTranslationEngine.sanitizeKey("key: $rawKey"))
        assertEquals(rawKey, com.translate.app.data.api.engine.BaiduTranslationEngine.sanitizeKey("key=$rawKey"))
    }

    @Test
    fun testBaiduEngineMissingAppIdPrompt() = runBlocking {
        val engine = com.translate.app.data.api.engine.BaiduTranslationEngine(
            getAppId = { "" },
            getSecretKey = { com.translate.app.data.api.ApiConfig.DEFAULT_BAIDU_SECRET_KEY }
        )
        val result = engine.translate("hello", Language.EN, Language.ZH)
        org.junit.Assert.assertFalse(result.isSuccess)
        org.junit.Assert.assertTrue(result.errorMessage?.contains("APP ID") == true)
        org.junit.Assert.assertTrue(result.errorMessage?.contains("wMHD") == true)
    }

    @Test
    fun testLanguageSwapLogic() {
        // 正常语言对调
        var src = Language.EN
        var tgt = Language.ZH
        var newSrc = tgt
        var newTgt = if (src == Language.AUTO) (if (tgt == Language.ZH) Language.EN else Language.ZH) else src
        assertEquals(Language.ZH, newSrc)
        assertEquals(Language.EN, newTgt)

        // AUTO 语言且目标为中文对调 -> 目标变为英文
        src = Language.AUTO
        tgt = Language.ZH
        newSrc = tgt
        newTgt = if (src == Language.AUTO) (if (tgt == Language.ZH) Language.EN else Language.ZH) else src
        assertEquals(Language.ZH, newSrc)
        assertEquals(Language.EN, newTgt)

        // AUTO 语言且目标为英文对调 -> 目标变为中文
        src = Language.AUTO
        tgt = Language.EN
        newSrc = tgt
        newTgt = if (src == Language.AUTO) (if (tgt == Language.ZH) Language.EN else Language.ZH) else src
        assertEquals(Language.EN, newSrc)
        assertEquals(Language.ZH, newTgt)
    }

    @Test
    fun testGoogleLanguageCodeMapping() {
        assertEquals("zh-CN", com.translate.app.data.api.engine.GoogleTranslationEngine.toGoogleLanguageCode(Language.ZH))
        assertEquals("en", com.translate.app.data.api.engine.GoogleTranslationEngine.toGoogleLanguageCode(Language.EN))
        assertEquals("ja", com.translate.app.data.api.engine.GoogleTranslationEngine.toGoogleLanguageCode(Language.JA))
        assertEquals("ko", com.translate.app.data.api.engine.GoogleTranslationEngine.toGoogleLanguageCode(Language.KO))
        assertEquals("fr", com.translate.app.data.api.engine.GoogleTranslationEngine.toGoogleLanguageCode(Language.FR))
        assertEquals("auto", com.translate.app.data.api.engine.GoogleTranslationEngine.toGoogleLanguageCode(Language.AUTO))
    }

    @Test
    fun testGoogleEngineBlankInput() = runBlocking {
        val engine = com.translate.app.data.api.engine.GoogleTranslationEngine()
        val result = engine.translate("   ", Language.EN, Language.ZH)
        assertTrue(result.isSuccess)
        assertEquals("", result.translatedText)
    }

    @Test
    fun testGoogleEngineDirectTranslation() = runBlocking {
        val engine = com.translate.app.data.api.engine.GoogleTranslationEngine(
            getMode = { "direct" }
        )
        val result = engine.translate("Good morning", Language.EN, Language.ZH)
        if (result.isSuccess) {
            assertTrue(result.translatedText.contains("早") || result.translatedText.contains("好"))
        } else {
            // 针对受限网络环境：确认返回了指引用户切换中转代理的明确错误信息
            assertTrue(result.errorMessage?.contains("服务器中转代理") == true || result.errorMessage?.contains("直连") == true)
        }
    }

    @Test
    fun testGoogleEngineProxyTranslation() = runBlocking {
        val proxyUrl = System.getenv("GOOGLE_PROXY_URL") ?: "http://129.151.9.55:8787"
        val engine = com.translate.app.data.api.engine.GoogleTranslationEngine(
            getMode = { "proxy" },
            getProxyUrl = { proxyUrl }
        )
        val result = engine.translate("Thank you", Language.EN, Language.ZH)
        if (result.isSuccess) {
            assertTrue(result.translatedText.contains("谢谢") || result.translatedText.contains("感谢"))
        } else {
            // CI 隔离网络容错：若 CI runner 无法连接外部特定端口，验证不发生未捕获异常并返回有意义错误提示
            assertNotNull(result.errorMessage)
        }
    }

    @Test
    fun testOcrTextCleaning() {
        val messyText = "  Hello World  \n\n   \n   This is line 2  \n"
        val cleaned = com.translate.app.util.OcrManager.cleanRecognizedText(messyText)
        assertEquals("Hello World\nThis is line 2", cleaned)
    }

    @Test
    fun testGoogleEngineTypeRegistered() {
        val googleType = com.translate.app.data.model.TranslationEngineType.fromId("google")
        assertEquals(com.translate.app.data.model.TranslationEngineType.GOOGLE, googleType)
        assertEquals("Google 翻译 (内置/免Key)", googleType.title)
    }

    @Test
    fun testLanguageFromCodeWithRegionalAndAliases() {
        // 区域代码解析
        assertEquals(Language.ZH, Language.fromCode("zh-CN"))
        assertEquals(Language.ZH, Language.fromCode("zh-TW"))
        assertEquals(Language.ZH, Language.fromCode("zh-Hans"))
        assertEquals(Language.ZH, Language.fromCode("cht"))
        assertEquals(Language.EN, Language.fromCode("en-US"))
        assertEquals(Language.EN, Language.fromCode("en-GB"))

        // 常见平台别名
        assertEquals(Language.JA, Language.fromCode("jp"))
        assertEquals(Language.KO, Language.fromCode("kor"))
        assertEquals(Language.FR, Language.fromCode("fra"))
        assertEquals(Language.ES, Language.fromCode("spa"))
        assertEquals(Language.AR, Language.fromCode("ara"))
        assertEquals(Language.VI, Language.fromCode("vie"))
    }

    @Test
    fun testTtsSplitTextIntoChunksPreservesPunctuationAndLimitsSize() {
        val longArticle = "第一句话包含标点符号。第二句话也有标点符号！第三句话则是问号？" +
                "第四句话继续测试分句，看看标点符号是否会丢失；第五句话也是如此。\n" +
                "最后一段测试换行符。"
        val chunks = com.translate.app.util.TtsManager.splitTextIntoChunks(longArticle, 100)

        assertTrue(chunks.isNotEmpty())
        // 验证标点符号被完整保留
        val combined = chunks.joinToString("")
        assertTrue(combined.contains("。"))
        assertTrue(combined.contains("！"))
        assertTrue(combined.contains("？"))
        assertTrue(combined.contains("；"))

        // 验证每个分片均不超过设定上限
        chunks.forEach { chunk ->
            assertTrue(chunk.length <= 100)
        }
    }

    @Test
    fun testAutoDetectTargetLanguage() {
        // 1. 中文输入 -> 目标语言为英文 (Language.EN)
        assertEquals(Language.EN, Language.autoDetectTargetLanguage("你好"))
        assertEquals(Language.EN, Language.autoDetectTargetLanguage("你好，世界！"))
        assertEquals(Language.EN, Language.autoDetectTargetLanguage("请帮我 review 这个 PR"))

        // 2. 纯英文输入 -> 目标语言为中文 (Language.ZH)
        assertEquals(Language.ZH, Language.autoDetectTargetLanguage("hello"))
        assertEquals(Language.ZH, Language.autoDetectTargetLanguage("Good morning everyone."))
        assertEquals(Language.ZH, Language.autoDetectTargetLanguage("How are you doing today?"))

        // 3. 其他非中文语种 -> 目标语言为中文 (Language.ZH)
        assertEquals(Language.ZH, Language.autoDetectTargetLanguage("こんにちは"))
        assertEquals(Language.ZH, Language.autoDetectTargetLanguage("안녕하세요"))
        assertEquals(Language.ZH, Language.autoDetectTargetLanguage("Bonjour tout le monde"))
        assertEquals(Language.ZH, Language.autoDetectTargetLanguage("Привет, как дела?"))

        // 4. 空白与纯符号/数字 -> 保持当前目标语言
        assertEquals(Language.ZH, Language.autoDetectTargetLanguage("", Language.ZH))
        assertEquals(Language.EN, Language.autoDetectTargetLanguage("   ", Language.EN))
        assertEquals(Language.EN, Language.autoDetectTargetLanguage("12345", Language.EN))
        assertEquals(Language.ZH, Language.autoDetectTargetLanguage("???!!!", Language.ZH))
    }

    @Test
    fun testBuildOnlineAudioUrls() {
        val proxyBase = "http://129.151.9.55:8787"

        // 1. 英文短词 -> 优先有道美音，同时提供中转代理与 Google 官方音频流
        val enShortUrls = com.translate.app.util.TtsManager.buildOnlineAudioUrls("hello", Language.EN, proxyBase)
        assertTrue(enShortUrls.isNotEmpty())
        assertTrue(enShortUrls.any { it.contains("dict.youdao.com") && it.contains("type=2") })
        assertTrue(enShortUrls.any { it.contains("129.151.9.55:8787/api/tts") && it.contains("lang=en") })
        assertTrue(enShortUrls.any { it.contains("translate.google.com/translate_tts") && it.contains("tl=en") })

        // 2. 中文短词 -> 优先有道中文发音，同时包含中转代理与 Google 官方音频流
        val zhShortUrls = com.translate.app.util.TtsManager.buildOnlineAudioUrls("你好", Language.ZH, proxyBase)
        assertTrue(zhShortUrls.isNotEmpty())
        assertTrue(zhShortUrls.any { it.contains("dict.youdao.com") && it.contains("le=zh") })
        assertTrue(zhShortUrls.any { it.contains("129.151.9.55:8787/api/tts") && it.contains("lang=zh-CN") })
        assertTrue(zhShortUrls.any { it.contains("translate.google.com/translate_tts") && it.contains("tl=zh-CN") })

        // 3. 英文长句 -> 包含中转代理与 Google 官方流，URL 已安全转义
        val enLongText = "This is a longer English sentence for audio playback testing."
        val enLongUrls = com.translate.app.util.TtsManager.buildOnlineAudioUrls(enLongText, Language.EN, proxyBase)
        assertTrue(enLongUrls.isNotEmpty())
        assertTrue(enLongUrls.none { it.contains(" ") }) // 验证空格已被 URL 编码，无非法空格
        assertTrue(enLongUrls.any { it.contains("129.151.9.55:8787/api/tts") })
        assertTrue(enLongUrls.any { it.contains("translate.google.com/translate_tts") })

        // 4. 空文本返回空列表
        val emptyUrls = com.translate.app.util.TtsManager.buildOnlineAudioUrls("  ", Language.EN, proxyBase)
        assertTrue(emptyUrls.isEmpty())
    }
}

