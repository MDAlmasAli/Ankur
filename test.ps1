# Builds and runs the whole test suite (LexerTest, ParserTest, SemanticAnalyzerTest).
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$outDir = Join-Path $root "out"

& (Join-Path $root "build.ps1")

java -cp $outDir ankur.tests.TestRunner
exit $LASTEXITCODE
