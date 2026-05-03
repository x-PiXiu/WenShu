#!/bin/bash
# 文枢 · 藏书阁 (WenShu) Launcher

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v java &>/dev/null; then
    echo "[错误] 未检测到 Java 环境，请安装 JDK 17+"
    echo "下载地址: https://adoptium.net/"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | grep -oP '\d+' | head -1)
if [ -n "$JAVA_VERSION" ] && [ "$JAVA_VERSION" -lt 17 ]; then
    echo "[警告] 当前 Java 版本不兼容，需要 JDK 17+"
    exit 1
fi

if [ ! -f config.json ]; then
    if [ -f config.example.json ]; then
        cp config.example.json config.json
        echo "[初始化] 已从模板创建 config.json，请编辑配置后重新启动"
        echo "  vi config.json"
        exit 0
    else
        echo "[错误] 缺少配置文件 config.json"
        exit 1
    fi
fi

echo "========================================"
echo "  文枢 · 藏书阁  WenShu"
echo "  http://localhost:8081"
echo "========================================"
echo ""
echo "启动中... 按 Ctrl+C 停止服务"
echo ""

java --enable-native-access=ALL-UNNAMED -jar wenshu.jar
