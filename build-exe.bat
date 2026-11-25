@echo off
REM Older name for make-installer.bat - builds dist\SmartBillingPro-Setup-<version>.exe
call "%~dp0make-installer.bat" %*
exit /b %ERRORLEVEL%
