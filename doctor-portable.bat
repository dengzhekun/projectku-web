@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\portable\doctor-portable.ps1" %*
exit /b %ERRORLEVEL%
