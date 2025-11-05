@echo off
REM scripts/stop-fullstack.bat

echo 🛑 Stopping Full MathQuest Arena Stack...
echo.

taskkill /FI "WindowTitle eq REST API Server*" /F >nul 2>&1
taskkill /FI "WindowTitle eq Backend Servers*" /F >nul 2>&1
taskkill /FI "WindowTitle eq WebSocket Server*" /F >nul 2>&1
taskkill /FI "WindowTitle eq Frontend Dev Server*" /F >nul 2>&1
taskkill /FI "WindowTitle eq RMI Service*" /F >nul 2>&1
taskkill /FI "WindowTitle eq SSL Auth Server*" /F >nul 2>&1
taskkill /FI "WindowTitle eq TCP Game Server*" /F >nul 2>&1
taskkill /FI "WindowTitle eq NIO Manager*" /F >nul 2>&1
taskkill /FI "WindowTitle eq UDP Server*" /F >nul 2>&1

echo ✅ All services stopped
echo.
pause

