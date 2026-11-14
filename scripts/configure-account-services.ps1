$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$taskEnvironmentPath = Join-Path $taskRoot '.env'
if (-not (Test-Path -LiteralPath $taskEnvironmentPath)) { throw 'Run start.cmd once to create the local .env file first.' }
$taskEnvironment = [IO.File]::ReadAllText($taskEnvironmentPath)
function Set-TaskSetting([string]$name, [string]$value) {
    if ($value.Contains("`n") -or $value.Contains("`r")) { throw 'Settings cannot contain newlines.' }
    $pattern = '(?m)^' + [regex]::Escape($name) + '=.*$'
    $line = $name + '=' + $value
    if ([regex]::IsMatch($script:taskEnvironment, $pattern)) {
        $script:taskEnvironment = [regex]::Replace($script:taskEnvironment, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $line })
    } else { $script:taskEnvironment += "`r`n" + $line }
}
$taskGmail = Read-Host 'Gmail sender address (Enter keeps the existing address)'
if ([string]::IsNullOrWhiteSpace($taskGmail)) { $taskGmail = [regex]::Match($taskEnvironment, '(?m)^SPRINTFLOW_SMTP_USERNAME=(.*)$').Groups[1].Value.Trim() }
if ($taskGmail -notmatch '^[^\s@]+@gmail\.com$') { throw 'Enter a valid Gmail address.' }
$taskAppPassword = Read-Host 'Gmail app password (input is hidden; Enter keeps the existing password)' -AsSecureString
$taskPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskAppPassword)
try {
    $taskPasswordText = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPasswordPointer).Replace(' ', '')
    if ($taskPasswordText) {
        if ($taskPasswordText.Length -ne 16) { throw 'Gmail app passwords contain 16 characters. Use an app password, not your regular password.' }
        Set-TaskSetting 'SPRINTFLOW_SMTP_PASSWORD' $taskPasswordText
    }
} finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPasswordPointer); $taskPasswordText = $null }
$taskClientId = Read-Host 'Google OAuth Web client ID (Enter keeps the existing value)'
if ($taskClientId) {
    if ($taskClientId -notmatch '^[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$') { throw 'Enter a valid Google OAuth client ID.' }
    Set-TaskSetting 'SPRINTFLOW_GOOGLE_CLIENT_ID' $taskClientId
}
Set-TaskSetting 'SPRINTFLOW_SMTP_USERNAME' $taskGmail
Set-TaskSetting 'SPRINTFLOW_SMTP_HOST' 'smtp.gmail.com'
Set-TaskSetting 'SPRINTFLOW_SMTP_PORT' '587'
[IO.File]::WriteAllText($taskEnvironmentPath, $taskEnvironment, [Text.UTF8Encoding]::new($false))
Write-Host 'Saved local settings without displaying passwords. Restart with .\start.cmd, then check Account services in Profile settings.'
