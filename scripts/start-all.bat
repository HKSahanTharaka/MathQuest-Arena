@echo off
REM scripts/start-all.bat

echo ========================================
echo 🧮 Starting MathQuest Arena Servers...
echo ========================================
echo.

cd /d "%~dp0.."

if not exist "resources\keystore.jks" (
    echo 🔐 Generating SSL keystore...
    cd resources
    call generate-keystore.bat
    cd ..
)

echo 📦 Compiling project...
if not exist "target\classes" (
    call scripts\compile.bat
    if %ERRORLEVEL% neq 0 (
        echo ❌ Compilation failed!
        pause
        exit /b 1
    )
) else (
    echo ✅ Classes already compiled (skipping)
)

if not exist "logs" mkdir logs

echo.
echo 🚀 Starting servers...
echo.

echo 📊 Starting RMI Service...
start "RMI Service" /MIN cmd /c "java -cp target/classes com.netbattle.server.rmi.RMIServer > logs/rmi.log 2>&1"
timeout /t 2 /nobreak >nul

echo 🔒 Starting Secure Auth Server...
start "SSL Auth Server" /MIN cmd /c "java -cp target/classes com.netbattle.server.security.SecureAuthServer > logs/ssl.log 2>&1"
timeout /t 2 /nobreak >nul

echo 🎮 Starting TCP Game Server...
start "TCP Game Server" /MIN cmd /c "java -cp target/classes com.netbattle.server.core.GameServer > logs/tcp.log 2>&1"
timeout /t 2 /nobreak >nul

echo ⚡ Starting NIO Manager...
start "NIO Manager" /MIN cmd /c "java -cp target/classes com.netbattle.server.nio.NIOGameStateManager > logs/nio.log 2>&1"
timeout /t 2 /nobreak >nul

echo 📡 Starting UDP Server...
start "UDP Server" /MIN cmd /c "java -cp target/classes com.netbattle.server.udp.UDPEventServer > logs/udp.log 2>&1"
timeout /t 2 /nobreak >nul

echo.
echo ✅ All servers started!
echo.
echo Server Status:
echo   📊 RMI Service:    localhost:1099
echo   🔒 SSL Auth:       localhost:8443
echo   🎮 TCP Server:     localhost:8080
echo   ⚡ NIO Manager:    localhost:8081
echo   📡 UDP Server:     localhost:9000
echo.
echo To start client: java -cp target/classes com.netbattle.client.GameClient
echo To stop servers: scripts\stop-all.bat
echo View logs: type logs\*.log
echo.
pause

