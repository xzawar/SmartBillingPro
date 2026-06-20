@echo off
REM ============================================================
REM  Build a standalone ADMIN-ONLY Windows app (.exe) with jpackage.
REM  Requires a full JDK 17+ (which includes 'javac', 'jar' and 'jpackage').
REM  The result is a folder you can copy to any PC - no Java needed there.
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
if exist build\admin-input rmdir /s /q build\admin-input
mkdir build\admin-input
jar --create --file build\admin-input\SmartBillingPro-Admin.jar --main-class com.smartbillingpro.AdminApp -C out .
copy lib\*.jar build\admin-input\ >nul

echo Building the Admin .exe with jpackage...
if exist app-image-admin rmdir /s /q app-image-admin
jpackage --type app-image --name "SmartBillingPro-Admin" --input build\admin-input --main-jar SmartBillingPro-Admin.jar --main-class com.smartbillingpro.AdminApp --dest app-image-admin

echo.
echo Done. Your Admin app is in:  app-image-admin\SmartBillingPro-Admin\
echo Run it with:  app-image-admin\SmartBillingPro-Admin\SmartBillingPro-Admin.exe
pause
