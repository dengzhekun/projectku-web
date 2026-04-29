[CmdletBinding()]
param()

. (Join-Path -Path $PSScriptRoot -ChildPath "common.ps1")

Initialize-PortableScript

$repoRoot = Get-PortableRepoRoot
Write-Host ("Repo root: {0}" -f $repoRoot)

$setupPath = Join-Path -Path $repoRoot -ChildPath "scripts\portable\setup-portable.ps1"
$startPath = Join-Path -Path $repoRoot -ChildPath "scripts\portable\start-portable.ps1"

if (-not (Test-Path -LiteralPath $setupPath)) {
    throw "setup-portable.ps1 not found at $setupPath"
}
if (-not (Test-Path -LiteralPath $startPath)) {
    throw "start-portable.ps1 not found at $startPath"
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
