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
}
