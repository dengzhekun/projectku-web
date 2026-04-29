@echo off
setlocal
set ROOT=%~dp0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\portable\package-portable.ps1" %*
exit /b %ERRORLEVEL%
