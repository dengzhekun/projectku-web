[CmdletBinding()]
param()

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "common.ps1")

Initialize-PortableScript

$repoRoot = Get-PortableRepoRoot
$setupComplete = Test-PortableSetupMarker -RepoRoot $repoRoot
$commandStatus = Get-PortableRequiredCommandStatus

Write-Host ("Repo root: {0}" -f $repoRoot)
Write-Host ("Setup complete: {0}" -f $setupComplete)
Write-Host ""
Write-Host "Required command status:"
$commandStatus | Format-Table -AutoSize

$missing = @($commandStatus | Where-Object { -not $_.Found })
if ($missing.Count -gt 0) {
    exit 1
}

exit 0
