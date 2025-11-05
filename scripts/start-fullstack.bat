@echo off
REM scripts/start-fullstack.bat - Start complete application

echo ========================================
echo 🚀 Starting Full MathQuest Arena Stack
echo ========================================
echo.

cd /d "%~dp0.."

echo Step 1: Compiling backend...
call scripts\compile.bat
if %ERRORLEVEL% neq 0 (
    echo ❌ Backend compilation failed!
    pause
    exit /b 1
)

echo.
echo Step 2: Generating SSL keystore...
if not exist "resources\keystore.jks" (
    cd resources
    call generate-keystore.bat
    cd ..
)

echo.
echo Step 3: Starting Service Discovery/Registry...
start "Service Discovery" /MIN cmd /c "java -cp target/classes com.netbattle.discovery.ServiceDiscoveryServer > logs/discovery.log 2>&1"
timeout /t 2 /nobreak >nul

echo.
echo Step 4: Starting REST API server...
start "REST API Server" /MIN cmd /c "java -cp target/classes com.netbattle.api.RestApiServer > logs/api.log 2>&1"
timeout /t 3 /nobreak >nul

echo.
echo Step 5: Starting WebSocket bridge...
start "WebSocket Server" /MIN cmd /c "java -cp target/classes com.netbattle.websocket.WebSocketGameServer > logs/websocket.log 2>&1"
timeout /t 2 /nobreak >nul

echo.
echo Step 6: Starting other backend servers...
start "Backend Servers" /MIN cmd /c "scripts\start-all.bat"
timeout /t 2 /nobreak >nul

echo.
echo Step 7: Starting frontend...
start "Frontend Dev Server" cmd /c "scripts\start-frontend.bat"

echo.
echo ========================================
echo ✅ Full Stack Started!
echo ========================================
echo.
echo Services running:
echo   🔍 Service Discovery: http://localhost:8084
echo   🌐 REST API: http://localhost:8080
echo   🌐 WebSocket Bridge: ws://localhost:8082
echo   🎮 Other Backend Servers: Running
echo   🎨 Frontend: http://localhost:3000
echo.
echo To stop all services: scripts\stop-fullstack.bat
echo.
pause

