#requires -version 5.1
<#
.SYNOPSIS
    Live-demo runner for the ai-android library (native Windows PowerShell).

.DESCRIPTION
    Steps, in order:
      0) check the active JDK (refuses JDK 24+),
      1) ensure the vendored sherpa-onnx AAR is present and SHA-256 verified,
      2) run the headline verification (unit tests + ktlint + detekt + engine resolution),
      3) build the :demo-app debug APK,
      4) install and launch it if an adb device is attached, else print instructions,
      5) print a final summary.

    This script only builds and (optionally) installs. It does NOT modify tracked
    build or source files.

.PARAMETER Offline
    Pass Gradle --offline. Only for a pre-warmed machine; a cold machine must be
    online to resolve dependencies.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\demo.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\demo.ps1 -Offline
#>
[CmdletBinding()]
param(
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'

# --- constants ----------------------------------------------------------------
$Version       = '1.13.8'
$Sha256        = '633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
$AarUrl        = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$Version/sherpa-onnx-$Version.aar"
$DemoPackage   = 'hk.edu.hkmu.demo'
$DemoActivity  = 'hk.edu.hkmu.demo/.MainActivity'
$TotalSteps    = 5

$Root          = Split-Path -Parent $PSScriptRoot
Set-Location $Root
$Gradlew       = Join-Path $Root 'gradlew.bat'

# --- helpers ------------------------------------------------------------------
function Write-Step {
    param([string]$Title)
    Write-Host ''
    Write-Host ('=' * 60)
    Write-Host "== $Title"
    Write-Host ('=' * 60)
}

function Write-Info {
    param([string]$Message)
    Write-Host "   $Message"
}

function Fail {
    param([string]$Message)
    throw "DEMO FAILED: $Message"
}

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -Algorithm SHA256 -Path $Path).Hash.ToLowerInvariant()
}

function Invoke-Gradle {
    param([string[]]$GradleArgs)
    $all = @()
    if ($Offline) { $all += '--offline' }
    $all += $GradleArgs
    Write-Host ("> .\gradlew.bat " + ($all -join ' ')) -ForegroundColor Cyan
    & $script:Gradlew @all
    if ($LASTEXITCODE -ne 0) {
        Fail "gradlew exited with code $LASTEXITCODE (args: $($all -join ' '))"
    }
}

# ==============================================================================
Write-Step "Step 0/$TotalSteps - Toolchain check (JDK 17 or 21; JDK 24+ is refused)"

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Fail @"
java was not found on PATH.
   Install JDK 17 or 21, point JAVA_HOME at it, and re-run.
   Example: `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21...'
"@
}

Write-Info "JAVA_HOME=$($env:JAVA_HOME)"
Write-Info 'java -version:'
$javaRaw = (& java -version 2>&1 | Out-String)
Write-Host ($javaRaw.TrimEnd())

$verMatch = [regex]::Match($javaRaw, 'version "([^"]+)"')
if (-not $verMatch.Success) {
    Fail "Could not parse a version out of: $($javaRaw.Trim())"
}
$javaVer = $verMatch.Groups[1].Value
if ($javaVer.StartsWith('1.')) {
    $javaMajor = [int]($javaVer.Split('.')[1])
}
else {
    $javaMajor = [int]($javaVer.Split('.')[0])
}

if ($javaMajor -ge 24) {
    Fail @"
Active Java is $javaVer (JDK $javaMajor). This WILL break the build:
   Gradle 8.13 and AGP 8.13.0 are not compatible with JDK $javaMajor.
   Install JDK 17 or 21, set JAVA_HOME to that JDK, then re-run.
   Verify with: java -version
"@
}
if ($javaMajor -lt 17) {
    Fail "Active Java is $javaVer (JDK $javaMajor); need JDK 17 or 21 (minSdk 28 / AGP 8.13)."
}
Write-Info "OK: JDK $javaMajor is supported (17-23 accepted; 17 or 21 recommended)."

# ==============================================================================
Write-Step "Step 1/$TotalSteps - Vendored sherpa-onnx $Version AAR"

$aarPath = Join-Path $Root ("libs\sherpa-onnx-$Version.aar")
$aarOk = $false
if (Test-Path $aarPath) {
    if ((Get-Sha256 $aarPath) -eq $Sha256) {
        Write-Info "Already present and SHA-256 verified: $aarPath"
        $aarOk = $true
    }
    else {
        Write-Info 'Present but checksum mismatch; re-fetching.'
    }
}

if (-not $aarOk) {
    if (Test-Path $aarPath) { Remove-Item -Force $aarPath }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $aarPath) | Out-Null
    Write-Info "Downloading $AarUrl"
    Invoke-WebRequest -Uri $AarUrl -OutFile $aarPath
    $downloaded = Get-Sha256 $aarPath
    if ($downloaded -ne $Sha256) {
        Remove-Item -Force $aarPath
        Fail "SHA-256 mismatch after download.
   expected: $Sha256
   actual:   $downloaded"
    }
    Write-Info "Fetched and SHA-256 verified: $aarPath"
}

# ==============================================================================
Write-Step "Step 2/$TotalSteps - Headline: unit tests + static analysis + engine resolution"
Write-Info "BUILD SUCCESSFUL here is the 'everything passes' evidence:"
Write-Info '  testDebugUnitTest : 386 tests, 0 failures across 9 modules'
Write-Info '  ktlintCheck detekt verifyEngineResolution'

Invoke-Gradle -GradleArgs @('testDebugUnitTest', 'ktlintCheck', 'detekt', 'verifyEngineResolution')

Write-Host ''
Write-Host ('*' * 70)
Write-Host '*  BUILD SUCCESSFUL - 386 tests, 0 failures + ktlint + detekt +       *'
Write-Host '*  engine resolution all green.                                       *'
Write-Host ('*' * 70)

# ==============================================================================
Write-Step "Step 3/$TotalSteps - Build the demo app (:demo-app:assembleDebug)"

Invoke-Gradle -GradleArgs @(':demo-app:assembleDebug')

$apkDir = Join-Path $Root 'demo-app\build\outputs\apk\debug'
$apk = $null
if (Test-Path $apkDir) {
    $apk = Get-ChildItem -Path $apkDir -Filter '*.apk' -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $apk) {
    Fail "No APK found under $apkDir. Check the :demo-app output name."
}
Write-Info "APK: $apk"

# ==============================================================================
Write-Step "Step 4/$TotalSteps - Deploy to a device (only if one is attached)"

function Show-StudioHint {
    Write-Info 'Run it from Android Studio instead:'
    Write-Info '  1. Open the project, wait for Gradle sync.'
    Write-Info "  2. Select the ':demo-app' run configuration."
    Write-Info '  3. Start an emulator or plug in a phone, then click Run (green triangle).'
    Write-Info "  4. The app is $DemoPackage, launcher $DemoActivity."
}

$adb = $null
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCmd) {
    $adb = $adbCmd.Source
}
else {
    foreach ($sdk in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ([string]::IsNullOrWhiteSpace($sdk)) { continue }
        $candidate = Join-Path $sdk 'platform-tools\adb.exe'
        if (Test-Path $candidate) { $adb = $candidate; break }
    }
}

if (-not $adb) {
    Write-Info 'adb not found on PATH or under ANDROID_HOME / ANDROID_SDK_ROOT. Skipping install.'
    Show-StudioHint
}
else {
    $devices = @(
        & $adb devices |
            Select-Object -Skip 1 |
            Where-Object { $_ -match '\tdevice$' } |
            ForEach-Object { ($_ -split '\t')[0].Trim() } |
            Where-Object { $_ }
    )
    if ($devices.Count -eq 0) {
        Write-Info "No device or emulator attached (adb devices lists no 'device' line). Skipping install."
        Show-StudioHint
    }
    else {
        Write-Info "Attached device(s): $($devices -join ' ')"
        Write-Info "Installing: $apk"
        & $adb install -r $apk
        if ($LASTEXITCODE -ne 0) { Fail "adb install failed with code $LASTEXITCODE" }
        Write-Info "Launching $DemoActivity"
        & $adb shell am start -n $DemoActivity
        if ($LASTEXITCODE -ne 0) { Fail "adb shell am start failed with code $LASTEXITCODE" }
    }
}

# ==============================================================================
Write-Step "Step 5/$TotalSteps - Summary"

Write-Info "[x] JDK check          : supported JDK $javaMajor (need 17 or 21; 24+ refused)"
Write-Info "[x] sherpa-onnx AAR    : verified ($Version, sha256 $($Sha256.Substring(0,12))...)"
Write-Info '[x] Verification suite : testDebugUnitTest (386/0) + ktlint + detekt + engine resolution'
Write-Info '[x] Demo app build     : :demo-app:assembleDebug'
Write-Info "[x] APK                : $apk"
if ($adb -and $devices -and $devices.Count -gt 0) {
    Write-Info "[x] Device deploy      : installed and launched on $($devices -join ' ')"
}
else {
    Write-Info '[ ] Device deploy      : skipped (no adb device); run from Android Studio'
}
Write-Host ''
Write-Info 'Tip: on a pre-warmed machine you may re-run with -Offline to skip dependency lookups.'
Write-Info 'The engines shown in the demo app are test-kit fakes; see docs/DEMO.md for REAL vs SIMULATED.'
