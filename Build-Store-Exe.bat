@echo off
REM ============================================================
REM  Build a standalone STORE-ONLY Windows app (.exe) with jpackage.
REM  Requires a full JDK 17+ (which includes 'javac', 'jar' and 'jpackage').
REM  This .exe opens owner / manager / cashier accounts only.
REM ============================================================
setlocal
cd /d "%~dp0"

where javac >nul 2>nul || (echo ERROR: install a JDK 17+ from https://adoptium.net & pause & exit /b 1)
where jpackage >nul 2>nul || (echo ERROR: 'jpackage' not found - use a full JDK 17+ & pause & exit /b 1)

if not exist out mkdir out
echo Compiling sources...
if exist sources.txt del sources.txt
powershell -NoProfile -Command "Get-ChildItem -Path 'src' -Recurse -Filter '*.java' | ForEach-Object { [char]34 + ($_.FullName -replace '\\','/') + [char]34 } | Set-Content -Encoding ASCII 'sources.txt'"
javac -d out -cp "lib/*" @sources.txt || (echo Compilation failed & del sources.txt & pause & exit /b 1)
del sources.txt

echo Staging files for packaging...
if exist build\store-input rmdir /s /q build\store-input
mkdir build\store-input
jar --create --file build\store-input\SmartBillingPro-Store.jar --main-class com.smartbillingpro.StoreApp -C out .
copy lib\*.jar build\store-input\ >nul

echo Building the Store .exe with jpackage...
if exist app-image-store rmdir /s /q app-image-store
jpackage --type app-image --name "SmartBillingPro-Store" --input build\store-input --main-jar SmartBillingPro-Store.jar --main-class com.smartbillingpro.StoreApp --dest app-image-store

echo.
echo Done. Your Store app is in:  app-image-store\SmartBillingPro-Store\
echo Run it with:  app-image-store\SmartBillingPro-Store\SmartBillingPro-Store.exe
pause
