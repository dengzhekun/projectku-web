[CmdletBinding()]
param()

$scriptDir = Split-Path -Parent -Path $MyInvocation.MyCommand.Path
. (Join-Path -Path $scriptDir -ChildPath "common.ps1")

Initialize-PortableScript

$repoRoot = Get-PortableRepoRoot
Write-Host ("Repo root: {0}" -f $repoRoot)

if (-not (Test-PortableSetupMarker -RepoRoot $repoRoot)) {
    throw "Portable setup marker is missing. Run .\setup-portable.bat first."
}

Ensure-PortableDirectories -RepoRoot $repoRoot

if ((Test-DockerDaemonReachable) -and (Test-DockerComposeAvailable)) {
    Start-PortableMySqlContainer -RepoRoot $repoRoot
    Write-Host "MySQL service start requested."
}
else {
    Write-Warning "Docker daemon or docker compose is unavailable; skipping mysql start."
}

$startAllPath = Join-Path -Path $repoRoot -ChildPath "start_all.ps1"
if (-not (Test-Path -LiteralPath $startAllPath)) {
    throw "start_all.ps1 not found at $startAllPath"
}

& $startAllPath -Mode dev -SkipDb -SkipAiDependencyInstall -SkipKbSeed -Portable
exit $LASTEXITCODE
