[CmdletBinding()]
param(
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if ($PSScriptRoot) {
        return (Split-Path -Path $PSScriptRoot -Parent)
    }
    return (Get-Location).Path
}

function Parse-EnvFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $result = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line -match '^\s*#') {
            continue
        }
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            continue
        }

        $key = $matches[1]
        $value = $matches[2].Trim()
        $result[$key] = $value
    }

    return $result
}

function New-StrongSecret {
    param(
        [int]$Bytes = 24
    )

    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buffer)
    }
    finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($buffer).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function Test-IsPlaceholder {
    param(
        [AllowNull()]
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $true
    }

    $trimmed = $Value.Trim()
    return ($trimmed -match '^(replace_with_|change_me|changeme|your_|example_|<)')
}

function Update-EnvLine {
    param(
        [string]$Line,
        [hashtable]$ValuesByKey
    )

    if ($Line -notmatch '^(\s*)([A-Za-z_][A-Za-z0-9_]*)(\s*=\s*)(.*)$') {
        return $Line
    }

    $indent = $matches[1]
    $key = $matches[2]
    $sep = $matches[3]
    if (-not $ValuesByKey.ContainsKey($key)) {
        return $Line
    }

    return "$indent$key$sep$($ValuesByKey[$key])"
}

$repoRoot = Get-RepoRoot
$templatePath = Join-Path -Path $repoRoot -ChildPath "deploy/lightrag.env.template"
$targetPath = Join-Path -Path $repoRoot -ChildPath "deploy/lightrag.env"
$aiEnvPath = Join-Path -Path $repoRoot -ChildPath "deploy/ai-service.env"

if (-not (Test-Path -LiteralPath $templatePath)) {
    Write-Error "Missing template file: $templatePath"
    exit 1
}

if ((Test-Path -LiteralPath $targetPath) -and -not $Force) {
    Write-Error "Target file already exists: $targetPath. Use -Force to overwrite."
    exit 1
}

$templateLines = Get-Content -Path $templatePath
$templateValues = Parse-EnvFile -Path $templatePath
$aiValues = @{}
if (Test-Path -LiteralPath $aiEnvPath) {
    $aiValues = Parse-EnvFile -Path $aiEnvPath
}

$embeddingGatewayKey = $null
if ($aiValues.ContainsKey("AI_EMBEDDING_GATEWAY_API_KEY")) {
    $embeddingGatewayKey = $aiValues["AI_EMBEDDING_GATEWAY_API_KEY"]
}

if ([string]::IsNullOrWhiteSpace($embeddingGatewayKey)) {
    Write-Error "AI_EMBEDDING_GATEWAY_API_KEY is missing in deploy/ai-service.env. Set it first, then rerun this script."
    exit 1
}

$updates = @{}
$statusSet = New-Object System.Collections.Generic.List[string]
$statusGenerated = New-Object System.Collections.Generic.List[string]
$statusKept = New-Object System.Collections.Generic.List[string]

$updates["EMBEDDING_BINDING_API_KEY"] = $embeddingGatewayKey
$statusSet.Add("EMBEDDING_BINDING_API_KEY (from AI_EMBEDDING_GATEWAY_API_KEY)") | Out-Null

$updates["EMBEDDING_BINDING_HOST"] = "http://ai-service:9000/v1"
$statusSet.Add("EMBEDDING_BINDING_HOST (fixed value)") | Out-Null

$updates["EMBEDDING_USE_BASE64"] = "false"
$statusSet.Add("EMBEDDING_USE_BASE64 (fixed value)") | Out-Null

$llmMap = @{
    "AI_LLM_BASE_URL" = "LLM_BINDING_HOST"
    "AI_LLM_API_KEY" = "LLM_BINDING_API_KEY"
    "AI_LLM_MODEL" = "LLM_MODEL"
}
foreach ($sourceKey in $llmMap.Keys) {
    $targetKey = $llmMap[$sourceKey]
    if ($aiValues.ContainsKey($sourceKey) -and -not [string]::IsNullOrWhiteSpace($aiValues[$sourceKey])) {
        $updates[$targetKey] = $aiValues[$sourceKey]
        $statusSet.Add("$targetKey (from $sourceKey)") | Out-Null
    }
}

foreach ($sensitiveKey in @("LIGHTRAG_API_KEY", "POSTGRES_PASSWORD")) {
    $templateValue = $null
    if ($templateValues.ContainsKey($sensitiveKey)) {
        $templateValue = $templateValues[$sensitiveKey]
    }

    if (Test-IsPlaceholder -Value $templateValue) {
        $updates[$sensitiveKey] = New-StrongSecret
        $statusGenerated.Add($sensitiveKey) | Out-Null
    }
    else {
        $updates[$sensitiveKey] = $templateValue
        $statusKept.Add("$sensitiveKey (from template)") | Out-Null
    }
}

$renderedLines = foreach ($line in $templateLines) {
    Update-EnvLine -Line $line -ValuesByKey $updates
}

[System.IO.File]::WriteAllLines($targetPath, $renderedLines)

Write-Host "Created $targetPath"
if ($statusSet.Count -gt 0) {
    Write-Host "Set variables:"
    foreach ($name in $statusSet) {
        Write-Host " - $name"
    }
}
if ($statusGenerated.Count -gt 0) {
    Write-Host "Generated strong random values:"
    foreach ($name in $statusGenerated) {
        Write-Host " - $name"
    }
}
if ($statusKept.Count -gt 0) {
    Write-Host "Kept existing template values:"
    foreach ($name in $statusKept) {
        Write-Host " - $name"
    }
}
