param(
    [string]$FrontendUrl = "http://127.0.0.1:5173",
    [string]$BackendUrl = "http://127.0.0.1:8080/api",
    [switch]$SkipBuild,
    [int]$TimeoutSec = 20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot "frontend"
$logsDir = Join-Path $repoRoot "logs"
if (-not (Test-Path -LiteralPath $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logFile = Join-Path $logsDir ("preflight-smoke-{0}.log" -f $timestamp)
Start-Transcript -Path $logFile -Force | Out-Null

$failed = $false

function Write-Step([string]$message) {
    Write-Host ("[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $message)
}

function Mark-Fail([string]$message) {
    $script:failed = $true
    Write-Host ("[FAIL] {0}" -f $message) -ForegroundColor Red
}

function Invoke-HttpSmoke([string]$name, [string]$url) {
    try {
        $resp = Invoke-WebRequest -Uri $url -Method Get -TimeoutSec $TimeoutSec
        if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) {
            Write-Host ("[OK] {0}: {1} -> {2}" -f $name, $url, $resp.StatusCode) -ForegroundColor Green
            return $resp
        }
        Mark-Fail ("{0}: unexpected status {1} for {2}" -f $name, $resp.StatusCode, $url)
    } catch {
        Mark-Fail ("{0}: {1}" -f $name, $_.Exception.Message)
    }
    return $null
}

function Test-ProductImageComponent {
    $viewFile = Join-Path $repoRoot "frontend/src/views/ProductDetailView.vue"
    if (-not (Test-Path -LiteralPath $viewFile)) {
        Mark-Fail ("Product detail view not found: {0}" -f $viewFile)
        return
    }
    $content = Get-Content -Path $viewFile -Raw
    if ($content -match "<ProductCardImage" -and $content -match 'variant="detail"') {
        Write-Host "[OK] Product image component exists in ProductDetailView.vue." -ForegroundColor Green
    } else {
        Mark-Fail "Product image component marker not found in ProductDetailView.vue."
    }
}

try {
    Write-Step ("Preflight smoke started. FrontendUrl={0} BackendUrl={1} SkipBuild={2}" -f $FrontendUrl, $BackendUrl, [bool]$SkipBuild)

    if (-not $SkipBuild) {
        Write-Step "Running frontend build..."
        Push-Location $frontendDir
        try {
            & npm run build | Out-Host
            if ($LASTEXITCODE -ne 0) {
                throw "npm run build failed with exit code $LASTEXITCODE"
            }
            Write-Host "[OK] Frontend build passed." -ForegroundColor Green
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "[SKIP] Frontend build skipped by -SkipBuild."
    }

    Write-Step "Checking frontend pages..."
    $homeResp = Invoke-HttpSmoke -name "Home page" -url $FrontendUrl
    $null = Invoke-HttpSmoke -name "Category page" -url ($FrontendUrl.TrimEnd("/") + "/category")

    Write-Step "Checking backend products API..."
    $apiCandidates = @(
        ($BackendUrl.TrimEnd("/") + "/v1/products"),
        ($BackendUrl.TrimEnd("/") + "/api/v1/products")
    )
    $apiOk = $false
    foreach ($candidate in $apiCandidates) {
        try {
            $apiResp = Invoke-WebRequest -Uri $candidate -Method Get -TimeoutSec $TimeoutSec
            if ($apiResp.StatusCode -ge 200 -and $apiResp.StatusCode -lt 400) {
                Write-Host ("[OK] Products API reachable: {0}" -f $candidate) -ForegroundColor Green
                $apiOk = $true
                break
            }
        } catch {
            Write-Host ("[WARN] API candidate unreachable: {0}" -f $candidate) -ForegroundColor Yellow
        }
    }
    if (-not $apiOk) {
        Mark-Fail ("Neither API endpoint is reachable: {0}" -f ($apiCandidates -join ", "))
    }

    $productId = $null
    if ($apiOk) {
        foreach ($candidate in $apiCandidates) {
            try {
                $raw = Invoke-RestMethod -Uri $candidate -Method Get -TimeoutSec $TimeoutSec
                $items = @()
                if ($raw -is [System.Array]) {
                    $items = $raw
                } elseif ($null -ne $raw.data) {
                    if ($raw.data -is [System.Array]) { $items = $raw.data } else { $items = @($raw.data) }
                } elseif ($null -ne $raw.items) {
                    if ($raw.items -is [System.Array]) { $items = $raw.items } else { $items = @($raw.items) }
                } else {
                    $items = @($raw)
                }
                if ($items.Count -gt 0 -and $null -ne $items[0].id) {
                    $productId = [string]$items[0].id
                    Write-Host ("[OK] Using product id from API: {0}" -f $productId) -ForegroundColor Green
                    break
                }
            } catch {
                Write-Host ("[WARN] Failed to parse product list from {0}: {1}" -f $candidate, $_.Exception.Message) -ForegroundColor Yellow
            }
        }
    }

    if ([string]::IsNullOrWhiteSpace($productId)) {
        $productId = "1"
        Write-Host ("[WARN] Fallback product id: {0}" -f $productId) -ForegroundColor Yellow
    }

    $detailUrl = $FrontendUrl.TrimEnd("/") + "/products/" + [uri]::EscapeDataString($productId)
    $detailResp = Invoke-HttpSmoke -name "Product detail page" -url $detailUrl

    Test-ProductImageComponent

    if ($failed) {
        Write-Host ("[RESULT] Preflight smoke failed. Log: {0}" -f $logFile) -ForegroundColor Red
        exit 1
    }

    Write-Host ("[RESULT] Preflight smoke passed. Log: {0}" -f $logFile) -ForegroundColor Green
    exit 0
}
catch {
    Write-Host ("[ERROR] Preflight smoke crashed: {0}" -f $_.Exception.Message) -ForegroundColor Red
    Write-Host ("[RESULT] Preflight smoke failed. Log: {0}" -f $logFile) -ForegroundColor Red
    exit 1
}
finally {
    Stop-Transcript | Out-Null
}
