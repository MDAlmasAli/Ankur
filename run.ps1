# Runs the Ankur compiler front end against a source file, e.g. .\run.ps1 examples\hello.ank
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$outDir = Join-Path $root "out"

if (-not (Test-Path $outDir)) {
    Write-Host "No build found. Running build.ps1 first..."
    & (Join-Path $root "build.ps1")
}

if ($args.Count -lt 1) {
    Write-Host "Usage: .\run.ps1 <path-to-.ank-file>"
    Write-Host "Example: .\run.ps1 examples\hello.ank"
    exit 1
}

java -cp $outDir ankur.Main $args[0]
