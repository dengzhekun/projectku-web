[CmdletBinding()]
param(
    [string]$OutputName = "",
    [switch]$IncludeMysqlData,
    [switch]$IncludeData
)

[System.Reflection.Assembly]::LoadWithPartialName("System.IO.Compression.FileSystem") | Out-Null

. (Join-Path -Path $PSScriptRoot -ChildPath "common.ps1")

Initialize-PortableScript

$repoRoot = Get-PortableRepoRoot
$distDir = Join-Path -Path $repoRoot -ChildPath ".portable\dist"
New-Item -Path $distDir -ItemType Directory -Force | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($OutputName)) {
    $OutputName = "projectku-portable-$stamp"
}
if (-not $OutputName.EndsWith(".zip")) {
    $OutputName = "$OutputName.zip"
}

$zipPath = Join-Path -Path $distDir -ChildPath $OutputName
$stageRoot = Join-Path -Path $env:TEMP -ChildPath ("projectku-portable-stage-" + [guid]::NewGuid().ToString("N"))

$excludeDirs = @(
    ".git",
    ".github",
    ".idea",
    ".vscode",
    ".pytest_cache",
    ".pids",
    "logs",
    ".runtime-logs",
    "back\target",
    "frontend\node_modules",
    "frontend\dist",
    "frontend\playwright-report",
    "frontend\test-results",
    "frontend\tests\.auth",
    "ai-service\data",
    "ai-service\.pytest_cache",
    ".trae",
    "backups",
    ".portable\dist"
)

if (-not $IncludeMysqlData) {
    $excludeDirs += "mysql-data"
}

if (-not $IncludeData) {
    $excludeDirs += "data"
}

$excludeFiles = @(
    "deploy\ai-service.env",
    "deploy\prod.env",
    "deploy\lightrag.env",
    "release-admin-auth.tgz",
    "release-admin-rbac.tgz",
    "release-ai-apple-query.tgz",
    "release-ai-apple-wording.tgz",
    "release-apple-query-full.tgz"
)

try {
    if (Test-Path -LiteralPath $stageRoot) {
        Remove-Item -LiteralPath $stageRoot -Recurse -Force
    }
    New-Item -Path $stageRoot -ItemType Directory -Force | Out-Null

    $robocopyArgs = @(
        $repoRoot,
        $stageRoot,
        "/E",
        "/R:1",
        "/W:1",
        "/NFL",
        "/NDL",
        "/NJH",
        "/NJS",
        "/NP"
    )

    if ($excludeDirs.Count -gt 0) {
        $robocopyArgs += "/XD"
        $robocopyArgs += ($excludeDirs | ForEach-Object { Join-Path -Path $repoRoot -ChildPath $_ })
    }

    if ($excludeFiles.Count -gt 0) {
        $robocopyArgs += "/XF"
        $robocopyArgs += ($excludeFiles | ForEach-Object { Join-Path -Path $repoRoot -ChildPath $_ })
    }

    & robocopy @robocopyArgs | Out-Null
    $robocopyExit = $LASTEXITCODE
    if ($robocopyExit -ge 8) {
        throw "robocopy failed with exit code $robocopyExit"
    }

    $privateDir = Join-Path -Path $stageRoot -ChildPath ".portable\private"
    New-Item -Path $privateDir -ItemType Directory -Force | Out-Null

    $liveAiEnv = Join-Path -Path $repoRoot -ChildPath "deploy\ai-service.env"
    $stagePrivateAiEnv = Join-Path -Path $privateDir -ChildPath "ai-service.env"
    if (Test-Path -LiteralPath $liveAiEnv) {
        Copy-Item -LiteralPath $liveAiEnv -Destination $stagePrivateAiEnv -Force
    }

    $readmeNote = @"
Portable package generated: $stamp

Target machine workflow:
1. unzip this package to a non-system path
2. run run-portable.bat
4. use doctor-portable.bat and stop-portable.bat for daily operation

Bundled private AI config:
- .portable\private\ai-service.env
"@
    Set-Content -Path (Join-Path -Path $stageRoot -ChildPath "PORTABLE_PACKAGE.txt") -Value $readmeNote -Encoding ASCII

    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        $stageRoot,
        $zipPath,
        [System.IO.Compression.CompressionLevel]::Optimal,
        $false
    )

    Write-Host ("Portable package created: {0}" -f $zipPath)
}
finally {
    if (Test-Path -LiteralPath $stageRoot) {
        Remove-Item -LiteralPath $stageRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
