@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\portable\setup-portable.ps1" %*
exit /b %ERRORLEVEL%
