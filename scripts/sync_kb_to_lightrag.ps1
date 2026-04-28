param(
    [string]$BaseUrl = "http://127.0.0.1:8080/api/v1",
    [string]$Account = "user@example.com",
    [string]$Password = "123456",
    [string]$Token = "",
    [int]$DocumentId = 0,
    [int]$Limit = 0,
    [string[]]$Statuses = @("chunked"),
    [switch]$IncludeIndexed,
    [switch]$ChunkFirst,
    [switch]$AllowLarge,
    [int]$LargeChunkThreshold = 50,
    [switch]$DryRun,
    [int]$TimeoutSec = 180
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
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    Invoke-RestMethod @params
}

function Get-ApiData {
    param([object]$Response)
    if ($null -eq $Response) {
        return $null
    }
    if ($Response.PSObject.Properties.Name -contains "code" -and $Response.code -ne 200) {
        throw "API returned code $($Response.code): $($Response.message)"
    }
    if ($Response.PSObject.Properties.Name -contains "data") {
        return $Response.data
    }
    return $Response
}

function Get-AuthToken {
    if ($Token.Trim()) {
        return $Token.Trim()
    }

    $loginUrl = "$BaseUrl/auth/login"
    $login = Invoke-JsonApi -Method POST -Uri $loginUrl -Body @{ account = $Account; password = $Password } -TimeoutSec 30
    $data = Get-ApiData $login
    $nextToken = $data.token
    if (-not $nextToken) {
        throw "Login succeeded but response did not include a token."
    }
    return $nextToken
}

function Test-ShouldSync {
    param([object]$Document)
    if ($DocumentId -gt 0 -and [int]$Document.id -ne $DocumentId) {
        return $false
    }
    if ($IncludeIndexed -and $Document.status -eq "indexed") {
        return $true
    }
    return $Statuses -contains [string]$Document.status
}

$normalizedBaseUrl = $BaseUrl.TrimEnd("/")
$authToken = Get-AuthToken
$headers = @{ Authorization = "Bearer $authToken" }

$docsResp = Invoke-JsonApi -Method GET -Uri "$normalizedBaseUrl/kb/documents" -Headers $headers -TimeoutSec 30
$rawDocs = @(Get-ApiData $docsResp)
$docs = @($rawDocs | Where-Object { Test-ShouldSync -Document $_ })

if ($Limit -gt 0) {
    $docs = @($docs | Select-Object -First $Limit)
}

if (-not $docs.Count) {
    Write-Host "No matching knowledge base documents found."
    exit 0
}

$results = New-Object System.Collections.Generic.List[object]

foreach ($doc in $docs) {
    $docId = [int]$doc.id
    $title = [string]$doc.title
    $status = [string]$doc.status

    try {
        if ($ChunkFirst -and $status -eq "parsed") {
            if ($DryRun) {
                $results.Add([pscustomobject]@{
                    Id = $docId; Title = $title; Status = $status; Chunks = "-"; Action = "would chunk"; Result = "dry-run"
                })
                continue
            }
            Invoke-JsonApi -Method POST -Uri "$normalizedBaseUrl/kb/documents/$docId/chunk" -Headers $headers -TimeoutSec $TimeoutSec | Out-Null
            $status = "chunked"
        }

        $chunksResp = Invoke-JsonApi -Method GET -Uri "$normalizedBaseUrl/kb/documents/$docId/chunks" -Headers $headers -TimeoutSec 30
        $chunks = @(Get-ApiData $chunksResp)
        $chunkCount = $chunks.Count

        if ($chunkCount -eq 0) {
            $results.Add([pscustomobject]@{
                Id = $docId; Title = $title; Status = $status; Chunks = 0; Action = "skip"; Result = "no chunks"
            })
            continue
        }

        if (-not $AllowLarge -and $chunkCount -gt $LargeChunkThreshold) {
            $results.Add([pscustomobject]@{
                Id = $docId; Title = $title; Status = $status; Chunks = $chunkCount; Action = "skip"; Result = "large document; pass -AllowLarge"
            })
            continue
        }

        if ($DryRun) {
            $results.Add([pscustomobject]@{
                Id = $docId; Title = $title; Status = $status; Chunks = $chunkCount; Action = "would index"; Result = "dry-run"
            })
            continue
        }

        Invoke-JsonApi -Method POST -Uri "$normalizedBaseUrl/kb/documents/$docId/index" -Headers $headers -TimeoutSec $TimeoutSec | Out-Null
        $results.Add([pscustomobject]@{
            Id = $docId; Title = $title; Status = $status; Chunks = $chunkCount; Action = "index"; Result = "submitted"
        })
    } catch {
        $results.Add([pscustomobject]@{
            Id = $docId; Title = $title; Status = $status; Chunks = "-"; Action = "error"; Result = $_.Exception.Message
        })
    }
}

$results | Format-Table -AutoSize
