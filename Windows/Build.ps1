# WhisperKey Build Script
param(
    [ValidateSet("Debug", "Release")]
    [string]$Configuration = "Debug",
    [switch]$Publish,
    [switch]$Run
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$project = "$root\WhisperKey\WhisperKey.csproj"

Write-Host "=== WhisperKey Build ===" -ForegroundColor Cyan

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
