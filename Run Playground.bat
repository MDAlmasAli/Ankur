@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command ".\build.ps1; if ($LASTEXITCODE -eq 0) { .\playground.ps1 }"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Kichu bhul hoyeche. Screenshot tule Claude-ke dekhao.
    pause
)
