param([ValidateSet('ftb','opac')][string]$ParentMod)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$taskMods = Join-Path $taskRoot ".work/servers/1.20.1/fabric/$ParentMod/mods"
# Only this project's private server and this addon's known old filenames are touched.
foreach ($taskName in @('cuboidplots-testhost.jar','cuboidplots.jar')) {
    $taskOldJar = Join-Path $taskMods $taskName
    if (Test-Path -LiteralPath $taskOldJar) { Remove-Item -LiteralPath $taskOldJar }
}
Copy-Item -LiteralPath (Join-Path $taskRoot 'fabric/build/libs/cuboidplots-1.20.1-fabric-testhost-0.1.0.jar') -Destination (Join-Path $taskMods 'cuboidplots.jar')
