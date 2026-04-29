[CmdletBinding()]
param()

$scriptDir = Split-Path -Parent -Path $MyInvocation.MyCommand.Path
. (Join-Path -Path $scriptDir -ChildPath "common.ps1")

Initialize-PortableScript

$repoRoot = Get-PortableRepoRoot
$setupComplete = Test-PortableSetupMarker -RepoRoot $repoRoot
$commandStatus = Get-PortableRequiredCommandStatus
$aiEnvPath = Join-Path -Path $repoRoot -ChildPath "deploy\ai-service.env"
$aiEnvPresent = Test-Path -LiteralPath $aiEnvPath
$ports = @(3306, 5173, 8080, 9000)
$portRows = @()
foreach ($port in $ports) {
    $portRows += [PSCustomObject]@{
        Port      = $port
        Listening = (Test-PortablePortListening -Port $port)
    }
}

Write-Host ("Repo root: {0}" -f $repoRoot)
Write-Host ("Setup complete: {0}" -f $setupComplete)
Write-Host ("AI env present: {0} ({1})" -f $aiEnvPresent, $aiEnvPath)
Write-Host ""
Write-Host "Required command status:"
$commandStatus | Format-Table -AutoSize
Write-Host ""
Write-Host "Listening ports:"
$portRows | Format-Table -AutoSize

$missing = @($commandStatus | Where-Object { -not $_.Found })
if ($missing.Count -gt 0) {
    exit 1
}

exit 0
