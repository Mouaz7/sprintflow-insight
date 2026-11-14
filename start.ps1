param([switch]$Native)
$ErrorActionPreference = 'Stop'
$launcher = if ($Native) { 'scripts/start-native.ps1' } else { 'scripts/start-docker.ps1' }
& (Join-Path $PSScriptRoot $launcher)
exit $LASTEXITCODE
