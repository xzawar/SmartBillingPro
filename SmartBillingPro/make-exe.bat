@echo off
REM ============================================================
REM  Step 2 of 2 - Turn the JAR into a Windows application (.exe)
REM  Uses jpackage, which ships with JDK 17+.
REM  Produces a self-contained folder that bundles its own Java
REM  runtime, so it runs on PCs that do NOT have Java installed.
REM ============================================================
setlocal
cd /d "%~dp0"

where jpackage >nul 2>nul
if errorlevel 1 (
  echo [ERROR] jpackage was not found. Install a full JDK 17+ ^(not a JRE^).
  echo         Download: https://adoptium.net
  pause & exit /b 1
)

if not exist SmartBillingPro.jar (
  echo [ERROR] SmartBillingPro.jar is missing. Run build-jar.bat first.
  pause & exit /b 1
)

echo Preparing input folder...
if exist dist rmdir /s /q dist
if exist app-image rmdir /s /q app-image
mkdir dist
copy /y SmartBillingPro.jar dist\ >nul

echo Building the application image (this can take a minute)...
jpackage ^
  --type app-image ^
  --name SmartBillingPro ^
  --input dist ^
  --main-jar SmartBillingPro.jar ^
  --main-class com.smartbillingpro.SmartBillingApp ^
  --dest app-image ^
  --app-version 3.0
if errorlevel 1 ( echo [ERROR] jpackage failed. & pause & exit /b 1 )

echo.
echo [OK] Done!
echo      Your application is here:
echo      %~dp0app-image\SmartBillingPro\SmartBillingPro.exe
echo.
echo      Copy the whole "SmartBillingPro" folder to any Windows PC and
echo      double-click SmartBillingPro.exe - no Java needed on that PC.
pause
