[CmdletBinding()]
param(
    [string]$Action = "start"
)

. (Join-Path -Path $PSScriptRoot -ChildPath "common.ps1")

Initialize-PortableScript

$repoRoot = Get-PortableRepoRoot
Write-Host ("Repo root: {0}" -f $repoRoot)

$setupPath = Join-Path -Path $repoRoot -ChildPath "scripts\portable\setup-portable.ps1"
$startPath = Join-Path -Path $repoRoot -ChildPath "scripts\portable\start-portable.ps1"
$stopPath = Join-Path -Path $repoRoot -ChildPath "scripts\portable\stop-portable.ps1"
$doctorPath = Join-Path -Path $repoRoot -ChildPath "scripts\portable\doctor-portable.ps1"

if (-not (Test-Path -LiteralPath $setupPath)) {
    throw "setup-portable.ps1 not found at $setupPath"
}
if (-not (Test-Path -LiteralPath $startPath)) {
    throw "start-portable.ps1 not found at $startPath"
}
if (-not (Test-Path -LiteralPath $stopPath)) {
    throw "stop-portable.ps1 not found at $stopPath"
}
if (-not (Test-Path -LiteralPath $doctorPath)) {
    throw "doctor-portable.ps1 not found at $doctorPath"
}

$normalizedAction = $Action.Trim().ToLowerInvariant()

if ($normalizedAction -eq "stop") {
    & $stopPath
    if (-not $?) {
        exit 1
    }
    exit 0
}

if ($normalizedAction -eq "doctor") {
    & $doctorPath
    if (-not $?) {
        exit 1
    }
    exit 0
}

if (($normalizedAction -ne "start") -and ($normalizedAction -ne "")) {
    throw "Unsupported action '$Action'. Use: start, stop, or doctor."
}

if (-not (Test-PortableSetupMarker -RepoRoot $repoRoot)) {
    Write-Host "Portable setup marker not found. Running first-time setup..."
    & $setupPath
    if (-not $?) {
        exit 1
    }
    Write-Host "Setup finished. Continuing with startup..."
}
else {
    Write-Host "Portable setup marker detected. Starting services..."
}

& $startPath
if (-not $?) {
    exit 1
}

exit 0
