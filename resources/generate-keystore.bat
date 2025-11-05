@echo off
REM resources/generate-keystore.bat

echo 🔐 Generating SSL Keystore for NetBattle Arena
echo ================================================
echo.

if exist keystore.jks (
    echo Keystore already exists. Deleting old keystore...
    del keystore.jks
)

echo Generating new keystore...
keytool -genkeypair -alias netbattle -keyalg RSA -keysize 2048 ^
    -validity 365 -keystore keystore.jks ^
    -storepass netbattle123 -keypass netbattle123 ^
    -dname "CN=NetBattle Arena, OU=IT, O=NetBattle, L=City, ST=State, C=US"

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ Keystore generated successfully!
    echo    File: keystore.jks
    echo    Password: netbattle123
) else (
    echo.
    echo ❌ Failed to generate keystore!
    echo Make sure Java keytool is in your PATH
)

echo.

