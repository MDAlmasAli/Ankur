# Compiles and launches the Ankur Playground GUI (a live demo tool -- not part of the
# graded compiler pipeline itself). Run .\build.ps1 first if out/ doesn't exist yet.
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$outDir = Join-Path $root "out"

if (-not (Test-Path $outDir)) {
    Write-Host "out/ not found -- run .\build.ps1 first." -ForegroundColor Red
    exit 1
}

Write-Host "Compiling playground GUI..."
javac -encoding UTF-8 -d $outDir -cp $outDir (Join-Path $root "tools\AnkurPlayground.java")
if ($LASTEXITCODE -ne 0) {
    Write-Host "Playground build failed." -ForegroundColor Red
    exit 1
}

Write-Host "Launching..."
java -cp $outDir tools.AnkurPlayground
