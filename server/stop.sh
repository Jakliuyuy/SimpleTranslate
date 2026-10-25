#!/bin/bash
# 停止 SimpleTranslate Google 中转代理服务
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$DIR/proxy.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "🛑 正在停止 SimpleTranslate Proxy (PID: $PID)..."
        kill "$PID"
        sleep 1
        if ps -p "$PID" > /dev/null 2>&1; then
            kill -9 "$PID"
        fi
        echo "✅ 服务已停止"
    else
        echo "⚠️ 进程 $PID 未运行"
    fi
    rm -f "$PID_FILE"
fi

PIDS=$(lsof -ti :8787 2>/dev/null)
if [ -n "$PIDS" ]; then
    echo "⚠️ 释放占用 8787 端口的残留进程: $PIDS"
    kill -9 $PIDS 2>/dev/null || true
fi
echo "✅ 8787 端口已就绪"
