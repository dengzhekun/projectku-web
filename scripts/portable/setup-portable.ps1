[CmdletBinding()]
param()

$scriptDir = Split-Path -Parent -Path $MyInvocation.MyCommand.Path
. (Join-Path -Path $scriptDir -ChildPath "common.ps1")

Initialize-PortableScript

$repoRoot = Get-PortableRepoRoot
Write-Host ("Repo root: {0}" -f $repoRoot)

Ensure-PortableDirectories -RepoRoot $repoRoot

$required = Get-PortableRequiredCommandStatus
$setupCommands = @("docker", "node", "npm", "python")
$requiredForSetup = @($required | Where-Object { $setupCommands -contains $_.Command })
$missing = @($requiredForSetup | Where-Object { -not $_.Found })
if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "Missing required commands:"
    $missing | Format-Table -AutoSize
    throw ("Missing required tools: {0}" -f (($missing | Select-Object -ExpandProperty Command) -join ", "))
}

$envPath = Ensure-PortableAiEnv -RepoRoot $repoRoot
Write-Host ("AI env ready: {0}" -f $envPath)

if (-not (Test-DockerDaemonReachable)) {
    throw "Docker daemon is not reachable. Start Docker Desktop (or docker engine) and re-run setup."
}

if (-not (Test-DockerComposeAvailable)) {
    throw "Docker Compose is not available via 'docker compose'. Install/enable Docker Compose v2 and re-run setup."
}

Start-PortableMySqlContainer -RepoRoot $repoRoot
Write-Host "MySQL service start requested."

if (-not (Wait-PortableMySqlReady -RepoRoot $repoRoot)) {
    throw "MySQL did not become ready within the timeout."
}
Write-Host "MySQL is ready."

$setupComplete = Test-PortableSetupMarker -RepoRoot $repoRoot
$dbInitialized = Test-PortableDatabaseInitialized -RepoRoot $repoRoot
if ((-not $setupComplete) -or (-not $dbInitialized)) {
    Initialize-PortableDatabase -RepoRoot $repoRoot
    Write-Host "Database initialized from back\\sql\\init_db.sql."
}
else {
    Write-Host "Setup marker and database state detected; skipping database re-initialization."
}

Install-PortableFrontendDependencies -RepoRoot $repoRoot
Write-Host "Frontend dependencies installed."

Install-PortableAiServiceDependencies -RepoRoot $repoRoot
Write-Host "AI service dependencies installed."

Set-PortableSetupMarker -RepoRoot $repoRoot
Write-Host "Setup marker written."
Write-Host "Portable setup complete."
