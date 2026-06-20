@echo off
REM Smart Billing Pro - ADMIN CONSOLE ONLY (compile and run on Windows)
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  echo ERROR: 'javac' not found. Install a JDK 17+ from https://adoptium.net and try again.
  pause
  exit /b 1
)

if not exist out mkdir out
echo Compiling sources...
if exist sources.txt del sources.txt
powershell -NoProfile -Command "Get-ChildItem -Path 'src' -Recurse -Filter '*.java' | ForEach-Object { [char]34 + ($_.FullName -replace '\\','/') + [char]34 } | Set-Content -Encoding ASCII 'sources.txt'"
javac -d out -cp "lib/*" @sources.txt
if errorlevel 1 (
  echo.
  echo Compilation failed - see the errors above.
  if exist sources.txt del sources.txt
  pause
  exit /b 1
)
del sources.txt

echo Starting the Admin Console...
java -cp "out;lib/*" com.smartbillingpro.AdminApp
pause
