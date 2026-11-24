@echo off
REM Runs the automated test suite (uses temporary data folders, never your shop data).
setlocal
cd /d "%~dp0"
if exist out-test rmdir /s /q out-test
mkdir out-test
powershell -NoProfile -Command "Get-ChildItem -Path 'src','test' -Recurse -Filter '*.java' | ForEach-Object { [char]34 + ($_.FullName -replace '\\','/') + [char]34 } | Set-Content -Encoding ASCII 'sources.txt'"
javac -encoding UTF-8 -d out-test -cp "lib/*" @sources.txt
if errorlevel 1 (
  del sources.txt
  exit /b 1
)
del sources.txt
xcopy /e /i /q /y resources out-test >nul
java -Djava.awt.headless=true -cp "out-test;lib/*" com.smartbillingpro.TestRunner %*
set RESULT=%ERRORLEVEL%
if "%CI%"=="" pause
exit /b %RESULT%
