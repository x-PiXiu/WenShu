@echo off
chcp 65001 >nul 2>&1
title 文枢 · 藏书阁 (WenShu)

where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Java 环境，请安装 JDK 17+
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)

java -version 2>&1 | findstr /r "17\|18\|19\|20\|21\|22\|23\|24\|25\|26" >nul 2>&1
if %errorlevel% neq 0 (
    echo [警告] 当前 Java 版本可能不兼容，建议使用 JDK 17+
    echo.
)

if not exist config.json (
    if exist config.example.json (
        copy config.example.json config.json >nul
        echo [初始化] 已从模板创建 config.json，请编辑配置后重新启动
        echo.
        notepad config.json
        exit /b 0
    ) else (
        echo [错误] 缺少配置文件 config.json
        pause
        exit /b 1
    )
)

echo ========================================
echo   文枢 · 藏书阁  WenShu
echo   http://localhost:8081
echo ========================================
echo.
echo 启动中... 首次运行可能需要几秒钟
echo 按 Ctrl+C 停止服务
echo.

java --enable-native-access=ALL-UNNAMED -jar wenshu.jar

pause
