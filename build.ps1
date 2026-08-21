# Compiles every .java file under src/ and test/ into out/ (no Maven on this machine).
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$outDir = Join-Path $root "out"

if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}

$sources = Get-ChildItem -Path (Join-Path $root "src"), (Join-Path $root "test") -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName }

Write-Host "Compiling $($sources.Count) source files..."
javac -encoding UTF-8 -d $outDir $sources

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit 1
}

Write-Host "Build succeeded -> $outDir" -ForegroundColor Green
