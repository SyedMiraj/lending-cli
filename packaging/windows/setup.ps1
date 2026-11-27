# Installs or uninstalls the Prime Lending CLI for the current user (no admin needed).
#   install:   copies this folder to %LOCALAPPDATA%\Programs\PrimeLendingCLI and adds it to the user PATH
#   uninstall: removes it from the user PATH and deletes that folder
# Run through install.cmd / uninstall.cmd.
param([ValidateSet('install', 'uninstall')][string]$Action = 'install')

$ErrorActionPreference = 'Stop'
$dest = Join-Path $env:LOCALAPPDATA 'Programs\PrimeLendingCLI'

function Get-UserPath {
    # Read the raw value so entries like %USERPROFILE%\bin stay unexpanded.
    (Get-Item 'HKCU:\Environment').GetValue('Path', '', 'DoNotExpandEnvironmentNames')
}

function Set-UserPath([string]$value) {
    Set-ItemProperty -Path 'HKCU:\Environment' -Name Path -Value $value -Type ExpandString
    # Setting a variable through .NET broadcasts the change, so new terminals see the new PATH.
    [Environment]::SetEnvironmentVariable('PRIME_LENDING_CLI_REFRESH', '1', 'User')
    [Environment]::SetEnvironmentVariable('PRIME_LENDING_CLI_REFRESH', $null, 'User')
}

$others = @((Get-UserPath) -split ';' | Where-Object { $_ -and ($_.TrimEnd('\') -ine $dest) })

if ($Action -eq 'install') {
    $src = $PSScriptRoot
    if ($src.TrimEnd('\') -ine $dest) {
        New-Item -ItemType Directory -Force -Path $dest | Out-Null
        Copy-Item -Path (Join-Path $src '*') -Destination $dest -Recurse -Force
    }
    # Older packages shipped the command as lending.exe; drop it so only "now-lending" remains.
    Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $dest 'lending.exe')
    Set-UserPath (($others + $dest) -join ';')
    Write-Host "[+] Installed to $dest"
    Write-Host '[+] Open a NEW terminal (Command Prompt or PowerShell) and type: now-lending'
} else {
    Set-UserPath ($others -join ';')
    if (Test-Path $dest) {
        Set-Location $env:TEMP
        # The folder may still be in use by the window that started us; delete it a moment later.
        Start-Process -WindowStyle Hidden cmd -ArgumentList "/c timeout /t 2 /nobreak >nul & rd /s /q `"$dest`""
    }
    Write-Host '[+] Prime Lending CLI removed.'
}
