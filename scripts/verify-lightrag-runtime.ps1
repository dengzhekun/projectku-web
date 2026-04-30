[CmdletBinding()]
param(
    [string]$BackendBaseUrl = $(if ($env:PROJECTKU_BACKEND_BASE_URL) { $env:PROJECTKU_BACKEND_BASE_URL } else { "http://127.0.0.1:8080/api" }),
    [string]$AiServiceBaseUrl = $(if ($env:AI_SERVICE_BASE_URL) { $env:AI_SERVICE_BASE_URL } else { "http://127.0.0.1:9000" }),
    [string]$LightRagBaseUrl = $(if ($env:LIGHTRAG_BASE_URL) { $env:LIGHTRAG_BASE_URL } else { "http://127.0.0.1:19621" }),
    [string]$EmbeddingBaseUrl = $(if ($env:PROJECTKU_EMBEDDING_BASE_URL) { $env:PROJECTKU_EMBEDDING_BASE_URL } else { "http://127.0.0.1:9001" }),
    [string]$LightRagApiKey = $env:LIGHTRAG_API_KEY,
    [string]$LightRagApiKeyHeader = $(if ($env:LIGHTRAG_API_KEY_HEADER) { $env:LIGHTRAG_API_KEY_HEADER } else { "X-API-Key" }),
    [switch]$CheckEmbeddingGateway,
    [string]$EmbeddingGatewayApiKey = $env:AI_EMBEDDING_GATEWAY_API_KEY,
    [string]$EmbeddingGatewayModel = $(if ($env:AI_EMBEDDING_MODEL) { $env:AI_EMBEDDING_MODEL } else { "BAAI/bge-m3" }),
    [int]$TimeoutSeconds = 5,
    [switch]$RunSmoke,
    [ValidateSet("AiService", "Backend")]
    [string]$SmokeTarget = "AiService",
    [string]$SmokeMessage = "售后质量问题退回运费谁承担？",
    [string]$SmokeConversationId = "runtime-smoke"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Normalize-BaseUrl {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    if ([string]::IsNullOrWhiteSpace($Url)) {
        throw "Base URL cannot be empty."
    }

    return $Url.TrimEnd("/")
}

function Get-UrlPort {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    $uri = [System.Uri]$Url
    if ($uri.IsDefaultPort) {
        if ($uri.Scheme -eq "https") {
            return 443
        }
        return 80
    }
    return $uri.Port
}

function Get-UrlHost {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    return ([System.Uri]$Url).Host
}

function New-LightRagHeaders {
    param(
        [string]$ApiKey,
        [string]$ApiKeyHeader
    )

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($ApiKey)) {
        $headers[$ApiKeyHeader] = $ApiKey
    }
    return $headers
}

function Invoke-RuntimeHttp {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [string]$Method = "Get",
        [hashtable]$Headers = @{},
        [AllowNull()]
        [string]$Body = $null,
        [string]$ContentType = "application/json; charset=utf-8",
        [int]$TimeoutSec = 5
    )

    try {
        $params = @{
            Uri = $Url
            Method = $Method
            TimeoutSec = $TimeoutSec
            ErrorAction = "Stop"
        }
        if ($Headers.Count -gt 0) {
            $params.Headers = $Headers
        }
        if (-not [string]::IsNullOrEmpty($Body)) {
            $params.Body = $Body
            $params.ContentType = $ContentType
        }

        $response = Invoke-WebRequest @params
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Content = [string]$response.Content
            Error = $null
        }
    }
    catch {
        $statusCode = $null
        $content = ""
        $response = $null
        if ($_.Exception.PSObject.Properties.Name -contains "Response") {
            $response = $_.Exception.Response
        }
        if ($response) {
            try {
                $statusCode = [int]$response.StatusCode
            }
            catch {
                $statusCode = $null
            }

            try {
                $stream = $response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    try {
                        $content = $reader.ReadToEnd()
                    }
                    finally {
                        $reader.Dispose()
                    }
                }
            }
            catch {
                $content = ""
            }
        }

        return [pscustomobject]@{
            StatusCode = $statusCode
            Content = $content
            Error = $_.Exception.Message
        }
    }
}

function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$HostName,
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [int]$TimeoutSec = 5
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        $connected = $async.AsyncWaitHandle.WaitOne([TimeSpan]::FromSeconds($TimeoutSec))
        if ($connected) {
            $client.EndConnect($async)
            return [pscustomobject]@{
                Name = $Name
                Passed = $true
                Skipped = $false
                Detail = "$HostName`:$Port accepts TCP connections"
            }
        }

        return [pscustomobject]@{
            Name = $Name
            Passed = $false
            Skipped = $false
            Detail = "$HostName`:$Port timed out after ${TimeoutSec}s"
        }
    }
    catch {
        return [pscustomobject]@{
            Name = $Name
            Passed = $false
            Skipped = $false
            Detail = $_.Exception.Message
        }
    }
    finally {
        $client.Close()
    }
}

function Test-HttpStatus {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [hashtable]$Headers = @{},
        [int]$TimeoutSec = 5,
        [switch]$IncludeHealthSummary
    )

    $result = Invoke-RuntimeHttp -Url $Url -Headers $Headers -TimeoutSec $TimeoutSec
    $passed = $false
    if ($null -ne $result.StatusCode) {
        $passed = ($result.StatusCode -ge 200 -and $result.StatusCode -lt 400)
    }

    $detail = if ($null -ne $result.StatusCode) {
        "GET $Url -> HTTP $($result.StatusCode)"
    }
    else {
        "GET $Url failed: $($result.Error)"
    }

    if ($passed -and $IncludeHealthSummary -and -not [string]::IsNullOrWhiteSpace($result.Content)) {
        try {
            $health = $result.Content | ConvertFrom-Json
            $summaryParts = New-Object System.Collections.Generic.List[string]
            foreach ($field in @("status", "knowledgeRetriever", "indexTarget", "llmProvider", "embeddingProvider")) {
                if ($health.PSObject.Properties.Name -contains $field) {
                    $summaryParts.Add("$field=$($health.$field)") | Out-Null
                }
            }
            if ($summaryParts.Count -gt 0) {
                $detail = "$detail; $($summaryParts -join ', ')"
            }
        }
        catch {
            $detail = "$detail; health body was not JSON"
        }
    }

    return [pscustomobject]@{
        Name = $Name
        Passed = $passed
        Skipped = $false
        Detail = $detail
    }
}

function Test-EmbeddingHealth {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,
        [int]$TimeoutSec = 5
    )

    $url = "$BaseUrl/health"
    $result = Invoke-RuntimeHttp -Url $url -TimeoutSec $TimeoutSec
    $passed = ($null -ne $result.StatusCode -and $result.StatusCode -ge 200 -and $result.StatusCode -lt 300)
    $detail = if ($null -ne $result.StatusCode) {
        "GET $url -> HTTP $($result.StatusCode)"
    }
    else {
        "GET $url failed: $($result.Error)"
    }

    if ($passed -and -not [string]::IsNullOrWhiteSpace($result.Content)) {
        try {
            $health = $result.Content | ConvertFrom-Json
            $statusValue = $null
            $modelValue = $null
            if ($health.PSObject.Properties.Name -contains "status") {
                $statusValue = [string]$health.status
            }
            foreach ($field in @("model", "embeddingModel")) {
                if ($health.PSObject.Properties.Name -contains $field -and -not [string]::IsNullOrWhiteSpace([string]$health.$field)) {
                    $modelValue = [string]$health.$field
                    break
                }
            }
            if (-not $modelValue -and $health.PSObject.Properties.Name -contains "data" -and $null -ne $health.data) {
                if ($health.data.PSObject.Properties.Name -contains "model" -and -not [string]::IsNullOrWhiteSpace([string]$health.data.model)) {
                    $modelValue = [string]$health.data.model
                }
            }

            $summaryParts = New-Object System.Collections.Generic.List[string]
            if (-not [string]::IsNullOrWhiteSpace($statusValue)) {
                $summaryParts.Add("status=$statusValue") | Out-Null
            }
            if (-not [string]::IsNullOrWhiteSpace($modelValue)) {
                $summaryParts.Add("model=$modelValue") | Out-Null
            }
            if ($summaryParts.Count -gt 0) {
                $detail = "$detail; $($summaryParts -join ', ')"
            }
        }
        catch {
            $detail = "$detail; health body was not JSON"
        }
    }

    return [pscustomobject]@{
        Name = "Embedding health"
        Passed = $passed
        Skipped = $false
        Detail = $detail
    }
}

function Test-LightRagDocs {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,
        [hashtable]$Headers = @{},
        [int]$TimeoutSec = 5
    )

    foreach ($path in @("/docs", "/redoc")) {
        $url = "$BaseUrl$path"
        $result = Invoke-RuntimeHttp -Url $url -Headers $Headers -TimeoutSec $TimeoutSec
        if ($null -ne $result.StatusCode -and $result.StatusCode -ge 200 -and $result.StatusCode -lt 400) {
            return [pscustomobject]@{
                Name = "LightRAG docs"
                Passed = $true
                Skipped = $false
                Detail = "GET $url -> HTTP $($result.StatusCode)"
            }
        }
    }

    return [pscustomobject]@{
        Name = "LightRAG docs"
        Passed = $false
        Skipped = $false
        Detail = "Neither $BaseUrl/docs nor $BaseUrl/redoc returned a 2xx/3xx status"
    }
}

function Test-EmbeddingGateway {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AiServiceUrl,
        [string]$ApiKey,
        [string]$Model,
        [int]$TimeoutSec = 5
    )

    if ([string]::IsNullOrWhiteSpace($ApiKey)) {
        return [pscustomobject]@{
            Name = "AI service embedding gateway"
            Passed = $true
            Skipped = $true
            Detail = "SKIP: AI_EMBEDDING_GATEWAY_API_KEY is empty. Set env var or pass -EmbeddingGatewayApiKey to enable this check."
        }
    }

    $url = "$AiServiceUrl/v1/embeddings"
    $headers = @{
        Authorization = "Bearer $ApiKey"
    }
    $payload = @{
        input = "runtime verify"
        model = $Model
    } | ConvertTo-Json -Compress

    $result = Invoke-RuntimeHttp -Url $url -Method "Post" -Headers $headers -Body $payload -TimeoutSec $TimeoutSec
    $passed = ($null -ne $result.StatusCode -and $result.StatusCode -ge 200 -and $result.StatusCode -lt 300)
    $detail = if ($null -ne $result.StatusCode) {
        "POST $url -> HTTP $($result.StatusCode)"
    }
    else {
        "POST $url failed: $($result.Error)"
    }

    if ($passed -and -not [string]::IsNullOrWhiteSpace($result.Content)) {
        try {
            $json = $result.Content | ConvertFrom-Json
            $summaryParts = New-Object System.Collections.Generic.List[string]
            if ($json.PSObject.Properties.Name -contains "model" -and -not [string]::IsNullOrWhiteSpace([string]$json.model)) {
                $summaryParts.Add("model=$($json.model)") | Out-Null
            }
            if ($json.PSObject.Properties.Name -contains "data" -and $null -ne $json.data -and $json.data.Count -gt 0) {
                $item = $json.data[0]
                if ($item.PSObject.Properties.Name -contains "embedding" -and $null -ne $item.embedding) {
                    $summaryParts.Add("embeddingDims=$($item.embedding.Count)") | Out-Null
                }
            }
            if ($summaryParts.Count -gt 0) {
                $detail = "$detail; $($summaryParts -join ', ')"
            }
        }
        catch {
            $detail = "$detail; response body was not JSON"
        }
    }

    return [pscustomobject]@{
        Name = "AI service embedding gateway"
        Passed = $passed
        Skipped = $false
        Detail = $detail
    }
}

function Invoke-ChatSmoke {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Target,
        [Parameter(Mandatory = $true)]
        [string]$BackendUrl,
        [Parameter(Mandatory = $true)]
        [string]$AiServiceUrl,
        [Parameter(Mandatory = $true)]
        [string]$Message,
        [Parameter(Mandatory = $true)]
        [string]$ConversationId,
        [int]$TimeoutSec = 20
    )

    $url = if ($Target -eq "Backend") {
        "$BackendUrl/v1/customer-service/chat"
    }
    else {
        "$AiServiceUrl/chat"
    }

    $payload = @{
        message = $Message
        conversationId = $ConversationId
    } | ConvertTo-Json -Compress

    $result = Invoke-RuntimeHttp -Url $url -Method "Post" -Body $payload -TimeoutSec $TimeoutSec
    $passed = ($null -ne $result.StatusCode -and $result.StatusCode -ge 200 -and $result.StatusCode -lt 400)
    $detail = if ($null -ne $result.StatusCode) {
        "POST $url -> HTTP $($result.StatusCode)"
    }
    else {
        "POST $url failed: $($result.Error)"
    }

    if ($passed -and -not [string]::IsNullOrWhiteSpace($result.Content)) {
        try {
            $json = $result.Content | ConvertFrom-Json
            $chatData = if ($Target -eq "Backend" -and ($json.PSObject.Properties.Name -contains "data")) { $json.data } else { $json }
            $answerLength = 0
            if ($chatData.PSObject.Properties.Name -contains "answer" -and $null -ne $chatData.answer) {
                $answerLength = ([string]$chatData.answer).Length
            }

            $summaryParts = New-Object System.Collections.Generic.List[string]
            $summaryParts.Add("answerChars=$answerLength") | Out-Null
            foreach ($field in @("route", "sourceType", "fallbackReason")) {
                if ($chatData.PSObject.Properties.Name -contains $field -and -not [string]::IsNullOrWhiteSpace([string]$chatData.$field)) {
                    $summaryParts.Add("$field=$($chatData.$field)") | Out-Null
                }
            }
            if ($chatData.PSObject.Properties.Name -contains "citations" -and $null -ne $chatData.citations) {
                $summaryParts.Add("citations=$($chatData.citations.Count)") | Out-Null
            }

            $detail = "$detail; $($summaryParts -join ', ')"
        }
        catch {
            $detail = "$detail; response body was not JSON"
        }
    }

    return [pscustomobject]@{
        Name = "Optional $Target chat smoke"
        Passed = $passed
        Skipped = $false
        Detail = $detail
    }
}

function Write-CheckResult {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Result
    )

    $prefix = if ($Result.Skipped) { "[SKIP]" } elseif ($Result.Passed) { "[OK]" } else { "[FAIL]" }
    Write-Host "$prefix $($Result.Name): $($Result.Detail)"
}

$backendUrl = Normalize-BaseUrl -Url $BackendBaseUrl
$aiServiceUrl = Normalize-BaseUrl -Url $AiServiceBaseUrl
$lightRagUrl = Normalize-BaseUrl -Url $LightRagBaseUrl
$embeddingUrl = Normalize-BaseUrl -Url $EmbeddingBaseUrl
$lightRagHeaders = New-LightRagHeaders -ApiKey $LightRagApiKey -ApiKeyHeader $LightRagApiKeyHeader

Write-Host "ProjectKu LightRAG runtime verification"
Write-Host "Backend:    $backendUrl"
Write-Host "AI service: $aiServiceUrl"
Write-Host "LightRAG:   $lightRagUrl"
Write-Host "Embedding:  $embeddingUrl"
if ($lightRagHeaders.Count -gt 0) {
    Write-Host "LightRAG auth header: $LightRagApiKeyHeader"
}
else {
    Write-Host "LightRAG auth header: skipped; LIGHTRAG_API_KEY is empty"
}
Write-Host ""

$results = New-Object System.Collections.Generic.List[object]

$results.Add((Test-TcpPort -Name "Backend TCP" -HostName (Get-UrlHost -Url $backendUrl) -Port (Get-UrlPort -Url $backendUrl) -TimeoutSec $TimeoutSeconds)) | Out-Null
$results.Add((Test-TcpPort -Name "AI service TCP" -HostName (Get-UrlHost -Url $aiServiceUrl) -Port (Get-UrlPort -Url $aiServiceUrl) -TimeoutSec $TimeoutSeconds)) | Out-Null
$results.Add((Test-TcpPort -Name "Embedding TCP" -HostName (Get-UrlHost -Url $embeddingUrl) -Port (Get-UrlPort -Url $embeddingUrl) -TimeoutSec $TimeoutSeconds)) | Out-Null
$results.Add((Test-HttpStatus -Name "Backend root" -Url "$backendUrl/" -TimeoutSec $TimeoutSeconds)) | Out-Null
$results.Add((Test-HttpStatus -Name "AI service health" -Url "$aiServiceUrl/health" -TimeoutSec $TimeoutSeconds -IncludeHealthSummary)) | Out-Null
$results.Add((Test-EmbeddingHealth -BaseUrl $embeddingUrl -TimeoutSec $TimeoutSeconds)) | Out-Null
$results.Add((Test-LightRagDocs -BaseUrl $lightRagUrl -Headers $lightRagHeaders -TimeoutSec $TimeoutSeconds)) | Out-Null

if ($CheckEmbeddingGateway) {
    $results.Add((Test-EmbeddingGateway -AiServiceUrl $aiServiceUrl -ApiKey $EmbeddingGatewayApiKey -Model $EmbeddingGatewayModel -TimeoutSec ([Math]::Max($TimeoutSeconds, 30)))) | Out-Null
}
else {
    Write-Host "[SKIP] AI service embedding gateway check was not run. Use -CheckEmbeddingGateway to test /v1/embeddings."
}

if ($RunSmoke) {
    $results.Add((Invoke-ChatSmoke -Target $SmokeTarget -BackendUrl $backendUrl -AiServiceUrl $aiServiceUrl -Message $SmokeMessage -ConversationId $SmokeConversationId -TimeoutSec ([Math]::Max($TimeoutSeconds, 20)))) | Out-Null
}
else {
    Write-Host "[SKIP] Optional chat smoke was not run. Use -RunSmoke to send one customer-service question."
}

foreach ($result in $results) {
    Write-CheckResult -Result $result
}

$failed = @($results | Where-Object { -not $_.Skipped -and -not $_.Passed })
Write-Host ""
if ($failed.Count -eq 0) {
    Write-Host "[OK] Runtime verification passed."
    exit 0
}

Write-Host "[FAIL] Runtime verification failed: $($failed.Count) check(s) failed."
exit 1
