@echo off
rem Builds the Windows client package: dist\PrimeLendingCLI\ and dist\PrimeLendingCLI.zip
rem Run from the "x64 Native Tools Command Prompt for VS" with JAVA_HOME pointing to GraalVM.
setlocal
cd /d "%~dp0"

where cl >nul 2>&1 || (
  echo [-] cl.exe not found. Open the "x64 Native Tools Command Prompt for VS" and run this again.
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\native-image.cmd" if not exist "%JAVA_HOME%\bin\native-image.exe" (
  echo [-] JAVA_HOME does not point to GraalVM: "%JAVA_HOME%"
  exit /b 1
)

rem --package-only: reuse the existing target\lending-cli.exe instead of rebuilding it.
if /i not "%~1"=="--package-only" (
  call "%~dp0mvnw.cmd" -Pnative clean package || exit /b 1
)

set "OUT=dist\PrimeLendingCLI"
if exist dist rmdir /s /q dist
mkdir "%OUT%\config"
rem Shipped as lending.exe so clients can type "lending" once it is on PATH (see install.cmd).
copy /y target\lending-cli.exe "%OUT%\lending.exe" >nul || exit /b 1
copy /y packaging\windows\install.cmd "%OUT%\" >nul || exit /b 1
copy /y packaging\windows\uninstall.cmd "%OUT%\" >nul || exit /b 1
copy /y packaging\windows\setup.ps1 "%OUT%\" >nul || exit /b 1
copy /y packaging\windows\README.txt "%OUT%\" >nul || exit /b 1

rem The exe needs the Visual C++ runtime, which a clean Windows may lack. Ship it app-locally.
set "CRT="
for /d %%D in ("%VCToolsRedistDir%x64\Microsoft.VC*.CRT") do set "CRT=%%~D"
if not defined CRT (
  echo [-] Visual C++ runtime DLLs not found under "%VCToolsRedistDir%x64".
  exit /b 1
)
copy /y "%CRT%\vcruntime140.dll" "%OUT%\" >nul || exit /b 1
copy /y "%CRT%\vcruntime140_1.dll" "%OUT%\" >nul || exit /b 1

if exist config\merchants.json (
  copy /y config\merchants.json "%OUT%\config\" >nul
  echo [+] Included config\merchants.json - it contains credentials, share the package securely.
) else (
  copy /y config\merchants.example.json "%OUT%\config\merchants.json" >nul
  echo [!] config\merchants.json not found - packaged the example config; fill in CHANGE_ME before sending.
)

powershell -NoProfile -Command "Compress-Archive -Path '%OUT%' -DestinationPath 'dist\PrimeLendingCLI.zip' -Force" || exit /b 1
echo [+] Done: dist\PrimeLendingCLI.zip
echo     Client: unzip, double-click install.cmd once, then type "lending" in a new terminal.
