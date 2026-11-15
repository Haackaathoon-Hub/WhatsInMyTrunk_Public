@echo off
setlocal
title Pleos Connect Emulator Launcher
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_pleos_emulator.ps1"
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] PowerShell launcher exited with error code %ERRORLEVEL%.
    pause
)
