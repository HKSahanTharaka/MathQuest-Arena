@echo off
REM scripts/start-frontend.bat

echo ========================================
echo 🧮 Starting MathQuest Arena Frontend
echo ========================================
echo.

cd /d "%~dp0..\frontend"

if not exist "node_modules" (
    echo 📦 Installing dependencies...
    call npm install
    if %ERRORLEVEL% neq 0 (
        echo ❌ Failed to install dependencies!
        echo.
        echo Make sure Node.js and npm are installed:
        echo   https://nodejs.org/
        pause
        exit /b 1
    )
)

echo 🚀 Starting development server...
echo.
echo Frontend will be available at:
echo   http://localhost:3000
echo.

call npm run dev

