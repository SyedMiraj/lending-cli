@echo off
rem Removes the Prime Lending CLI and its PATH entry for this user.
cd /d "%TEMP%"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" uninstall & pause & exit /b
