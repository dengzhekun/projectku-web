[CmdletBinding()]
param()

$scriptDir = Split-Path -Parent -Path $MyInvocation.MyCommand.Path
. (Join-Path -Path $scriptDir -ChildPath "common.ps1")

Initialize-PortableScript

$repoRoot = Get-PortableRepoRoot
Write-Host ("Repo root: {0}" -f $repoRoot)

$services = @("frontend", "backend", "ai-service")
foreach ($service in $services) {
    $pidFile = Get-PortablePidFilePath -RepoRoot $repoRoot -Name $service
    $stopped = Stop-PortableProcessByPidFile -PidFilePath $pidFile -RepoRoot $repoRoot
    if ($stopped) {
        Write-Host ("Stopped {0}." -f $service)
    }
    else {
        Write-Host ("Did not stop {0} (not running, unverified pid metadata, or stop failed)." -f $service)
    }
}

if ((Test-DockerDaemonReachable) -and (Test-DockerComposeAvailable)) {
    Push-Location -LiteralPath $repoRoot
    try {
        & docker compose stop mysql
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Stopped mysql service."
        }
        else {
            Write-Host "mysql stop command returned a non-zero exit code."
        }
    }
    finally {
        Pop-Location
    }
}
else {
    Write-Host "Skipped mysql stop because Docker daemon or docker compose is unavailable."
}
