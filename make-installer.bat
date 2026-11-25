@echo off
REM ==========================================================================
REM  Smart Billing Pro - make the Windows installer (developed by KeyKraft)
REM
REM  Double-click this file. It creates:
REM      dist\SmartBillingPro-Setup-4.0.0.exe
REM  a single installer that contains everything the app needs (its own Java
REM  runtime). Copy it to the shop's laptop and run it there: the customer
REM  must accept the terms of use, and the installed app only runs on that
REM  laptop.
REM
REM  Needs a Java JDK 17+ and Inno Setup 6 on THIS computer (the one that
REM  builds the installer). If they are missing you are offered to install
REM  them with winget. Options: -SkipTests  (do not run the tests first)
REM ==========================================================================
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0packaging\make-installer.ps1" %*
set RESULT=%ERRORLEVEL%
if "%CI%"=="" pause
exit /b %RESULT%
