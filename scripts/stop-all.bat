@echo off
REM scripts/stop-all.bat

echo 🛑 Stopping MathQuest Arena Servers...
echo.

taskkill /FI "WindowTitle eq RMI Service*" /F >nul 2>&1
taskkill /FI "WindowTitle eq SSL Auth Server*" /F >nul 2>&1
taskkill /FI "WindowTitle eq TCP Game Server*" /F >nul 2>&1
taskkill /FI "WindowTitle eq NIO Manager*" /F >nul 2>&1
taskkill /FI "WindowTitle eq UDP Server*" /F >nul 2>&1

echo ✅ All servers stopped
echo.
pause

