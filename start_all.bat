@echo off
setlocal
set ROOT=%~dp0
if /I "%~1"=="dry-run" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%start_all.ps1" -DryRun
  exit /b %ERRORLEVEL%
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%start_all.ps1" %*
exit /b %ERRORLEVEL%
