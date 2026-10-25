#!/bin/bash
# 启动 SimpleTranslate Google 中转代理服务
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$DIR/proxy.pid"
LOG_FILE="$DIR/proxy.log"
PORT="${PORT:-8787}"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "⚠️ SimpleTranslate Proxy 正在运行 (PID: $PID, 端口: $PORT)"
        exit 0
    else
        rm -f "$PID_FILE"
    fi
fi

echo "🚀 启动 SimpleTranslate Google 中转服务 (端口: $PORT)..."
nohup python3 "$DIR/server.py" --port "$PORT" > "$LOG_FILE" 2>&1 &
NEW_PID=$!
echo $NEW_PID > "$PID_FILE"
sleep 1

if ps -p "$NEW_PID" > /dev/null 2>&1; then
    echo "✅ 服务已成功在后台启动！PID: $NEW_PID"
    echo "📡 访问地址: http://127.0.0.1:$PORT/health"
    echo "📄 日志文件: $LOG_FILE"
else
    echo "❌ 启动失败，请查看日志: $LOG_FILE"
    cat "$LOG_FILE"
    exit 1
fi
