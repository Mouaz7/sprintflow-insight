param()

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$frontend = Join-Path $root 'frontend'
$backend = Join-Path $root 'backend'
$environmentFile = Join-Path $root '.env'
$env:SPRING_PROFILES_ACTIVE = 'dev'

function New-LocalDatabasePassword {
    $characters = ('abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789').ToCharArray()
    -join (1..32 | ForEach-Object { $characters | Get-Random })
}

function Import-EnvironmentFile([string]$path) {
    Get-Content -LiteralPath $path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line -match '^([^=]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}

if (-not (Test-Path -LiteralPath $environmentFile)) {
    $template = Get-Content (Join-Path $root '.env.example') -Raw
    $template = $template -replace '(?m)^SPRINTFLOW_DB_PASSWORD=\r?$', "SPRINTFLOW_DB_PASSWORD=$(New-LocalDatabasePassword)"
    $template = $template -replace '(?m)^SPRINTFLOW_COOKIE_SECURE=.*$', 'SPRINTFLOW_COOKIE_SECURE=false'
    [IO.File]::WriteAllText($environmentFile, $template, [Text.UTF8Encoding]::new($false))
    Write-Host 'Created .env with a local PostgreSQL password.'
}
# Generated once, never replaced: a new key would make existing encrypted values unreadable (ADR 0011).
$settings = [IO.File]::ReadAllText($environmentFile)
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
Import-EnvironmentFile $environmentFile

$npmCommand = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
if (-not $npmCommand -and (Test-Path 'C:\Program Files\nodejs\npm.cmd')) {
    $npmCommand = 'C:\Program Files\nodejs\npm.cmd'
}
if (-not $npmCommand) {
    Write-Error 'Node.js 24 or later with npm is required. Install Node.js LTS, then run this script again.'
}

$dockerCommand = (Get-Command docker.exe -ErrorAction SilentlyContinue).Source
if (-not $dockerCommand) {
    $dockerCommand = (Get-Command docker -ErrorAction SilentlyContinue).Source
}
if (-not $dockerCommand -and (Test-Path 'C:\Program Files\Docker\Docker\resources\bin\docker.exe')) {
    $dockerCommand = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
}
$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\java.exe'))) {
    $javaHome = $null
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $javaHome = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
    }
}
if (-not $javaHome) {
    # Also support the JDK supplied by the Java extension in VS Code on this machine.
    $taskJavaExtensions = Join-Path $env:USERPROFILE '.vscode\extensions'
    if (Test-Path -LiteralPath $taskJavaExtensions) {
        $taskJavaCandidates = Get-ChildItem -LiteralPath $taskJavaExtensions -Directory -Filter 'redhat.java-*' |
            Sort-Object LastWriteTime -Descending
        foreach ($taskJavaExtension in $taskJavaCandidates) {
            $taskJavaRuntimeDirectory = Join-Path $taskJavaExtension.FullName 'jre'
            if (Test-Path -LiteralPath $taskJavaRuntimeDirectory) {
                $taskJavaRuntime = Get-ChildItem -LiteralPath $taskJavaRuntimeDirectory -Directory |
                    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } |
                    Select-Object -First 1
                if ($taskJavaRuntime) { $javaHome = $taskJavaRuntime.FullName; break }
            }
        }
    }
}
if (-not $javaHome) {
    Write-Error 'Java 25 is required. Install a JDK or configure JAVA_HOME, then run this script again.'
}

$taskJavaVersion = & (Join-Path $javaHome 'bin\java.exe') -version 2>&1 | Out-String
if ($taskJavaVersion -notmatch 'version "25(?:\.|")') {
    Write-Error 'This release requires Java 25. Set JAVA_HOME to a Java 25 JDK and run again.'
}

$env:JAVA_HOME = $javaHome
$env:PATH = "$(Join-Path $javaHome 'bin');$env:PATH"

Push-Location $root
try {
    $dockerReady = $false
    if ($dockerCommand) {
        try {
            $null = & $dockerCommand info --format '{{.ServerVersion}}' 2>$null
            $dockerReady = $LASTEXITCODE -eq 0
        }
        catch {
            $dockerReady = $false
        }
    }

    if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
        & $npmCommand --prefix $frontend install
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    }
    & $npmCommand --prefix $frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }

    if (-not $dockerReady -and $dockerCommand) {
        $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
        if (Test-Path $dockerDesktop) {
            Write-Host 'Starting Docker Desktop and waiting for the local database service...'
            Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
            for ($attempt = 1; $attempt -le 18; $attempt++) {
                Start-Sleep -Seconds 5
                try {
                    $null = & $dockerCommand info --format '{{.ServerVersion}}' 2>$null
                    if ($LASTEXITCODE -eq 0) {
                        $dockerReady = $true
                        break
                    }
                }
                catch { }
            }
        }
    }

    if (-not $dockerReady) {
        $wslCommand = Get-Command wsl.exe -ErrorAction SilentlyContinue
        if (-not $wslCommand) {
            throw 'Docker is unavailable and WSL is not installed. Start Docker Desktop, then run .\start.cmd again.'
        }

        & $wslCommand.Source -d Ubuntu -u root -- pg_isready -h 127.0.0.1 -p 5432
        if ($LASTEXITCODE -ne 0) {
            throw 'Docker is unavailable and PostgreSQL is not running in WSL. Start PostgreSQL in WSL, then run .\start.cmd again.'
        }

        Write-Host 'Docker is unavailable. Starting Spring Boot on Windows with local WSL PostgreSQL at http://localhost:8081/'
        Push-Location $backend
        & (Join-Path $backend 'mvnw.cmd') spring-boot:run
        exit $LASTEXITCODE
    }

    & $dockerCommand compose --env-file $environmentFile stop app
    if ($LASTEXITCODE -ne 0) { throw 'Could not stop Docker app.' }
    & $dockerCommand compose --env-file $environmentFile up -d --wait postgres
    if ($LASTEXITCODE -ne 0) { throw 'Could not start PostgreSQL with Docker Compose.' }
    $existingServer = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($existingServer) {
        $existingProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($existingServer.OwningProcess)" -ErrorAction SilentlyContinue
        $isSprintFlowProcess = $false
        if ($existingProcess -and $existingProcess.Name -eq 'java.exe' -and $existingProcess.CommandLine.Contains('com.sprintflowinsight.SprintFlowApplication')) {
            $argfilePart = $existingProcess.CommandLine.Split('@', 2)[1]
            $argfilePath = $argfilePart.Split(' ', 2)[0].Trim('"')
            if (Test-Path -LiteralPath $argfilePath) {
                $slash = [string][char]92
                $classpath = (Get-Content -LiteralPath $argfilePath -Raw).Replace("$slash$slash", $slash)
                $isSprintFlowProcess = $classpath.Contains((Join-Path $backend 'target\classes'))
            }
        }

        if (-not $isSprintFlowProcess) {
            throw 'Port 8081 is already used by another application. Close that application, then run .\start.cmd again.'
        }

        Write-Host 'Restarting the existing SprintFlow server to load the latest frontend and backend changes...'
        Stop-Process -Id $existingServer.OwningProcess -Force
        for ($attempt = 1; $attempt -le 20; $attempt++) {
            Start-Sleep -Milliseconds 500
            $stillListening = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            if (-not $stillListening) { break }
        }
        if ($stillListening) { throw 'The old SprintFlow server did not release port 8081.' }
    }

    Push-Location $backend
    & (Join-Path $backend 'mvnw.cmd') spring-boot:run
    exit $LASTEXITCODE
}
finally {
    if ((Get-Location).Path -eq $backend) { Pop-Location }
    Pop-Location
}
