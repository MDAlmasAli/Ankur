# Builds and runs the whole test suite (lexer, parser, semantic analyzer, TAC, and all three
# code generators).
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$outDir = Join-Path $root "out"

& (Join-Path $root "build.ps1")
# A child script's `exit 1` does not stop this one, so a failed build would otherwise run the
# previous build's classes and report stale results.
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

java -cp $outDir ankur.tests.TestRunner
exit $LASTEXITCODE
