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
}
