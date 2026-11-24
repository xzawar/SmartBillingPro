@echo off
REM Smart Billing Pro - compile and run from source (Windows). Needs JDK 17+.
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  echo ERROR: 'javac' was not found. Install a JDK 17 or newer from https://adoptium.net and try again.
  pause
  exit /b 1
)

if exist out rmdir /s /q out
mkdir out
echo Compiling...
powershell -NoProfile -Command "Get-ChildItem -Path 'src' -Recurse -Filter '*.java' | ForEach-Object { [char]34 + ($_.FullName -replace '\\','/') + [char]34 } | Set-Content -Encoding ASCII 'sources.txt'"
javac -encoding UTF-8 -d out -cp "lib/*" @sources.txt
if errorlevel 1 (
  del sources.txt
  echo.
  echo Compilation failed - see the errors above.
  pause
  exit /b 1
)
del sources.txt
xcopy /e /i /q /y resources out >nul

echo Starting Smart Billing Pro...
start "" javaw -Dfile.encoding=UTF-8 -cp "out;lib/*" com.smartbillingpro.SmartBillingApp
