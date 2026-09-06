param([ValidateSet('ftb','opac')][string]$ParentMod)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path $PSScriptRoot -Parent) ".work/servers/1.20.1/fabric/$ParentMod/world"))
$taskAddon = [IO.Path]::GetFullPath((Join-Path $taskRoot 'cuboidplots'))
if (-not $taskAddon.StartsWith($taskRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Test cleanup escaped the private test world' }
if (Test-Path -LiteralPath $taskAddon) { Remove-Item -LiteralPath $taskAddon -Recurse -Force }
$taskReport = Join-Path $taskRoot 'cuboidplots-acceptance.txt'
if (Test-Path -LiteralPath $taskReport) { Move-Item -LiteralPath $taskReport -Destination (Join-Path $taskRoot ("previous-acceptance-" + [DateTime]::UtcNow.Ticks + '.txt')) }
New-Item -ItemType Directory -Path $taskAddon -Force | Out-Null
Set-Content -LiteralPath (Join-Path $taskAddon 'config.properties') -Value "provider=config`ndefault.max_regions=32`ndefault.max_volume=1000000"
