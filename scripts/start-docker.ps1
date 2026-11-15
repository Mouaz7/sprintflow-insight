param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'env-bootstrap.ps1')
$environmentFile = Initialize-SprintFlowEnvironment $root
$appPort = Read-SprintFlowPort $environmentFile 'SPRINTFLOW_APP_PORT' 8081
$null = Read-SprintFlowPort $environmentFile 'SPRINTFLOW_DB_PORT' 5432
$docker = (Get-Command docker.exe -ErrorAction SilentlyContinue).Source
if (-not $docker) { $docker = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe' }
if (-not (Test-Path -LiteralPath $docker)) { throw 'Install Docker Desktop, enable Linux containers, then run .\start.cmd again.' }
function Test-DockerReady {
    # Windows PowerShell can treat native stderr as a terminating error.
    try {
        $null = & $docker info --format '{{.ServerVersion}}' 2>&1
        return $LASTEXITCODE -eq 0
    } catch { return $false }
}
if (-not (Test-DockerReady)) {
    $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $dockerDesktop)) { throw 'Docker Desktop is installed incorrectly. Reinstall it, then run .\start.cmd again.' }
    Write-Host 'Starting Docker Desktop...'
    Start-Process -FilePath $dockerDesktop
    $dockerReady = $false
    for ($attempt = 1; $attempt -le 36; $attempt++) {
        Start-Sleep -Seconds 5
        Write-Host "Waiting for Docker Desktop ($attempt/36)..."
        if (Test-DockerReady) {
            $dockerReady = $true
            break
        }
    }
    if (-not $dockerReady) { throw 'Docker Desktop did not become ready within three minutes. Open Docker Desktop and check its status, then run .\start.cmd again.' }
}
& $docker compose version
if ($LASTEXITCODE -ne 0) { throw 'Update Docker Desktop: Docker Compose v2 is required.' }
$backend = Join-Path $root 'backend'
$listener = Get-NetTCPConnection -LocalPort $appPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if ($process.Name -eq 'java.exe' -and $process.CommandLine.Contains('com.sprintflowinsight.SprintFlowApplication')) {
        $argument = [regex]::Match($process.CommandLine, '@"?([^" ]+)').Groups[1].Value
        $owned = $false
        if ($argument -and (Test-Path -LiteralPath $argument)) {
            $classpath = [IO.File]::ReadAllText($argument).Replace('\\', '\')
            $owned = $classpath.Contains((Join-Path $backend 'target\classes'))
        }
        if (-not $owned) { throw "Port $appPort belongs to another Java application. Close it before starting SprintFlow." }
        Stop-Process -Id $process.ProcessId -Force
        Start-Sleep -Seconds 2
    }
}
Push-Location $root
try {
    Write-Host 'Building and starting SprintFlow. The first build downloads dependencies and can take several minutes.'
    & $docker compose --env-file $environmentFile up -d --build --wait --wait-timeout 180
    if ($LASTEXITCODE -ne 0) {
        & $docker compose --env-file $environmentFile logs --tail 80 app postgres
        throw 'Docker startup failed. See the logs above.'
    }
    Write-Host "SprintFlow is ready at http://localhost:$appPort. You may close this window."
} finally { Pop-Location }
