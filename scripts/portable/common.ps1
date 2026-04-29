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
        [string]$PidFilePath,
        [string]$RepoRoot
    )

    if (-not (Test-Path -LiteralPath $PidFilePath)) {
        return $false
    }

    if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
        $pidDir = Split-Path -Parent -Path $PidFilePath
        $RepoRoot = Split-Path -Parent -Path $pidDir
    }

    $raw = (Get-Content -LiteralPath $PidFilePath -ErrorAction Stop -Raw).Trim()
    if (-not $raw) {
        Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
        return $false
    }

    $processId = 0
    $expectedName = ""
    $expectedStart = [DateTime]::MinValue
    $hasExpectedStart = $false
    $legacyMode = $false

    if ($raw -match "^\d+$") {
        # Legacy bare-PID format: allow guarded handling only.
        if (-not [int]::TryParse($raw, [ref]$processId)) {
            Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
            return $false
        }
        $legacyMode = $true
    }
    elseif ($raw -match "^\s*\{") {
        try {
            $pidData = $raw | ConvertFrom-Json -ErrorAction Stop
        }
        catch {
            Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
            return $false
        }

        if (
            $null -eq $pidData -or
            $null -eq $pidData.pid -or
            [string]::IsNullOrWhiteSpace([string]$pidData.processName) -or
            [string]::IsNullOrWhiteSpace([string]$pidData.startedAt)
        ) {
            Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
            return $false
        }

        if (-not [int]::TryParse([string]$pidData.pid, [ref]$processId)) {
            Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
            return $false
        }

        $expectedName = [string]$pidData.processName
        if (-not [DateTime]::TryParse([string]$pidData.startedAt, [ref]$expectedStart)) {
            Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
            return $false
        }
        $hasExpectedStart = $true
    }
    else {
        Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
        return $false
    }
    $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($null -eq $proc) {
        Remove-Item -LiteralPath $PidFilePath -Force -ErrorAction SilentlyContinue
        return $false
    }

    try {
        $actualStart = $proc.StartTime
    }
    catch {
        return $false
    }

    if ($legacyMode) {
        # Guarded legacy support: only stop our own service shell process shape.
        $nameOk = [string]::Equals($proc.ProcessName, "powershell", [System.StringComparison]::OrdinalIgnoreCase) -or
            [string]::Equals($proc.ProcessName, "pwsh", [System.StringComparison]::OrdinalIgnoreCase)
        if (-not $nameOk) {
            return $false
        }

        $procCmd = ""
        try {
            $cim = Get-CimInstance -ClassName Win32_Process -Filter ("ProcessId = {0}" -f $processId) -ErrorAction SilentlyContinue
            if ($null -ne $cim) {
                $procCmd = [string]$cim.CommandLine
            }
        }
        catch {
            $procCmd = ""
        }

        if ([string]::IsNullOrWhiteSpace($procCmd)) {
            return $false
        }

        $repoEscaped = [Regex]::Escape($RepoRoot)
        $looksLikeRepoShell = $procCmd -match $repoEscaped
        $looksLikeService = ($procCmd -match "mvn\s+spring-boot:run") -or
            ($procCmd -match "uvicorn\s+app\.main:app") -or
            ($procCmd -match "npm\s+run\s+dev")
        if ((-not $looksLikeRepoShell) -or (-not $looksLikeService)) {
            return $false
        }
    }
    else {
        if (-not [string]::Equals($proc.ProcessName, $expectedName, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
        if ($hasExpectedStart) {
            $expectedUtc = $expectedStart.ToUniversalTime()
            $actualUtc = $actualStart.ToUniversalTime()
            if ($actualUtc.Ticks -ne $expectedUtc.Ticks) {
                return $false
            }
        }
    }

    $childrenByParent = @{}
    try {
        $allProc = Get-CimInstance -ClassName Win32_Process -ErrorAction SilentlyContinue
        foreach ($p in $allProc) {
            $ppid = [int]$p.ParentProcessId
            if (-not $childrenByParent.ContainsKey($ppid)) {
                $childrenByParent[$ppid] = New-Object System.Collections.ArrayList
            }
            [void]$childrenByParent[$ppid].Add([int]$p.ProcessId)
        }
    }
    catch {
        $childrenByParent = @{}
    }

    $descendantIds = New-Object System.Collections.ArrayList
    $stack = New-Object System.Collections.Stack
    $stack.Push([int]$processId)
    while ($stack.Count -gt 0) {
        $current = [int]$stack.Pop()
        if ($childrenByParent.ContainsKey($current)) {
            foreach ($childId in $childrenByParent[$current]) {
                [void]$descendantIds.Add([int]$childId)
                $stack.Push([int]$childId)
            }
        }
    }

    # Deepest-first best effort stop of descendants before wrapper.
    for ($i = $descendantIds.Count - 1; $i -ge 0; $i--) {
        $childId = [int]$descendantIds[$i]
        Stop-Process -Id $childId -Force -ErrorAction SilentlyContinue
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

    $mysqlContainerName = "projectku-mysql"
    $existingIdLine = @(& docker ps -aq -f ("name=^{0}$" -f $mysqlContainerName) 2>$null | Select-Object -First 1)
    $existingId = ""
    if ($existingIdLine.Count -gt 0 -and $null -ne $existingIdLine[0]) {
        $existingId = [string]$existingIdLine[0]
    }
    if (-not [string]::IsNullOrWhiteSpace($existingId)) {
        $runningIdLine = @(& docker ps -q -f ("name=^{0}$" -f $mysqlContainerName) 2>$null | Select-Object -First 1)
        $runningId = ""
        if ($runningIdLine.Count -gt 0 -and $null -ne $runningIdLine[0]) {
            $runningId = [string]$runningIdLine[0]
        }
        if (-not [string]::IsNullOrWhiteSpace($runningId)) {
            return
        }

        & docker start $mysqlContainerName 1>$null
        if ($LASTEXITCODE -eq 0) {
            return
        }

        throw "MySQL container '$mysqlContainerName' exists but could not be started."
    }

    Push-Location -LiteralPath $RepoRoot
    try {
        & docker compose up -d mysql
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to start mysql service via docker compose and no reusable '$mysqlContainerName' container was available."
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
        $containerIdLine = @(& docker compose ps -q mysql 2>$null | Select-Object -First 1)
        $containerId = ""
        if ($containerIdLine.Count -gt 0 -and $null -ne $containerIdLine[0]) {
            $containerId = [string]$containerIdLine[0]
        }
        if (-not [string]::IsNullOrWhiteSpace($containerId)) {
            return $containerId
        }
    }
    finally {
        Pop-Location
    }

    $fallbackLine = @(& docker ps -aq -f "name=^projectku-mysql$" 2>$null | Select-Object -First 1)
    if ($fallbackLine.Count -gt 0 -and $null -ne $fallbackLine[0]) {
        return [string]$fallbackLine[0]
    }
    return ""
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
            $previousErrorAction = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            & docker exec $containerId mysqladmin ping -h 127.0.0.1 -uroot ("-p{0}" -f $RootPassword) --silent 1>$null 2>$null
            $ErrorActionPreference = $previousErrorAction
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

    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & docker exec $containerId mysql -uroot ("-p{0}" -f $RootPassword) -e ("CREATE DATABASE IF NOT EXISTS `{0}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" -f $DatabaseName) 1>$null 2>$null
    $ErrorActionPreference = $previousErrorAction
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create database '$DatabaseName' when needed."
    }

    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    Get-Content -LiteralPath $sqlPath -Raw | & docker exec -i $containerId mysql -uroot ("-p{0}" -f $RootPassword) $DatabaseName 1>$null 2>$null
    $ErrorActionPreference = $previousErrorAction
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
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $result = & docker exec $containerId mysql -N -B -uroot ("-p{0}" -f $RootPassword) -e $query 2>$null
    $ErrorActionPreference = $previousErrorAction
    if ($LASTEXITCODE -ne 0) {
        return $false
    }

    $first = $result | Select-Object -First 1
    if ($null -eq $first) {
        return $false
    }
    return ([string]$first).Trim() -eq "1"
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

function Test-PortablePortListening {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}
