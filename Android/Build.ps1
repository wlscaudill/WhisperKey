# WhisperKey Android Build Script
param(
    [ValidateSet("Debug", "Release")]
    [string]$Configuration = "Release",
    [switch]$Release,
    [string]$Tag
)
set-alias gh 'C:\Program Files\GitHub CLI\gh.exe'
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$buildGradlePath = "$root\app\build.gradle.kts"

Write-Host "=== WhisperKey Android Build ===" -ForegroundColor Cyan

# Verify gradlew.bat exists
$gradlew = "$root\gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Host "ERROR: gradlew.bat not found at $gradlew" -ForegroundColor Red
    exit 1
}

# Bump minor version when releasing
if ($Release) {
    $buildGradle = Get-Content $buildGradlePath -Raw

    # Read current versionName (e.g. "1.3")
    if ($buildGradle -match 'versionName\s*=\s*"(\d+)\.(\d+)"') {
        $major = [int]$Matches[1]
        $minor = [int]$Matches[2]
    } else {
        Write-Host "ERROR: Could not parse versionName from build.gradle.kts" -ForegroundColor Red
        exit 1
    }

    # Read current versionCode
    if ($buildGradle -match 'versionCode\s*=\s*(\d+)') {
        $versionCode = [int]$Matches[1]
    } else {
        Write-Host "ERROR: Could not parse versionCode from build.gradle.kts" -ForegroundColor Red
        exit 1
    }

    $oldVersion = "$major.$minor"
    $newMinor = $minor + 1
    $newVersion = "$major.$newMinor"
    $newVersionCode = $versionCode + 1

    Write-Host "`nBumping version: $oldVersion -> $newVersion (versionCode: $versionCode -> $newVersionCode)" -ForegroundColor Yellow

    $buildGradle = $buildGradle -replace "versionCode\s*=\s*\d+", "versionCode = $newVersionCode"
    $buildGradle = $buildGradle -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$newVersion`""
    Set-Content $buildGradlePath $buildGradle -NoNewline
}

# Build
$task = if ($Configuration -eq "Release") { "assembleRelease" } else { "assembleDebug" }
Write-Host "`nBuilding ($Configuration)..." -ForegroundColor Yellow
& $gradlew $task
if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild FAILED!" -ForegroundColor Red
    exit 1
}

# Locate the APK
$configLower = $Configuration.ToLower()
$apkDir = "$root\app\build\outputs\apk\$configLower"
$apk = Get-ChildItem "$apkDir\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $apk) {
    Write-Host "ERROR: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

Write-Host "`nBuild succeeded!" -ForegroundColor Green
Write-Host "APK: $($apk.FullName)" -ForegroundColor DarkGray

# Upload to GitHub release (optional)
if ($Release) {
    # Verify gh CLI is available
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: GitHub CLI (gh) is not installed. Install from https://cli.github.com/" -ForegroundColor Red
        exit 1
    }

    # Determine tag from the (already bumped) version
    if (-not $Tag) {
        $Tag = "android-v$newVersion"
    }

    Write-Host "`nCreating GitHub release: $Tag" -ForegroundColor Yellow

    # Check if release already exists
    $ErrorActionPreference = "Continue"
    gh release view $Tag 2>$null | Out-Null
    $ErrorActionPreference = "Stop"
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Release $Tag already exists. Uploading APK to existing release..." -ForegroundColor Yellow
        gh release upload $Tag $apk.FullName --clobber
    } else {
        Write-Host "Creating new release $Tag..." -ForegroundColor Yellow
        gh release create $Tag $apk.FullName `
            --title "WhisperKey Android $Tag" `
            --notes "WhisperKey Android $Configuration build" `
            --latest
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`nRelease uploaded!" -ForegroundColor Green
        gh release view $Tag --web
    } else {
        Write-Host "`nRelease upload FAILED!" -ForegroundColor Red
        exit 1
    }
}
