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
else
    echo "⚠️ PID 文件不存在，尝试通过端口查找并停止..."
    PIDS=$(lsof -ti :8787 2>/dev/null)
    if [ -n "$PIDS" ]; then
        kill -9 $PIDS
        echo "✅ 已杀死占用 8787 端口的进程: $PIDS"
    else
        echo "未发现运行中的服务。"
    fi
fi
