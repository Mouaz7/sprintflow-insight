# Shared .env bootstrap for start-docker.ps1 and start-native.ps1.
# Dot-source it, then call Initialize-SprintFlowEnvironment. The file mirrors
# scripts/env-bootstrap.sh; .env is parsed as KEY=VALUE lines, never executed.

function New-SprintFlowDatabasePassword {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    # Hex keeps the value safe in .env, JDBC and Compose interpolation alike.
    -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

function Initialize-SprintFlowEnvironment([string]$Root) {
    $environmentFile = Join-Path $Root '.env'
    if (-not (Test-Path -LiteralPath $environmentFile)) {
        $template = [IO.File]::ReadAllText((Join-Path $Root '.env.example'))
        # Local starts serve plain HTTP on localhost, so the template's secure-cookie default must be relaxed.
        $template = $template -replace '(?m)^SPRINTFLOW_COOKIE_SECURE=.*$', 'SPRINTFLOW_COOKIE_SECURE=false'
        [IO.File]::WriteAllText($environmentFile, $template, [Text.UTF8Encoding]::new($false))
        Write-Host 'Created private .env settings. Keep this file for future starts.'
    }
    $settings = [IO.File]::ReadAllText($environmentFile)
    if ($settings -notmatch '(?m)^SPRINTFLOW_DB_PASSWORD=[^\r\n]+\r?$') {
        $passwordLine = 'SPRINTFLOW_DB_PASSWORD=' + (New-SprintFlowDatabasePassword)
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
        $bytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes) } finally { $random.Dispose() }
        $keyLine = 'SPRINTFLOW_ENCRYPTION_KEY=' + [Convert]::ToBase64String($bytes)
        if ($settings -match '(?m)^SPRINTFLOW_ENCRYPTION_KEY=\r?$') {
            $settings = $settings -replace '(?m)^SPRINTFLOW_ENCRYPTION_KEY=\r?$', $keyLine
        } else {
            $settings = $settings.TrimEnd() + "`n" + $keyLine + "`n"
        }
        [IO.File]::WriteAllText($environmentFile, $settings, [Text.UTF8Encoding]::new($false))
        Write-Host 'Generated a private encryption key in .env. Keep this file (and a backup of it) for future starts.'
    }
    New-Item -ItemType Directory -Force (Join-Path $Root 'backend\data') | Out-Null
    return $environmentFile
}

function Read-SprintFlowSetting([string]$EnvironmentFile, [string]$Name, [string]$Default) {
    $settings = [IO.File]::ReadAllText($EnvironmentFile)
    $found = [regex]::Matches($settings, '(?m)^' + [regex]::Escape($Name) + '=([^\r\n]*)\r?$')
    if ($found.Count -eq 0 -or -not $found[$found.Count - 1].Groups[1].Value) { return $Default }
    return $found[$found.Count - 1].Groups[1].Value
}

function Read-SprintFlowPort([string]$EnvironmentFile, [string]$Name, [int]$Default) {
    $value = Read-SprintFlowSetting $EnvironmentFile $Name ([string]$Default)
    if ($value -notmatch '^[0-9]{1,5}$' -or [int]$value -lt 1 -or [int]$value -gt 65535) {
        throw "$Name must be between 1 and 65535."
    }
    return [int]$value
}

function Import-SprintFlowEnvironment([string]$EnvironmentFile) {
    Get-Content -LiteralPath $EnvironmentFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line -match '^([^=]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}
