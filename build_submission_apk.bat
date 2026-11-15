@echo off
setlocal

echo ============================================================================
echo   HMG Pleos Connect - Dual APK Packaging Script (Prod Release + Dev Debugger)
echo   App: WHAT'S IN MY TRUNK? (Pleos Vehicle Context Intelligence)
echo ============================================================================

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
set "PROJECT_DIR=%~dp0"
cd /d "%PROJECT_DIR%"

echo.
echo [1/3] Running Unit Tests and Assembling Prod Release + Dev Debugger APKs...
call .\gradlew.bat testProdDebugUnitTest testDevDebugUnitTest assembleProdRelease assembleDevDebug
if errorlevel 1 (
    echo [ERROR] Build or unit test execution failed.
    exit /b 1
)

echo.
echo [2/3] Copying APKs to apk/ folder...
if not exist "apk" mkdir "apk"

set "PROD_RELEASE_SRC=app\build\outputs\apk\prod\release\app-prod-release.apk"
set "DEV_DEBUG_SRC=app\build\outputs\apk\dev\debug\app-dev-debug.apk"
set "RELEASE_TARGET=apk\Players_WhatsInMyTrunk_release.apk"
set "DEBUGGER_TARGET=apk\Players_WhatsInMyTrunk_debugger.apk"

if exist "%PROD_RELEASE_SRC%" (
    copy /y "%PROD_RELEASE_SRC%" "%RELEASE_TARGET%" > nul
    copy /y "%PROD_RELEASE_SRC%" "apk\app-prod-release.apk" > nul
    echo [OK] Prod Release APK [No Dev Console, R8 Optimized]: %RELEASE_TARGET%
) else (
    echo [ERROR] Prod Release APK not found: %PROD_RELEASE_SRC%
    exit /b 1
)

if exist "%DEV_DEBUG_SRC%" (
    copy /y "%DEV_DEBUG_SRC%" "%DEBUGGER_TARGET%" > nul
    copy /y "%DEV_DEBUG_SRC%" "apk\app-dev-debug.apk" > nul
    echo [OK] Dev Debugger APK [With Dev Console]: %DEBUGGER_TARGET%
) else (
    echo [ERROR] Dev Debugger APK not found: %DEV_DEBUG_SRC%
    exit /b 1
)

echo.
echo ============================================================================
echo   [3/3] Dual APK packaging completed successfully!
echo   Generated APK files in apk/:
dir "apk\*.apk" /b
echo ============================================================================
echo.
