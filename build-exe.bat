@echo off
REM ==========================================================================
REM  Build the Windows desktop app with jpackage (included in JDK 17+).
REM
REM  Result:  dist\SmartBillingPro\SmartBillingPro.exe   (portable folder,
REM           includes its own Java runtime - nothing else to install)
REM           dist\SmartBillingPro-<version>-windows.zip
REM  If the WiX Toolset 3 is installed (candle.exe on PATH) an installer
REM  dist\SmartBillingPro-<version>.exe is also created.
REM ==========================================================================
setlocal
cd /d "%~dp0"
set APP_VERSION=4.0.0

where jpackage >nul 2>nul || (echo ERROR: jpackage not found. Install a full JDK 17+ from https://adoptium.net & goto :fail)

echo [1/5] Compiling...
if exist build rmdir /s /q build
mkdir build\classes build\input
powershell -NoProfile -Command "Get-ChildItem -Path 'src' -Recurse -Filter '*.java' | ForEach-Object { [char]34 + ($_.FullName -replace '\\','/') + [char]34 } | Set-Content -Encoding ASCII 'sources.txt'"
javac -encoding UTF-8 --release 17 -d build\classes -cp "lib/*" @sources.txt || (del sources.txt & goto :fail)
del sources.txt
xcopy /e /i /q /y resources build\classes >nul

echo [2/5] Creating the application jar...
jar --create --file build\input\SmartBillingPro.jar --main-class com.smartbillingpro.SmartBillingApp -C build\classes . || goto :fail
copy /y lib\*.jar build\input\ >nul

echo [3/5] Packaging the app with its own Java runtime...
if exist dist rmdir /s /q dist
mkdir dist
jpackage --type app-image --name SmartBillingPro --app-version %APP_VERSION% --vendor "Smart Billing Pro" ^
  --input build\input --main-jar SmartBillingPro.jar --main-class com.smartbillingpro.SmartBillingApp ^
  --icon packaging\app.ico --java-options "-Dfile.encoding=UTF-8" --java-options "-Xmx768m" ^
  --dest dist || goto :fail

echo [4/5] Zipping the portable app...
powershell -NoProfile -Command "Compress-Archive -Path 'dist\SmartBillingPro' -DestinationPath 'dist\SmartBillingPro-%APP_VERSION%-windows.zip' -Force" || goto :fail

echo [5/5] Installer (optional, needs WiX Toolset 3)...
where candle >nul 2>nul
if errorlevel 1 (
  echo     WiX not found - skipping installer. The portable app in dist\SmartBillingPro works on its own.
) else (
  jpackage --type exe --name SmartBillingPro --app-version %APP_VERSION% --vendor "Smart Billing Pro" ^
    --app-image dist\SmartBillingPro --win-menu --win-shortcut --win-dir-chooser ^
    --win-upgrade-uuid 6f4f3c1e-8a3b-4b6e-9a51-6b8d2f0c4e11 --dest dist || goto :fail
)

echo.
echo Done.
echo   Portable app : dist\SmartBillingPro\SmartBillingPro.exe
echo   Zip          : dist\SmartBillingPro-%APP_VERSION%-windows.zip
if "%CI%"=="" pause
exit /b 0

:fail
echo.
echo BUILD FAILED - see the messages above.
if "%CI%"=="" pause
exit /b 1
