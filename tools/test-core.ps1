$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $outputPath = Join-Path (Get-Location) 'core/build/standalone'
    New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
    $sourceFiles = @(Get-ChildItem core/src/main/java,core/src/test/java -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 8 -encoding UTF-8 -d $outputPath @sourceFiles
    if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
    & java -ea -cp $outputPath dev.cuboidplots.core.CoreContractTest
    if ($LASTEXITCODE -ne 0) { throw 'Core contract tests failed' }
} finally { Pop-Location }
