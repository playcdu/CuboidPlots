param([switch]$TestHost)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $env:JAVA_HOME = Get-ChildItem .work/toolchains/jdk17 -Directory | Select-Object -First 1 -ExpandProperty FullName
    $env:GRADLE_USER_HOME = Join-Path (Get-Location) '.work/forge-gradle-cache'
    $taskTemporaryDirectory = (Join-Path (Get-Location) '.work/tmp').Replace('\','/')
    $env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$taskTemporaryDirectory"
    $buildArguments = @('-p','forge','build','jarJar','--console=plain','--no-daemon')
    if ($TestHost) { $buildArguments += '-PtestHost' }
    & .work/toolchains/gradle8/gradle-8.14.3/bin/gradle.bat @buildArguments
    if ($LASTEXITCODE -ne 0) { throw 'Forge build failed' }
} finally { Pop-Location }
