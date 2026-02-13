# WhisperKey Build Script
param(
    [ValidateSet("Debug", "Release")]
    [string]$Configuration = "Debug",
    [switch]$Publish,
    [switch]$Run,
    [switch]$Restart
)

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

# Publish (optional)
if ($Publish) {
    Write-Host "`nPublishing..." -ForegroundColor Yellow
    dotnet publish $project `
        -c Release `
        -r win-x64 `
        --self-contained true `
        -p:PublishSingleFile=true `
        -p:IncludeNativeLibrariesForSelfExtract=false `
        -o "$root\publish"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`nPublished to: $root\publish" -ForegroundColor Green
        Write-Host "(Native DLLs are in the runtimes/ folder next to the exe)" -ForegroundColor DarkGray
    }
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
