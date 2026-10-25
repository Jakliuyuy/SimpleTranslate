#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
SimpleTranslate Google Translation Proxy Server
===============================================
轻量级高性能 Google 翻译中转服务端。
部署于具有良好国际网络的服务器（如美国服务器 129.151.9.55），
为国内及移动端客户端提供免 Key、稳定极速的 Google 翻译中转代理。

特点：
- 零第三方依赖：纯 Python 3 标准库（http.server + urllib），开箱即用
- 高并发支持：基于 ThreadingHTTPServer 多线程并发处理
- 自动容错重试：支持超时重试与健全的异常恢复
- CORS 跨域支持：方便 Web 及各种客户端集成
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

GOOGLE_TRANSLATE_API = "https://translate.googleapis.com/translate_a/single"
DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/131.0.0.0 Safari/537.36"
)

# 语种映射容错
LANG_CODE_MAP = {
    "zh": "zh-CN",
    "zh-cn": "zh-CN",
    "zh-hans": "zh-CN",
    "zh-tw": "zh-TW",
    "zh-hant": "zh-TW",
    "cht": "zh-TW",
}

def normalize_lang(code: str) -> str:
    if not code:
        return "auto"
    code = code.strip()
    return LANG_CODE_MAP.get(code.lower(), code)

def request_google_translate(text: str, sl: str, tl: str, timeout: float = 10.0) -> dict:
    sl = normalize_lang(sl)
    tl = normalize_lang(tl)

    query_params = {
        "client": "gtx",
        "sl": sl,
        "tl": tl,
        "dt": "t"
    }
    url = f"{GOOGLE_TRANSLATE_API}?{urllib.parse.urlencode(query_params)}"
    post_data = urllib.parse.urlencode({"q": text}).encode("utf-8")

    req = urllib.request.Request(
        url,
        data=post_data,
        headers={
            "User-Agent": DEFAULT_USER_AGENT,
            "Content-Type": "application/x-www-form-urlencoded;charset=utf-8"
        }
    )

    last_error = None
    for attempt in range(2):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as response:
                content = response.read().decode("utf-8")
                data = json.loads(content)

                # 解析 Google 返回结构: [[["translated", "source", ...], ...], null, "en", ...]
                sentences = data[0] if (data and len(data) > 0 and isinstance(data[0], list)) else []
                translated_parts = []
                for s in sentences:
                    if s and len(s) > 0 and s[0]:
                        translated_parts.append(str(s[0]))
                translated_text = "".join(translated_parts)

                detected_source = sl
                if len(data) > 2 and isinstance(data[2], str) and data[2]:
                    detected_source = data[2]

                return {
                    "originalText": text,
                    "translatedText": translated_text,
                    "detectedSource": detected_source,
                    "sourceLanguage": sl,
                    "targetLanguage": tl
                }
        except Exception as e:
            last_error = e
            time.sleep(0.3)

    raise RuntimeError(f"Google 翻译接口请求失败: {last_error}")

class ProxyHandler(BaseHTTPRequestHandler):

    def send_json(self, status_code: int, data: dict):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(HTTPStatus.NO_CONTENT)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip("/")

        if path in ("", "/health"):
            self.send_json(200, {
                "status": "ok",
                "service": "SimpleTranslate-GoogleProxy",
                "version": "1.0.0",
                "timestamp": int(time.time()),
                "message": "Google Translation Proxy is running smoothly."
            })
            return

        if path == "/api/translate":
            params = urllib.parse.parse_qs(parsed.query)
            text = (params.get("text") or params.get("q") or [""])[0]
            sl = (params.get("from") or params.get("sl") or ["auto"])[0]
            tl = (params.get("to") or params.get("tl") or ["zh-CN"])[0]

            if not text.strip():
                self.send_json(400, {
                    "code": 400,
                    "success": false,
                    "error": "缺少必填参数 text 或 q"
                })
                return

            try:
                res = request_google_translate(text=text, sl=sl, tl=tl)
                self.send_json(200, {
                    "code": 200,
                    "success": True,
                    "data": res
                })
            except Exception as e:
                self.send_json(500, {
                    "code": 500,
                    "success": False,
                    "error": str(e)
                })
            return

        self.send_json(404, {"code": 404, "error": "Not Found"})

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip("/")

        if path != "/api/translate":
            self.send_json(404, {"code": 404, "error": "Not Found"})
            return

        content_length = int(self.headers.get("Content-Length", 0))
        post_bytes = self.rfile.read(content_length) if content_length > 0 else b""
        content_type = self.headers.get("Content-Type", "")

        text = ""
        sl = "auto"
        tl = "zh-CN"

        if "application/json" in content_type:
            try:
                payload = json.loads(post_bytes.decode("utf-8"))
                text = payload.get("text") or payload.get("q") or ""
                sl = payload.get("from") or payload.get("sl") or "auto"
                tl = payload.get("to") or payload.get("tl") or "zh-CN"
            except Exception as e:
                self.send_json(400, {"code": 400, "success": False, "error": f"JSON解析错误: {e}"})
                return
        else:
            params = urllib.parse.parse_qs(post_bytes.decode("utf-8", errors="replace"))
            text = (params.get("text") or params.get("q") or [""])[0]
            sl = (params.get("from") or params.get("sl") or ["auto"])[0]
            tl = (params.get("to") or params.get("tl") or ["zh-CN"])[0]

        if not text.strip():
            self.send_json(400, {
                "code": 400,
                "success": False,
                "error": "缺少待翻译文本 (text 或 q)"
            })
            return

        try:
            res = request_google_translate(text=text, sl=sl, tl=tl)
            self.send_json(200, {
                "code": 200,
                "success": True,
                "data": res
            })
        except Exception as e:
            self.send_json(500, {
                "code": 500,
                "success": False,
                "error": str(e)
            })

    def log_message(self, format, *args):
        # 简化日志输出
        sys.stderr.write(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {self.address_string()} - {format % args}\n")

def run(host: str = "0.0.0.0", port: int = 8787):
    server = ThreadingHTTPServer((host, port), ProxyHandler)
    print(f"=====================================================")
    print(f"🚀 SimpleTranslate Google Proxy Started")
    print(f"📡 Listening on http://{host}:{port}")
    print(f"🔍 Health check: http://127.0.0.1:{port}/health")
    print(f"🌐 API endpoint: http://127.0.0.1:{port}/api/translate")
    print(f"=====================================================")
    sys.stdout.flush()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        print("Server stopped.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="SimpleTranslate Google Proxy")
    parser.add_argument("--host", default=os.getenv("HOST", "0.0.0.0"), help="Host to bind (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=int(os.getenv("PORT", "8787")), help="Port to bind (default: 8787)")
    args = parser.parse_args()
    run(host=args.host, port=args.port)
