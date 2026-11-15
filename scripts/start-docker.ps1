param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$environmentFile = Join-Path $root '.env'
function New-LocalDatabasePassword {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    [Convert]::ToBase64String($bytes)
}
if (-not (Test-Path -LiteralPath $environmentFile)) {
    $password = New-LocalDatabasePassword
    $template = [IO.File]::ReadAllText((Join-Path $root '.env.example'))
    $template = $template -replace '(?m)^SPRINTFLOW_DB_PASSWORD=\r?$', ('SPRINTFLOW_DB_PASSWORD=' + $password)
    $template = $template -replace '(?m)^SPRINTFLOW_COOKIE_SECURE=.*$', 'SPRINTFLOW_COOKIE_SECURE=false'
    [IO.File]::WriteAllText($environmentFile, $template, [Text.UTF8Encoding]::new($false))
    Write-Host 'Created private .env settings. Keep this file for future starts.'
}
$settings = [IO.File]::ReadAllText($environmentFile)
if ($settings -notmatch '(?m)^SPRINTFLOW_DB_PASSWORD=[^\r\n]+\r?$') {
    $passwordLine = 'SPRINTFLOW_DB_PASSWORD=' + (New-LocalDatabasePassword)
    if ($settings -match '(?m)^SPRINTFLOW_DB_PASSWORD=\r?$') {
        $settings = $settings -replace '(?m)^SPRINTFLOW_DB_PASSWORD=\r?$', $passwordLine
    } else {
        $settings = $settings.TrimEnd() + "`n" + $passwordLine + "`n"
    }
    [IO.File]::WriteAllText($environmentFile, $settings, [Text.UTF8Encoding]::new($false))
    Write-Host 'Generated a private database password in .env. Keep this file for future starts.'
}
# Generated once, never replaced: a new key would make existing encrypted values unreadable (ADR 0015).
if ($settings -notmatch '(?m)^SPRINTFLOW_ENCRYPTION_KEY=[^\r\n]+\r?$') {
    $keyLine = 'SPRINTFLOW_ENCRYPTION_KEY=' + (New-LocalDatabasePassword)
    if ($settings -match '(?m)^SPRINTFLOW_ENCRYPTION_KEY=\r?$') {
        $settings = $settings -replace '(?m)^SPRINTFLOW_ENCRYPTION_KEY=\r?$', $keyLine
    } else {
        $settings = $settings.TrimEnd() + "`n" + $keyLine + "`n"
    }
    [IO.File]::WriteAllText($environmentFile, $settings, [Text.UTF8Encoding]::new($false))
    Write-Host 'Generated a private encryption key in .env. Keep this file (and a backup of it) for future starts.'
}
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
function Read-LocalPort([string]$name, [int]$defaultPort) {
    $match = [regex]::Match($settings, '(?m)^' + [regex]::Escape($name) + '=([^\r\n]*)\r?$')
    if (-not $match.Success -or -not $match.Groups[1].Value) { return $defaultPort }
    $value = $match.Groups[1].Value
    if ($value -notmatch '^[0-9]{1,5}$' -or [int]$value -lt 1 -or [int]$value -gt 65535) {
        throw "$name must be between 1 and 65535."
    }
    return [int]$value
}
$appPort = Read-LocalPort 'SPRINTFLOW_APP_PORT' 8081
$null = Read-LocalPort 'SPRINTFLOW_DB_PORT' 5432
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
        if (-not $owned) { throw 'Port 8081 belongs to another Java application. Close it before starting SprintFlow.' }
        Stop-Process -Id $process.ProcessId -Force
        Start-Sleep -Seconds 2
    }
}
New-Item -ItemType Directory -Force (Join-Path $backend 'data') | Out-Null
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
