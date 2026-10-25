#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
单元测试：测试 Google 翻译代理与请求解析
"""

import unittest
from server import request_google_translate, normalize_lang

class TestGoogleProxy(unittest.TestCase):

    def test_normalize_lang(self):
        self.assertEqual(normalize_lang("zh"), "zh-CN")
        self.assertEqual(normalize_lang("zh-CN"), "zh-CN")
        self.assertEqual(normalize_lang("zh-tw"), "zh-TW")
        self.assertEqual(normalize_lang("en"), "en")
        self.assertEqual(normalize_lang("auto"), "auto")
        self.assertEqual(normalize_lang("jp"), "ja")
        self.assertEqual(normalize_lang("kor"), "ko")
        self.assertEqual(normalize_lang("fra"), "fr")
        self.assertEqual(normalize_lang("spa"), "es")

    def test_translate_english_to_chinese(self):
        result = request_google_translate(text="Good morning", sl="en", tl="zh-CN")
        self.assertIn("早上好", result["translatedText"])
        self.assertEqual(result["originalText"], "Good morning")
        self.assertEqual(result["sourceLanguage"], "en")
        self.assertEqual(result["targetLanguage"], "zh-CN")

    def test_translate_chinese_to_english(self):
        result = request_google_translate(text="你好，世界", sl="zh-CN", tl="en")
        self.assertIn("Hello", result["translatedText"])
        self.assertEqual(result["originalText"], "你好，世界")

    def test_multiline_translation(self):
        text = "Hello.\nThank you very much."
        result = request_google_translate(text=text, sl="auto", tl="zh-CN")
        self.assertTrue(len(result["translatedText"]) > 0)
        self.assertIn("你好", result["translatedText"])
        self.assertIn("感谢", result["translatedText"])

    def test_empty_query_http_error(self):
        import urllib.request
        import urllib.error
        import json
        try:
            urllib.request.urlopen("http://127.0.0.1:8787/api/translate")
            self.fail("Expected HTTP 400")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 400)
            data = json.loads(e.read().decode("utf-8"))
            self.assertFalse(data["success"])
            self.assertEqual(data["code"], 400)

    def test_tts_endpoint(self):
        import urllib.request
        url = "http://127.0.0.1:8787/api/tts?text=Good+morning&lang=en"
        with urllib.request.urlopen(url) as resp:
            self.assertEqual(resp.status, 200)
            self.assertEqual(resp.headers.get("Content-Type"), "audio/mpeg")
            data = resp.read()
            self.assertGreater(len(data), 1000)

if __name__ == "__main__":
    unittest.main()
