<#
.SYNOPSIS
  Builds the Smart Billing Pro installer for Windows: dist\SmartBillingPro-Setup-<version>.exe

.DESCRIPTION
  Start it with make-installer.bat (double-click) in the project folder.

  1. Checks the tools: a Java JDK 17+ (for jpackage) and Inno Setup 6.
     Missing tools can be installed with winget after you agree.
  2. Runs the automated tests (skip with -SkipTests).
  3. Compiles the app and gives this build its own computer-registration token.
  4. Packages the app with its own Java runtime (jpackage), so the target
     laptop needs nothing else installed.
  5. Creates the installer with Inno Setup: KeyKraft branding, the terms of
     use (must be accepted), shortcuts, an uninstaller, and registration of
     the computer it is installed on.

  Copy the finished setup .exe to the shop's laptop and run it there. The
  installed app only runs on the computer it was installed on.

.PARAMETER SkipTests
  Do not run the automated tests first.

.PARAMETER NonInteractive
  Never ask questions (used by the automatic build on GitHub).
#>
[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$NonInteractive
)

$ErrorActionPreference = 'Stop'
$AppVersion = '4.0.0'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$Build = Join-Path $Root 'build'
$Dist = Join-Path $Root 'dist'
$Total = 6

function Step([int]$n, [string]$text) {
    Write-Host ''
    Write-Host "[$n/$Total] $text" -ForegroundColor Cyan
}

function Fail([string]$text) {
    Write-Host ''
    Write-Host "INSTALLER NOT CREATED: $text" -ForegroundColor Red
    exit 1
}

function Ask([string]$question) {
    if ($NonInteractive) { return $false }
    $answer = Read-Host "$question [Y/N]"
    return ($answer -match '^(y|yes)$')
}

function Run([string]$exe, [string[]]$arguments) {
    & $exe @arguments | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "$([IO.Path]::GetFileName($exe)) stopped with exit code $LASTEXITCODE (see the messages above)." }
}

function Get-JdkMajor([string]$bin) {
    $release = Join-Path (Split-Path -Parent $bin) 'release'
    if (-not (Test-Path $release)) { return 0 }
    $line = Select-String -Path $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    if ($line -and $line.Matches.Count -gt 0) { return [int]$line.Matches[0].Groups[1].Value }
    return 0
}

function Find-Jdk {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin')) }
    $cmd = Get-Command jpackage.exe -ErrorAction SilentlyContinue
    if ($cmd) { $candidates.Add((Split-Path -Parent $cmd.Source)) }
    foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft",
                        "$env:ProgramFiles\Zulu", "$env:ProgramFiles\Amazon Corretto")) {
        if (Test-Path $base) {
            Get-ChildItem $base -Directory | Sort-Object Name -Descending | ForEach-Object {
                $candidates.Add((Join-Path $_.FullName 'bin'))
            }
        }
    }
    foreach ($bin in $candidates) {
        if ((Test-Path (Join-Path $bin 'jpackage.exe')) -and (Test-Path (Join-Path $bin 'javac.exe')) -and
            (Get-JdkMajor $bin) -ge 17) {
            return $bin
        }
    }
    return $null
}

function Find-Iscc {
    $paths = New-Object System.Collections.Generic.List[string]
    $cmd = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($cmd) { $paths.Add($cmd.Source) }
    $paths.Add("${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe")
    $paths.Add("$env:ProgramFiles\Inno Setup 6\ISCC.exe")
    $paths.Add("$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe")
    foreach ($p in $paths) { if ($p -and (Test-Path $p)) { return $p } }
    return $null
}

function Install-WithWinget([string]$id, [string]$name) {
    if (-not (Get-Command winget.exe -ErrorAction SilentlyContinue)) { return $false }
    if (-not (Ask "$name is needed to build the installer but is not installed. Install it now with winget?")) { return $false }
    & winget install --id $id -e --accept-package-agreements --accept-source-agreements | Out-Host
    return ($LASTEXITCODE -eq 0)
}

function Write-Sources([string]$listFile, [string[]]$folders) {
    $files = foreach ($f in $folders) {
        Get-ChildItem -Path (Join-Path $Root $f) -Recurse -Filter '*.java' | ForEach-Object {
            '"' + ($_.FullName -replace '\\', '/') + '"'
        }
    }
    [IO.File]::WriteAllLines($listFile, [string[]]$files, (New-Object System.Text.UTF8Encoding($false)))
}

Write-Host "Smart Billing Pro $AppVersion - installer builder (KeyKraft)" -ForegroundColor Green

# ------------------------------------------------------------------ 1. tools
Step 1 'Checking the tools...'
$jdk = Find-Jdk
if (-not $jdk) {
    if (Install-WithWinget 'EclipseAdoptium.Temurin.21.JDK' 'A Java JDK (Temurin 21)') { $jdk = Find-Jdk }
}
if (-not $jdk) {
    Fail 'A Java JDK 17 or newer is needed (it includes jpackage). Install "Eclipse Temurin 21 JDK" from https://adoptium.net and run this again.'
}
$iscc = Find-Iscc
if (-not $iscc) {
    if (Install-WithWinget 'JRSoftware.InnoSetup' 'Inno Setup 6') { $iscc = Find-Iscc }
}
if (-not $iscc) {
    Fail 'Inno Setup 6 is needed to create the installer. Install it from https://jrsoftware.org/isdl.php (or run: winget install JRSoftware.InnoSetup) and run this again.'
}
$java = Join-Path $jdk 'java.exe'
$javac = Join-Path $jdk 'javac.exe'
$jar = Join-Path $jdk 'jar.exe'
$jpackage = Join-Path $jdk 'jpackage.exe'
Write-Host "  Java JDK    : $jdk"
Write-Host "  Inno Setup  : $iscc"

if (Test-Path $Build) { Remove-Item $Build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Build | Out-Null

# ------------------------------------------------------------------ 2. tests
if ($SkipTests) {
    Step 2 'Skipping the automated tests (-SkipTests).'
} else {
    Step 2 'Running the automated tests...'
    $testClasses = Join-Path $Build 'test-classes'
    New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
    $testList = Join-Path $Build 'test-sources.txt'
    Write-Sources $testList @('src', 'test')
    Run $javac @('-encoding', 'UTF-8', '--release', '17', '-d', $testClasses, '-cp', 'lib/*', "@$testList")
    Copy-Item -Path (Join-Path $Root 'resources\*') -Destination $testClasses -Recurse -Force
    Run $java @('-Djava.awt.headless=true', '-cp', "$testClasses;lib/*", 'com.smartbillingpro.TestRunner')
}

# ------------------------------------------------------------------ 3. compile + token
Step 3 'Compiling the app...'
$classes = Join-Path $Build 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$srcList = Join-Path $Build 'sources.txt'
Write-Sources $srcList @('src')
Run $javac @('-encoding', 'UTF-8', '--release', '17', '-d', $classes, '-cp', 'lib/*', "@$srcList")
Copy-Item -Path (Join-Path $Root 'resources\*') -Destination $classes -Recurse -Force

# Each build gets its own random token. Only its SHA-256 goes into the app; the token itself only
# exists inside this build's installer, which uses it to register the computer it installs on.
$token = $env:SBP_BIND_TOKEN
if (-not $token) { $token = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N') }
if ($token -notmatch '^[A-Za-z0-9]{16,128}$') { Fail 'SBP_BIND_TOKEN must be 16-128 letters or digits.' }
$sha = [Security.Cryptography.SHA256]::Create()
$hash = ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($token)) | ForEach-Object { $_.ToString('x2') }) -join ''
$licenseDir = Join-Path $classes 'license'
New-Item -ItemType Directory -Force -Path $licenseDir | Out-Null
[IO.File]::WriteAllText((Join-Path $licenseDir 'bind.sha256'), $hash, [Text.Encoding]::ASCII)

$jarInput = Join-Path $Build 'input'
New-Item -ItemType Directory -Force -Path $jarInput | Out-Null
Run $jar @('--create', '--file', (Join-Path $jarInput 'SmartBillingPro.jar'), '--main-class', 'com.smartbillingpro.SmartBillingApp',
           '-C', $classes, '.')
Copy-Item -Path (Join-Path $Root 'lib\*.jar') -Destination $jarInput -Force

# ------------------------------------------------------------------ 4. app with its own runtime
Step 4 'Packaging the app with its own Java runtime (this takes a minute)...'
$imageRoot = Join-Path $Build 'app-image'
Run $jpackage @('--type', 'app-image', '--name', 'SmartBillingPro', '--app-version', $AppVersion,
                '--vendor', 'KeyKraft', '--copyright', 'Copyright (c) 2026 KeyKraft',
                '--description', 'Smart Billing Pro - offline point of sale',
                '--input', $jarInput, '--main-jar', 'SmartBillingPro.jar', '--main-class', 'com.smartbillingpro.SmartBillingApp',
                '--icon', (Join-Path $Root 'packaging\app.ico'),
                '--java-options', '-Dfile.encoding=UTF-8', '--java-options', '-Xmx768m',
                '--dest', $imageRoot)
$appImage = Join-Path $imageRoot 'SmartBillingPro'
if (-not (Test-Path (Join-Path $appImage 'SmartBillingPro.exe'))) { Fail 'jpackage did not create SmartBillingPro.exe.' }

# ------------------------------------------------------------------ 5. installer
Step 5 'Creating the installer with Inno Setup...'
if (-not (Test-Path $Dist)) { New-Item -ItemType Directory -Force -Path $Dist | Out-Null }
$setupName = "SmartBillingPro-Setup-$AppVersion.exe"
if (Test-Path (Join-Path $Dist $setupName)) { Remove-Item (Join-Path $Dist $setupName) -Force }
Run $iscc @('/Qp', "/DAppVersion=$AppVersion", "/DAppImageDir=$appImage", "/DBindToken=$token", "/DOutputDir=$Dist",
            (Join-Path $Root 'packaging\installer\SmartBillingPro.iss'))
$setup = Join-Path $Dist $setupName
if (-not (Test-Path $setup)) { Fail 'Inno Setup did not create the installer.' }

# ------------------------------------------------------------------ 6. done
Step 6 'Done.'
$sizeMb = [math]::Round((Get-Item $setup).Length / 1MB, 1)
Write-Host ''
Write-Host "  Installer: $setup ($sizeMb MB)" -ForegroundColor Green
Write-Host ''
Write-Host '  Copy it to the shop''s laptop and run it there. It includes everything the app needs.'
Write-Host '  The customer must accept the terms of use, and the installed app only runs on that laptop.'
Write-Host '  Windows may show "Windows protected your PC" because the installer is not code-signed:'
Write-Host '  click "More info" and then "Run anyway".'
exit 0
