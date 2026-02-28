# WhisperKey Build Script
param(
    [ValidateSet("Debug", "Release")]
    [string]$Configuration = "Debug",
    [switch]$Publish,
    [switch]$Run,
    [switch]$Restart,
    [switch]$Release,
    [string]$Tag
)
set-alias gh 'C:\Program Files\GitHub CLI\gh.exe'
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$project = "$root\WhisperKey\WhisperKey.csproj"

Write-Host "=== WhisperKey Build ===" -ForegroundColor Cyan

# Restart: stop running process and clean publish directory
if ($Restart) {
    Write-Host "`nStopping running WhisperKey process..." -ForegroundColor Yellow
    Stop-Process -Name WhisperKey -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    if (Test-Path "$root\publish") {
        Write-Host "Cleaning publish directory..." -ForegroundColor Yellow
        Remove-Item "$root\publish\*" -Recurse -Force
    }

    $Publish = $true
}

# Bump version and force Release config when -Release is used
if ($Release) {
    $csproj = Get-Content $project -Raw

    if ($csproj -match '<Version>(\d+)\.(\d+)</Version>') {
        $major = [int]$Matches[1]
        $minor = [int]$Matches[2]
    } else {
        Write-Host "ERROR: Could not parse <Version> from WhisperKey.csproj" -ForegroundColor Red
        exit 1
    }

    $oldVersion = "$major.$minor"
    $newMinor = $minor + 1
    $newVersion = "$major.$newMinor"

    Write-Host "`nBumping version: $oldVersion -> $newVersion" -ForegroundColor Yellow

    $csproj = $csproj -replace '<Version>[^<]+</Version>', "<Version>$newVersion</Version>"
    Set-Content $project $csproj -NoNewline

    $Configuration = "Release"
    $Publish = $true
}

# Restore
Write-Host "`nRestoring packages..." -ForegroundColor Yellow
dotnet restore "$root\WhisperKey.sln"

# Build
Write-Host "`nBuilding ($Configuration)..." -ForegroundColor Yellow
dotnet build "$root\WhisperKey.sln" -c $Configuration --no-restore

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild FAILED!" -ForegroundColor Red
    exit 1
}

Write-Host "`nBuild succeeded!" -ForegroundColor Green

# Publish (optional, or forced by -Release)
if ($Publish) {
    Write-Host "`nPublishing..." -ForegroundColor Yellow
    dotnet publish $project `
        -c Release `
        -r win-x64 `
        --self-contained true `
        -p:PublishSingleFile=true `
        -p:IncludeNativeLibrariesForSelfExtract=false `
        -o "$root\publish"

    if ($LASTEXITCODE -ne 0) {
        Write-Host "`nPublish FAILED!" -ForegroundColor Red
        exit 1
    }

    Write-Host "`nPublished to: $root\publish" -ForegroundColor Green
    Write-Host "(Native DLLs are in the runtimes/ folder next to the exe)" -ForegroundColor DarkGray
}

# Run (optional)
if ($Run) {
    $exe = Get-ChildItem "$root\WhisperKey\bin\$Configuration\net8.0-windows\win-x64\WhisperKey.exe" -ErrorAction SilentlyContinue
    if ($exe) {
        Write-Host "`nRunning $($exe.FullName)..." -ForegroundColor Yellow
        Start-Process $exe.FullName
    } else {
        Write-Host "`nCould not find built exe" -ForegroundColor Red
    }
}

# Restart: relaunch from publish directory
if ($Restart) {
    $publishExe = "$root\publish\WhisperKey.exe"
    if (Test-Path $publishExe) {
        Write-Host "`nLaunching WhisperKey from publish directory..." -ForegroundColor Yellow
        Start-Process $publishExe
        Write-Host "WhisperKey launched!" -ForegroundColor Green
    } else {
        Write-Host "`nCould not find published exe at $publishExe" -ForegroundColor Red
    }
}

# Create GitHub release
if ($Release) {
    # Verify gh CLI is available
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: GitHub CLI (gh) is not installed. Install from https://cli.github.com/" -ForegroundColor Red
        exit 1
    }

    # Zip the publish folder
    $zipName = "WhisperKey-Windows-x64.zip"
    $zipPath = "$root\$zipName"
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

    Write-Host "`nCreating release archive..." -ForegroundColor Yellow
    Compress-Archive -Path "$root\publish\*" -DestinationPath $zipPath
    Write-Host "Archive: $zipPath" -ForegroundColor DarkGray

    # Determine tag
    if (-not $Tag) {
        $Tag = "windows-v$newVersion"
    }

    Write-Host "`nCreating GitHub release: $Tag" -ForegroundColor Yellow

    # Check if release already exists
    $ErrorActionPreference = "Continue"
    gh release view $Tag 2>$null | Out-Null
    $ErrorActionPreference = "Stop"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "Release $Tag already exists. Uploading archive to existing release..." -ForegroundColor Yellow
        gh release upload $Tag $zipPath --clobber
    } else {
        Write-Host "Creating new release $Tag..." -ForegroundColor Yellow
        gh release create $Tag $zipPath `
            --title "WhisperKey Windows $Tag" `
            --notes "WhisperKey Windows Release build" `
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
