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
            New-Item -LiteralPath $dir -ItemType Directory -Force | Out-Null
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
        New-Item -LiteralPath $dir -ItemType Directory -Force | Out-Null
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
