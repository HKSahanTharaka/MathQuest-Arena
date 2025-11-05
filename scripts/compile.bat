@echo off
REM scripts/compile.bat - Manual compilation script (no Maven required)

echo ========================================
echo 📦 Compiling MathQuest Arena
echo ========================================
echo.

cd /d "%~dp0.."

if not exist "target\classes" mkdir target\classes

echo Compiling common model classes...
javac -encoding UTF-8 -d target\classes src\main\java\com\netbattle\common\model\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling common protocol classes...
javac -encoding UTF-8 -d target\classes -cp target\classes src\main\java\com\netbattle\common\protocol\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling server core...
javac -encoding UTF-8 -d target\classes -cp target\classes -sourcepath src\main\java src\main\java\com\netbattle\server\core\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling NIO manager...
javac -encoding UTF-8 -d target\classes -cp target\classes -sourcepath src\main\java src\main\java\com\netbattle\server\nio\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling UDP server...
javac -encoding UTF-8 -d target\classes -cp target\classes -sourcepath src\main\java src\main\java\com\netbattle\server\udp\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling security layer...
javac -encoding UTF-8 -d target\classes -cp target\classes -sourcepath src\main\java src\main\java\com\netbattle\server\security\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling RMI service...
javac -encoding UTF-8 -d target\classes -cp target\classes -sourcepath src\main\java src\main\java\com\netbattle\server\rmi\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling client...
javac -encoding UTF-8 -d target\classes -cp target\classes -sourcepath src\main\java src\main\java\com\netbattle\client\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling WebSocket server...
javac -encoding UTF-8 -d target\classes -cp target\classes -sourcepath src\main\java src\main\java\com\netbattle\websocket\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling REST API server...
javac -encoding UTF-8 -d target\classes -cp target\classes -sourcepath src\main\java src\main\java\com\netbattle\api\*.java
if %ERRORLEVEL% neq 0 goto :error

echo Compiling Service Discovery...
javac -encoding UTF-8 -d target\classes -cp target\classes -sourcepath src\main\java src\main\java\com\netbattle\discovery\*.java
if %ERRORLEVEL% neq 0 goto :error

echo.
echo ✅ Compilation successful!
echo    Classes: target\classes
echo.
goto :end

:error
echo.
echo ❌ Compilation failed!
echo.
pause
exit /b 1

:end

