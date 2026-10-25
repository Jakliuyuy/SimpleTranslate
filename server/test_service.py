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

if __name__ == "__main__":
    unittest.main()
