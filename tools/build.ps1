param([switch]$TestHost)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $env:JAVA_HOME = Get-ChildItem .work/toolchains/jdk21 -Directory | Select-Object -First 1 -ExpandProperty FullName
    $env:GRADLE_USER_HOME = Join-Path (Get-Location) '.work/gradle-cache'
    $taskTemporaryDirectory = (Join-Path (Get-Location) '.work/tmp').Replace('\','/')
    $env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$taskTemporaryDirectory -Dfabric.loom.ci=true"
    $buildArguments = @('build','--console=plain','--no-daemon')
    if ($TestHost) { $buildArguments += '-PtestHost' }
    & .work/toolchains/gradle8/gradle-8.14.3/bin/gradle.bat @buildArguments
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
} finally { Pop-Location }
