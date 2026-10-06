param(
    [Parameter(Mandatory)][string]$Serial,
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'

# Keep RedTriggerFix itself foreground, with its guard enabled and backend connected.
# This check stops/reopens the app without clearing data or changing saved profiles.
function Invoke-Device([string[]]$Command) {
    $result = & $Adb -s $Serial @Command
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Command" }
    $result -join "`n"
}

$dumpArgs = @('shell', 'dumpsys', 'activity', 'service', 'com.redtriggerfix/com.redtrigger.TriggerService')
$before = Invoke-Device $dumpArgs
if ($before -notmatch 'masterEnabled=true' -or $before -notmatch 'backend=CONNECTED') {
    throw 'Enable the guard and wait for its backend before the lifecycle check.'
}
if ($before -notmatch 'foreground=com.redtriggerfix profile= ') {
    throw 'Open RedTriggerFix and leave the game before this app-stop/reopen check.'
}
$backendPid = (Invoke-Device @('shell', 'pidof', 'com.redtriggerfix:tgk')).Trim()
if ($backendPid -notmatch '^\d+$') { throw 'Expected exactly one backend process.' }
try {
    Invoke-Device @('shell', 'am', 'force-stop', 'com.redtriggerfix') | Out-Null
    Start-Sleep -Seconds 2
    $survivingPid = (Invoke-Device @('shell', 'pidof', 'com.redtriggerfix:tgk')).Trim()
    if ($survivingPid -cne $backendPid) { throw 'Backend did not survive app process exit.' }
} finally {
    Invoke-Device @('shell', 'am', 'start', '-n', 'com.redtriggerfix/com.redtrigger.MainActivity') | Out-Null
}
for ($attempt = 0; $attempt -lt 10; $attempt++) {
    Start-Sleep -Seconds 1
    $after = Invoke-Device $dumpArgs
    if ($after -match 'masterEnabled=true running=true' -and $after -match 'backend=CONNECTED') {
        $reusedPid = (Invoke-Device @('shell', 'pidof', 'com.redtriggerfix:tgk')).Trim()
        if ($reusedPid -cne $backendPid) { throw 'Reconnection replaced the surviving backend.' }
        "PASS: backend PID $backendPid survived app exit and was reused after reopening."
        return
    }
}
throw 'Guard did not reconnect after reopening.'
