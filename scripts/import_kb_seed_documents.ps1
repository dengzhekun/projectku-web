param(
    [string]$BaseUrl = "http://127.0.0.1:8080/api/v1",
    [string]$Account = "user@example.com",
    [string]$Password = "123456",
    [switch]$Chunk,
    [switch]$Index,
    [switch]$DryRun,
    [int]$TimeoutSec = 60
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 6) {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
}

function Invoke-JsonApi {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("GET", "POST")][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [int]$TimeoutSec = 30
    )

    $params = @{
        Method      = $Method
        Uri         = $Uri
        Headers     = $Headers
        TimeoutSec  = $TimeoutSec
        ErrorAction = "Stop"
        DisableKeepAlive = $true
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }

    try {
        $response = Invoke-WebRequest @params -UseBasicParsing
        if ([string]::IsNullOrWhiteSpace($response.Content)) {
            return $null
        }
        return $response.Content | ConvertFrom-Json
    } catch {
        $respText = ""
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $respText = $_.ErrorDetails.Message
        } elseif ($_.Exception -and $_.Exception.Message) {
            $respText = $_.Exception.Message
        }
        throw "API request failed: [$Method] $Uri`n$respText"
    }
}

function Get-ApiData {
    param([object]$Response)

    if ($null -eq $Response) {
        return $null
    }

    if ($Response.PSObject.Properties.Name -contains "code") {
        if ([int]$Response.code -ne 200) {
            throw "API returned code $($Response.code): $($Response.message)"
        }
        return $Response.data
    }

    if ($Response.PSObject.Properties.Name -contains "data") {
        return $Response.data
    }

    return $Response
}

$normalizedBaseUrl = $BaseUrl.TrimEnd("/")
$repoRoot = Split-Path -Parent $PSScriptRoot
$seedDir = Join-Path $repoRoot "docs\knowledge-base\seed"

$documents = @(
    @{ File = "after-sales-policy.md";     Title = "KB After Sales Policy";     Category = "after_sales" },
    @{ File = "coupon-rules.md";           Title = "KB Coupon Rules";           Category = "coupon" },
    @{ File = "logistics-rules.md";        Title = "KB Logistics Rules";        Category = "logistics" },
    @{ File = "refund-payment-rules.md";   Title = "KB Refund Payment Rules";   Category = "payment_refund" },
    @{ File = "product-shopping-guide.md"; Title = "KB Product Shopping Guide"; Category = "shopping_guide" }
)

if (-not (Test-Path -LiteralPath $seedDir)) {
    throw "Seed directory does not exist: $seedDir"
}

foreach ($doc in $documents) {
    $fullPath = Join-Path $seedDir $doc.File
    if (-not (Test-Path -LiteralPath $fullPath)) {
        throw "Required seed file is missing: $fullPath"
    }
}

Write-Host "Import seed directory: $seedDir"
Write-Host ("Target API: {0}" -f $normalizedBaseUrl)
Write-Host ("Options: Chunk={0}, Index={1}, DryRun={2}" -f $Chunk.IsPresent, $Index.IsPresent, $DryRun.IsPresent)
if ($Index -and -not $Chunk) {
    Write-Warning "Indexing without -Chunk may fail for newly created parsed documents."
}

$authToken = ""
if (-not $DryRun) {
    $loginResponse = Invoke-JsonApi -Method POST -Uri "$normalizedBaseUrl/auth/login" -Body @{
        account  = $Account
        password = $Password
    } -TimeoutSec $TimeoutSec
    $loginData = Get-ApiData $loginResponse
    $authToken = [string]$loginData.token
    if ([string]::IsNullOrWhiteSpace($authToken)) {
        throw "Login succeeded but token is missing."
    }
}

$headers = @{}
if (-not $DryRun) {
    $headers.Authorization = "Bearer $authToken"
}

$results = New-Object System.Collections.Generic.List[object]

foreach ($doc in $documents) {
    $fullPath = Join-Path $seedDir $doc.File
    $content = Get-Content -LiteralPath $fullPath -Raw -Encoding UTF8
    $createdId = ""
    $createResult = "skipped"
    $chunkResult = "skipped"
    $indexResult = "skipped"
    $message = ""

    try {
        if ($DryRun) {
            $createResult = "would-create"
            if ($Chunk) {
                $chunkResult = "would-chunk"
            }
            if ($Index) {
                $indexResult = "would-index"
            }
        } else {
            $createResponse = Invoke-JsonApi -Method POST -Uri "$normalizedBaseUrl/kb/documents" -Headers $headers -Body @{
                title       = $doc.Title
                category    = $doc.Category
                contentText = $content
            } -TimeoutSec $TimeoutSec
            $created = Get-ApiData $createResponse
            $createdId = [string]$created.id
            $createResult = "ok"
            if ([string]::IsNullOrWhiteSpace($createdId)) {
                throw "Document created but id is missing."
            }

            if ($Chunk) {
                Invoke-JsonApi -Method POST -Uri "$normalizedBaseUrl/kb/documents/$createdId/chunk" -Headers $headers -TimeoutSec $TimeoutSec | Out-Null
                $chunkResult = "ok"
            }

            if ($Index) {
                Invoke-JsonApi -Method POST -Uri "$normalizedBaseUrl/kb/documents/$createdId/index" -Headers $headers -TimeoutSec $TimeoutSec | Out-Null
                $indexResult = "ok"
            }
        }
    } catch {
        $message = $_.Exception.Message
        if ($createResult -eq "skipped") {
            $createResult = "error"
        } elseif ($Chunk -and $chunkResult -eq "skipped") {
            $chunkResult = "error"
        } elseif ($Index -and $indexResult -eq "skipped") {
            $indexResult = "error"
        }
    }

    $results.Add([pscustomobject]@{
        File       = $doc.File
        DocumentId = $createdId
        Create     = $createResult
        Chunk      = $chunkResult
        Index      = $indexResult
        Message    = $message
    })
}

Write-Host ""
Write-Host "Import summary:"
$results | Format-Table -AutoSize

$createSuccess = @($results | Where-Object { $_.Create -eq "ok" -or $_.Create -eq "exists" -or $_.Create -eq "would-create" }).Count
$chunkSuccess = @($results | Where-Object { $_.Chunk -eq "ok" -or $_.Chunk -eq "would-chunk" }).Count
$indexSuccess = @($results | Where-Object { $_.Index -eq "ok" -or $_.Index -eq "would-index" }).Count
$errorCount = @($results | Where-Object {
    $_.Create -eq "error" -or $_.Chunk -eq "error" -or $_.Index -eq "error"
}).Count

Write-Host ""
Write-Host ("Totals: documents={0}, create={1}, chunk={2}, index={3}, errors={4}" -f $results.Count, $createSuccess, $chunkSuccess, $indexSuccess, $errorCount)

if ($errorCount -gt 0) {
    exit 1
}
