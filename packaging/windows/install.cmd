@echo off
rem Double-click to install the Prime Lending CLI for this user, then type "now-lending" in any new terminal.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" install
if errorlevel 1 echo [-] Installation failed.
pause
