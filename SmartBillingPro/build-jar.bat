@echo off
REM ============================================================
REM  Step 1 of 2 - Build a single runnable JAR
REM  Requires a full JDK 17+ (java + javac + jar on PATH).
REM  Put the sqlite-jdbc-*.jar inside the lib\ folder first.
REM ============================================================
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  echo [ERROR] javac was not found. Install a full JDK 17+ ^(not just a JRE^).
  echo         Download: https://adoptium.net  then reopen this window.
  pause & exit /b 1
)

if not exist lib\*.jar (
  echo [ERROR] No SQLite driver found in the lib folder.
  echo         Put sqlite-jdbc-3.45.x.x.jar inside:  %~dp0lib
  pause & exit /b 1
)

echo Cleaning previous build...
if exist out rmdir /s /q out
if exist SmartBillingPro.jar del /q SmartBillingPro.jar
mkdir out

echo Compiling Java sources...
powershell -NoProfile -Command "Get-ChildItem -Path 'src' -Recurse -Filter '*.java' | ForEach-Object { [char]34 + ($_.FullName -replace '\\','/') + [char]34 } | Set-Content -Encoding ASCII 'sources.txt'"
javac -d out -cp "lib/*" @sources.txt
if errorlevel 1 ( echo [ERROR] Compilation failed. & pause & exit /b 1 )

echo Bundling the SQLite driver into the app...
for %%j in (lib\*.jar) do (
  pushd out
  jar xf "..\%%j"
  popd
)
REM Remove signed-jar signatures so the merged jar stays valid
if exist out\META-INF\*.SF del /q out\META-INF\*.SF
if exist out\META-INF\*.RSA del /q out\META-INF\*.RSA
if exist out\META-INF\*.DSA del /q out\META-INF\*.DSA

echo Creating SmartBillingPro.jar...
jar --create --file SmartBillingPro.jar --main-class com.smartbillingpro.SmartBillingApp -C out .
if errorlevel 1 ( echo [ERROR] Failed to create the jar. & pause & exit /b 1 )

echo.
echo [OK] Built SmartBillingPro.jar
echo      Test it with:  java -jar SmartBillingPro.jar
echo      Now run  make-exe.bat  to create the Windows .exe
pause
