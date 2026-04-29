Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Initialize-PortableScript {
    Set-StrictMode -Version Latest
    $ErrorActionPreference = "Stop"
}

function Get-PortableRepoRoot {
    $scriptsDir = Split-Path -Parent -Path $PSScriptRoot
    return Split-Path -Parent -Path $scriptsDir
}

function Ensure-PortableDirectories {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $portableRoot = Join-Path -Path $RepoRoot -ChildPath ".portable"
    $dirs = @(
        (Join-Path -Path $RepoRoot -ChildPath ".runtime-logs"),
        (Join-Path -Path $RepoRoot -ChildPath ".pids"),
        $portableRoot
    )

    foreach ($dir in $dirs) {
        if (-not (Test-Path -LiteralPath $dir)) {
            New-Item -Path $dir -ItemType Directory -Force | Out-Null
        }
    }
}

function Get-PortableSetupMarkerPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $portableRoot = Join-Path -Path $RepoRoot -ChildPath ".portable"
    return Join-Path -Path $portableRoot -ChildPath "setup-complete.marker"
}

function Test-PortableSetupMarker {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    return Test-Path -LiteralPath (Get-PortableSetupMarkerPath -RepoRoot $RepoRoot)
}

function Set-PortableSetupMarker {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $markerPath = Get-PortableSetupMarkerPath -RepoRoot $RepoRoot
    $dir = Split-Path -Parent -Path $markerPath
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -Path $dir -ItemType Directory -Force | Out-Null
    }
    Set-Content -LiteralPath $markerPath -Value ("setup-complete {0}" -f (Get-Date -Format "s")) -Encoding ASCII
}

function Get-PortablePidFilePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $pidDir = Join-Path -Path $RepoRoot -ChildPath ".pids"
    return Join-Path -Path $pidDir -ChildPath ($Name + ".pid")
}

function Stop-PortableProcessByPidFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PidFilePath
    )

    if (-not (Test-Path -LiteralPath $PidFilePath)) {
        return $false
    }

    $raw = (Get-Content -LiteralPath $PidFilePath -ErrorAction Stop -Raw).Trim()
    if (-not $raw) {
        return $false
    }

    # Backward compatibility: legacy bare PID files are treated conservatively.
    if ($raw -notmatch "^\s*\{") {
        return $false
    }

    try {
        $pidData = $raw | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        return $false
    }

    if (
        $null -eq $pidData -or
        $null -eq $pidData.pid -or
        [string]::IsNullOrWhiteSpace([string]$pidData.processName) -or
        [string]::IsNullOrWhiteSpace([string]$pidData.startedAt)
    ) {
        return $false
    }

    [int]$processId = 0
    if (-not [int]::TryParse([string]$pidData.pid, [ref]$processId)) {
        return $false
    }

    $expectedName = [string]$pidData.processName
    $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($null -eq $proc) {
        Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
        return $false
    }

    if (-not [string]::Equals($proc.ProcessName, $expectedName, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $false
    }

    [DateTime]$expectedStart = [DateTime]::MinValue
    if (-not [DateTime]::TryParse([string]$pidData.startedAt, [ref]$expectedStart)) {
        return $false
    }
    try {
        $actualStart = $proc.StartTime
    }
    catch {
        return $false
    }

    $expectedUtc = $expectedStart.ToUniversalTime()
    $actualUtc = $actualStart.ToUniversalTime()
    if ($actualUtc.Ticks -ne $expectedUtc.Ticks) {
        return $false
    }

    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    $delaysMs = @(150, 250, 400, 500, 700)
    foreach ($delayMs in $delaysMs) {
        Start-Sleep -Milliseconds $delayMs
        $stillRunning = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($null -eq $stillRunning) {
            Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
            return $true
        }
    }
    return $false
}

function Get-PortableRequiredCommandStatus {
    $commands = @("docker", "java", "mvn", "node", "npm", "python")
    $results = @()

    foreach ($name in $commands) {
        $cmd = Get-Command -Name $name -ErrorAction SilentlyContinue
        $cmdPath = ""
        if ($null -ne $cmd) {
            if ($cmd.PSObject.Properties["Definition"] -and -not [string]::IsNullOrWhiteSpace([string]$cmd.Definition)) {
                $cmdPath = [string]$cmd.Definition
            }
            elseif ($cmd.PSObject.Properties["Path"] -and -not [string]::IsNullOrWhiteSpace([string]$cmd.Path)) {
                $cmdPath = [string]$cmd.Path
            }
            elseif ($cmd.PSObject.Properties["Source"] -and -not [string]::IsNullOrWhiteSpace([string]$cmd.Source)) {
                $cmdPath = [string]$cmd.Source
            }
        }
        $results += [PSCustomObject]@{
            Command = $name
            Found   = ($null -ne $cmd)
            Path    = $cmdPath
        }
    }

    return $results
}

function Ensure-PortableAiEnv {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $targetPath = Join-Path -Path $RepoRoot -ChildPath "deploy\ai-service.env"
    if (Test-Path -LiteralPath $targetPath) {
        return $targetPath
    }

    $privatePath = Join-Path -Path $RepoRoot -ChildPath ".portable\private\ai-service.env"
    $examplePath = Join-Path -Path $RepoRoot -ChildPath "deploy\ai-service.env.example"
    $sourcePath = ""

    if (Test-Path -LiteralPath $privatePath) {
        $sourcePath = $privatePath
    }
    elseif (Test-Path -LiteralPath $examplePath) {
        $sourcePath = $examplePath
    }
    else {
        throw "Cannot ensure AI env: no source file found at $privatePath or $examplePath."
    }

    Copy-Item -LiteralPath $sourcePath -Destination $targetPath -Force
    return $targetPath
}

function Test-DockerDaemonReachable {
    try {
        $null = & docker info 2>$null
        return ($LASTEXITCODE -eq 0)
    }
    catch {
        return $false
    }
}

function Test-DockerComposeAvailable {
    try {
        & docker compose version 1>$null 2>$null
        return ($LASTEXITCODE -eq 0)
    }
    catch {
        return $false
    }
}

function Start-PortableMySqlContainer {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    Push-Location -LiteralPath $RepoRoot
    try {
        & docker compose up -d mysql
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to start mysql service via docker compose."
        }
    }
    finally {
        Pop-Location
    }
}

function Get-PortableMySqlContainerId {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    Push-Location -LiteralPath $RepoRoot
    try {
        $containerId = (& docker compose ps -q mysql | Select-Object -First 1).Trim()
        return $containerId
    }
    finally {
        Pop-Location
    }
}

function Wait-PortableMySqlReady {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [string]$RootPassword = "123456",
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $containerId = Get-PortableMySqlContainerId -RepoRoot $RepoRoot
        if (-not [string]::IsNullOrWhiteSpace($containerId)) {
            & docker exec $containerId mysqladmin ping -h 127.0.0.1 -uroot ("-p{0}" -f $RootPassword) --silent 2>$null
            if ($LASTEXITCODE -eq 0) {
                return $true
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    return $false
}

function Initialize-PortableDatabase {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [string]$RootPassword = "123456",
        [string]$DatabaseName = "web"
    )

    $sqlPath = Join-Path -Path $RepoRoot -ChildPath "back\sql\init_db.sql"
    if (-not (Test-Path -LiteralPath $sqlPath)) {
        throw "Database init script not found: $sqlPath"
    }

    $containerId = Get-PortableMySqlContainerId -RepoRoot $RepoRoot
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        throw "MySQL container id not found for docker compose service 'mysql'."
    }

    & docker exec $containerId mysql -uroot ("-p{0}" -f $RootPassword) -e ("CREATE DATABASE IF NOT EXISTS `{0}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" -f $DatabaseName)
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create database '$DatabaseName' when needed."
    }

    Get-Content -LiteralPath $sqlPath -Raw | & docker exec -i $containerId mysql -uroot ("-p{0}" -f $RootPassword) $DatabaseName
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to initialize database from $sqlPath"
    }
}

function Test-PortableDatabaseInitialized {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [string]$RootPassword = "123456",
        [string]$DatabaseName = "web",
        [string]$KnownTable = "users"
    )

    $containerId = Get-PortableMySqlContainerId -RepoRoot $RepoRoot
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        return $false
    }

    $query = "SELECT 1 FROM information_schema.tables WHERE table_schema='{0}' AND table_name='{1}' LIMIT 1;" -f $DatabaseName, $KnownTable
    $result = & docker exec $containerId mysql -N -B -uroot ("-p{0}" -f $RootPassword) -e $query 2>$null
    if ($LASTEXITCODE -ne 0) {
        return $false
    }

    return (($result | Select-Object -First 1).Trim() -eq "1")
}

function Install-PortableFrontendDependencies {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $frontendDir = Join-Path -Path $RepoRoot -ChildPath "frontend"
    $lockPath = Join-Path -Path $frontendDir -ChildPath "package-lock.json"
    Push-Location -LiteralPath $frontendDir
    try {
        if (Test-Path -LiteralPath $lockPath) {
            & npm ci
        }
        else {
            & npm install
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to install frontend dependencies."
        }
    }
    finally {
        Pop-Location
    }
}

function Install-PortableAiServiceDependencies {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $aiServiceDir = Join-Path -Path $RepoRoot -ChildPath "ai-service"
    $requirementsPath = Join-Path -Path $aiServiceDir -ChildPath "requirements.txt"
    Push-Location -LiteralPath $aiServiceDir
    try {
        & python -m pip install -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com -r $requirementsPath
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to install ai-service dependencies."
        }
    }
    finally {
        Pop-Location
    }
}
