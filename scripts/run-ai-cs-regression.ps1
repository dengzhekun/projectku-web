[CmdletBinding()]
param(
    [string]$BackendBaseUrl = $(if ($env:PROJECTKU_BACKEND_BASE_URL) { $env:PROJECTKU_BACKEND_BASE_URL } else { "http://127.0.0.1:8080/api" }),
    [string]$LightRagBaseUrl = $(if ($env:LIGHTRAG_BASE_URL) { $env:LIGHTRAG_BASE_URL } else { "http://127.0.0.1:19621" }),
    [string]$LightRagApiKey = $env:LIGHTRAG_API_KEY,
    [string]$LightRagApiKeyHeader = $(if ($env:LIGHTRAG_API_KEY_HEADER) { $env:LIGHTRAG_API_KEY_HEADER } else { "X-API-Key" }),
    [switch]$SkipLightRagCheck,
    [int]$TimeoutSeconds = 20,
    [string]$ConversationIdPrefix = "ai-cs-regression"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Decode-Utf8Base64 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $bytes = [System.Convert]::FromBase64String($Value)
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Normalize-BaseUrl {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    if ([string]::IsNullOrWhiteSpace($Url)) {
        throw "BackendBaseUrl cannot be empty."
    }

    return $Url.TrimEnd("/")
}

function New-RegressionCase {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Message,
        [string[]]$AnswerMustContain = @(),
        [string[]]$AnswerMustContainAny = @(),
        [hashtable]$Headers = @{},
        [int]$ExpectedStatusCode = 200,
        [string]$ExpectedRoute,
        [string]$ExpectedSourceType,
        [bool]$RequiresKnowledgeRuntime = $false,
        [bool]$RequireNullFallbackReason = $false,
        [bool]$AllowNonNullFallbackReason = $true
    )

    return [pscustomobject]@{
        Name = $Name
        Message = $Message
        AnswerMustContain = $AnswerMustContain
        AnswerMustContainAny = $AnswerMustContainAny
        Headers = $Headers
        ExpectedStatusCode = $ExpectedStatusCode
        ExpectedRoute = $ExpectedRoute
        ExpectedSourceType = $ExpectedSourceType
        RequiresKnowledgeRuntime = $RequiresKnowledgeRuntime
        RequireNullFallbackReason = $RequireNullFallbackReason
        AllowNonNullFallbackReason = $AllowNonNullFallbackReason
    }
}

function Get-RegressionCases {
    $definitions = @(
        @{
            Name = "apple-ambiguous-price";
            Message = (Decode-Utf8Base64 "6Iu55p6c5aSa5bCR6ZKx");
            AnswerMustContain = @((Decode-Utf8Base64 "6Iu55p6c"));
            AnswerMustContainAny = @((Decode-Utf8Base64 "5rC05p6c6Iu55p6c"), (Decode-Utf8Base64 "6K+N5aSq5a69"));
            ExpectedRoute = "product";
            ExpectedSourceType = "product";
            AllowNonNullFallbackReason = $true
        },
        @{
            Name = "apple-15-concrete-product-price";
            Message = (Decode-Utf8Base64 "6Iu55p6cMTXlpJrlsJHpkrE=");
            AnswerMustContainAny = @((Decode-Utf8Base64 "aVBob25lIDE1"), (Decode-Utf8Base64 "6Iu55p6cMTU="), (Decode-Utf8Base64 "5a6e6ZmF5Lu35qC8"));
            ExpectedRoute = "product";
            ExpectedSourceType = "product";
            AllowNonNullFallbackReason = $true
        },
        @{
            Name = "apple-15-pro-variant-clarification";
            Message = (Decode-Utf8Base64 "6Iu55p6cMTVQcm/lpJrlsJHpkrE=");
            AnswerMustContainAny = @((Decode-Utf8Base64 "5YaF5a2Y"), (Decode-Utf8Base64 "6KeE5qC8"), (Decode-Utf8Base64 "5a656YeP"));
            AnswerMustContain = @((Decode-Utf8Base64 "5LiN56Gu5a6a"), (Decode-Utf8Base64 "6K+35ZGK6K+J5oiR"));
            ExpectedRoute = "product";
            ExpectedSourceType = "product";
            AllowNonNullFallbackReason = $true
        },
        @{
            Name = "after-sales-quality-return-shipping";
            Message = (Decode-Utf8Base64 "5ZSu5ZCO6LSo6YeP6Zeu6aKY6YCA5Zue6L+Q6LS56LCB5om/5ouF");
            AnswerMustContainAny = @((Decode-Utf8Base64 "5ZWG5a625om/5ouF"), (Decode-Utf8Base64 "6L+Q6LS56YCa5bi455Sx5ZWG5a625om/5ouF"));
            ExpectedRoute = "after_sales";
            ExpectedSourceType = "knowledge";
            RequiresKnowledgeRuntime = $true;
            RequireNullFallbackReason = $true
        },
        @{
            Name = "after-sales-eligible-order-status";
            Message = (Decode-Utf8Base64 "5LuA5LmI6K6i5Y2V5Y+v5Lul55Sz6K+35ZSu5ZCO");
            AnswerMustContainAny = @((Decode-Utf8Base64 "5bey5pSv5LuY"), (Decode-Utf8Base64 "5bey5Y+R6LSn"), (Decode-Utf8Base64 "5bey5a6M5oiQ"));
            ExpectedRoute = "after_sales";
            ExpectedSourceType = "knowledge";
            RequiresKnowledgeRuntime = $true;
            RequireNullFallbackReason = $true
        },
        @{
            Name = "coupon-threshold";
            Message = (Decode-Utf8Base64 "5LyY5oOg5Yi45rKh5Yiw6Zeo5qeb5Li65LuA5LmI5LiN6IO955So");
            AnswerMustContainAny = @((Decode-Utf8Base64 "6Zeo5qeb"));
            ExpectedRoute = "coupon";
            RequiresKnowledgeRuntime = $true
        },
        @{
            Name = "logistics-stuck";
            Message = (Decode-Utf8Base64 "54mp5rWB5LiA55u05LiN5Yqo5oCO5LmI5Yqe");
            AnswerMustContainAny = @((Decode-Utf8Base64 "54mp5rWB"));
            ExpectedRoute = "logistics";
            RequiresKnowledgeRuntime = $true
        },
        @{
            Name = "wallet-login-required";
            Message = (Decode-Utf8Base64 "5oiR55qE5L2Z6aKd5piv5aSa5bCR");
            AnswerMustContainAny = @((Decode-Utf8Base64 "55m75b2V"));
            ExpectedRoute = "wallet";
            ExpectedSourceType = "business";
            AllowNonNullFallbackReason = $true
        },
        @{
            Name = "order-login-required";
            Message = (Decode-Utf8Base64 "5oiR55qE6K6i5Y2V5Yiw5ZOq5LqG");
            AnswerMustContainAny = @((Decode-Utf8Base64 "55m75b2V"));
            ExpectedRoute = "order";
            ExpectedSourceType = "business";
            AllowNonNullFallbackReason = $true
        },
        @{
            Name = "order-forged-token-should-401";
            Message = (Decode-Utf8Base64 "5oiR55qE6K6i5Y2V5Yiw5ZOq5LqG");
            Headers = @{
                Authorization = "Bearer fake-token"
            };
            ExpectedStatusCode = 401
        }
    )

    return @($definitions | ForEach-Object { New-RegressionCase @_ })
}

function Test-LightRagRuntime {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,
        [string]$ApiKey,
        [string]$ApiKeyHeader,
        [int]$TimeoutSec
    )

    $normalized = Normalize-BaseUrl -Url $BaseUrl
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($ApiKey)) {
        $headers[$ApiKeyHeader] = $ApiKey
    }

    $lastError = $null
    foreach ($path in @("/docs", "/redoc")) {
        try {
            $params = @{
                Uri = "$normalized$path"
                Method = "Get"
                TimeoutSec = [Math]::Max(1, [Math]::Min($TimeoutSec, 10))
                ErrorAction = "Stop"
            }
            if ($headers.Count -gt 0) {
                $params.Headers = $headers
            }
            $response = Invoke-WebRequest @params
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                return [pscustomobject]@{
                    Reachable = $true
                    Url = "$normalized$path"
                    StatusCode = [int]$response.StatusCode
                    Error = $null
                }
            }
        }
        catch {
            $lastError = $_.Exception.Message
        }
    }

    return [pscustomobject]@{
        Reachable = $false
        Url = $normalized
        StatusCode = $null
        Error = $lastError
    }
}

function Invoke-ChatCase {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Case,
        [Parameter(Mandatory = $true)]
        [int]$TimeoutSec,
        [Parameter(Mandatory = $true)]
        [string]$ConversationId,
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl/v1/customer-service/chat"
    $payload = @{
        message = $Case.Message
        conversationId = $ConversationId
    } | ConvertTo-Json -Depth 4

    try {
        $request = [System.Net.HttpWebRequest]::Create($uri)
        $request.Method = "POST"
        $request.ContentType = "application/json; charset=utf-8"
        $request.Accept = "application/json"
        $request.Timeout = $TimeoutSec * 1000
        $request.ReadWriteTimeout = $TimeoutSec * 1000
        foreach ($headerName in $Headers.Keys) {
            $request.Headers[$headerName] = $Headers[$headerName]
        }

        $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
        $request.ContentLength = $payloadBytes.Length

        $requestStream = $request.GetRequestStream()
        try {
            $requestStream.Write($payloadBytes, 0, $payloadBytes.Length)
        }
        finally {
            $requestStream.Dispose()
        }

        $responseMessage = [System.Net.HttpWebResponse]$request.GetResponse()
        try {
            $statusCode = [int]$responseMessage.StatusCode
            $responseStream = $responseMessage.GetResponseStream()
            try {
                $reader = New-Object System.IO.StreamReader($responseStream, [System.Text.Encoding]::UTF8)
                try {
                    $bodyText = $reader.ReadToEnd()
                }
                finally {
                    $reader.Dispose()
                }
            }
            finally {
                if ($null -ne $responseStream) {
                    $responseStream.Dispose()
                }
            }
        }
        finally {
            $responseMessage.Dispose()
        }

        $response = $bodyText | ConvertFrom-Json
        return [pscustomobject]@{
            Ok = $true
            StatusCode = $statusCode
            Body = $response
            Error = $null
        }
    }
    catch {
        $statusCode = $null
        $bodyText = $null
        $responseObj = $null
        if (($_.Exception.PSObject.Properties.Name -contains "Response") -and $null -ne $_.Exception.Response) {
            $responseObj = $_.Exception.Response
        }
        elseif ($null -ne $_.Exception.InnerException -and ($_.Exception.InnerException.PSObject.Properties.Name -contains "Response") -and $null -ne $_.Exception.InnerException.Response) {
            $responseObj = $_.Exception.InnerException.Response
        }
        $hasResponse = $null -ne $responseObj
        if ($hasResponse) {
            try {
                $statusCode = [int]$responseObj.StatusCode
            }
            catch {
                $statusCode = $null
            }
            try {
                $reader = New-Object System.IO.StreamReader($responseObj.GetResponseStream(), [System.Text.Encoding]::UTF8)
                try {
                    $bodyText = $reader.ReadToEnd()
                }
                finally {
                    $reader.Dispose()
                }
            }
            catch {
                $bodyText = $null
            }
        }

        return [pscustomobject]@{
            Ok = $false
            StatusCode = $statusCode
            Body = $bodyText
            Error = $_.Exception.Message
        }
    }
}

function Test-ContainsAny {
    param(
        [string]$Text,
        [string[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and $Text.Contains($candidate)) {
            return $true
        }
    }
    return $false
}

function Format-AnswerPreview {
    param(
        [string]$Answer
    )

    if ([string]::IsNullOrWhiteSpace($Answer)) {
        return ""
    }

    $singleLine = ($Answer -replace "\r?\n", " ").Trim()
    if ($singleLine.Length -le 120) {
        return $singleLine
    }

    return $singleLine.Substring(0, 120) + "..."
}

function Evaluate-ChatCase {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Case,
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Invocation
    )

    $failures = New-Object System.Collections.Generic.List[string]
    $route = $null
    $sourceType = $null
    $fallbackReason = $null
    $answer = ""
    $statusCode = $Invocation.StatusCode

    if ($null -eq $statusCode) {
        $failures.Add("missing HTTP status code") | Out-Null
    }
    elseif ($statusCode -ne $Case.ExpectedStatusCode) {
        $failures.Add("expected HTTP $($Case.ExpectedStatusCode), got HTTP $statusCode") | Out-Null
    }

    if (-not $Invocation.Ok) {
        if ($Case.ExpectedStatusCode -ge 400 -and $Invocation.StatusCode -eq $Case.ExpectedStatusCode) {
            $responseText = [string]$Invocation.Body
            if ([string]::IsNullOrWhiteSpace($responseText)) {
                $responseText = [string]$Invocation.Error
            }
            if ($Case.ExpectedStatusCode -ne 401 -and -not [string]::IsNullOrWhiteSpace($responseText)) {
                if (-not (Test-ContainsAny -Text $responseText -Candidates @("401", "Unauthorized", "登录", "login"))) {
                    $failures.Add("expected unauthorized/login hint in response body") | Out-Null
                }
            }
        }
        else {
            $httpPart = if ($null -ne $Invocation.StatusCode) { "HTTP $($Invocation.StatusCode)" } else { "no HTTP response" }
            $detail = if ([string]::IsNullOrWhiteSpace([string]$Invocation.Body)) { $Invocation.Error } else { [string]$Invocation.Body }
            $failures.Add("request failed: $httpPart; $detail") | Out-Null
        }
    }
    else {
        $body = $Invocation.Body
        $propertyNames = @()
        if ($null -ne $body -and $null -ne $body.PSObject) {
            $propertyNames = @($body.PSObject.Properties.Name)
        }

        if ($propertyNames -contains "answer") {
            $answer = [string]$body.answer
        }
        elseif ($propertyNames -contains "data" -and $null -ne $body.data -and $body.data.PSObject.Properties.Name -contains "answer") {
            $body = $body.data
            $propertyNames = @($body.PSObject.Properties.Name)
            $answer = [string]$body.answer
        }
        else {
            $serialized = ""
            try {
                $serialized = $Invocation.Body | ConvertTo-Json -Depth 6 -Compress
            }
            catch {
                $serialized = [string]$Invocation.Body
            }
            $failures.Add("response missing answer field: $serialized") | Out-Null
        }

        $route = if ($propertyNames -contains "route") { [string]$body.route } else { $null }
        $sourceType = if ($propertyNames -contains "sourceType") { [string]$body.sourceType } else { $null }
        $fallbackReason = if ($propertyNames -contains "fallbackReason") { [string]$body.fallbackReason } else { $null }

        foreach ($expected in $Case.AnswerMustContain) {
            if (-not [string]::IsNullOrWhiteSpace($expected) -and -not $answer.Contains($expected)) {
                $failures.Add("answer missing '$expected'") | Out-Null
            }
        }

        if ($Case.AnswerMustContainAny.Count -gt 0 -and -not (Test-ContainsAny -Text $answer -Candidates $Case.AnswerMustContainAny)) {
            $failures.Add("answer missing any of: $($Case.AnswerMustContainAny -join ', ')") | Out-Null
        }

        if (-not [string]::IsNullOrWhiteSpace($Case.ExpectedRoute)) {
            $routeMatches = ($route -eq $Case.ExpectedRoute)
            $answerMatches = Test-ContainsAny -Text $answer -Candidates $Case.AnswerMustContainAny
            if (-not $routeMatches -and -not $answerMatches) {
                $failures.Add("expected route '$($Case.ExpectedRoute)' or answer to include one of: $($Case.AnswerMustContainAny -join ', ')") | Out-Null
            }
        }

        if (-not [string]::IsNullOrWhiteSpace($Case.ExpectedSourceType) -and $sourceType -ne $Case.ExpectedSourceType) {
            $failures.Add("expected sourceType '$($Case.ExpectedSourceType)', got '$sourceType'") | Out-Null
        }

        if ($Case.RequireNullFallbackReason -and -not [string]::IsNullOrWhiteSpace($fallbackReason)) {
            $failures.Add("expected fallbackReason to be null/empty, got '$fallbackReason'") | Out-Null
        }

        if (-not $Case.AllowNonNullFallbackReason -and -not [string]::IsNullOrWhiteSpace($fallbackReason)) {
            $failures.Add("fallbackReason must be empty, got '$fallbackReason'") | Out-Null
        }
    }

    return [pscustomobject]@{
        Name = $Case.Name
        Message = $Case.Message
        Passed = ($failures.Count -eq 0)
        Failures = @($failures)
        StatusCode = $statusCode
        Route = $route
        SourceType = $sourceType
        FallbackReason = $fallbackReason
        AnswerPreview = (Format-AnswerPreview -Answer $answer)
    }
}

$normalizedBaseUrl = Normalize-BaseUrl -Url $BackendBaseUrl
$cases = Get-RegressionCases
$results = New-Object System.Collections.Generic.List[object]
$lightRagRuntime = $null

Write-Host "AI customer-service regression target: $normalizedBaseUrl/v1/customer-service/chat"
Write-Host "Cases: $($cases.Count)"
if ($SkipLightRagCheck) {
    Write-Host "LightRAG preflight: skipped by -SkipLightRagCheck"
} else {
    $lightRagRuntime = Test-LightRagRuntime -BaseUrl $LightRagBaseUrl -ApiKey $LightRagApiKey -ApiKeyHeader $LightRagApiKeyHeader -TimeoutSec $TimeoutSeconds
    if ($lightRagRuntime.Reachable) {
        Write-Host "LightRAG preflight: reachable at $($lightRagRuntime.Url) -> $($lightRagRuntime.StatusCode)"
    } else {
        Write-Host "LightRAG preflight: NOT reachable at $($lightRagRuntime.Url)"
        if (-not [string]::IsNullOrWhiteSpace($lightRagRuntime.Error)) {
            Write-Host "  $($lightRagRuntime.Error)"
        }
        Write-Host "  Knowledge cases are expected to fail until LightRAG is started and indexed."
    }
}

for ($i = 0; $i -lt $cases.Count; $i++) {
    $case = $cases[$i]
    $conversationId = "$ConversationIdPrefix-$($i + 1)"
    $invocation = Invoke-ChatCase -BaseUrl $normalizedBaseUrl -Case $case -TimeoutSec $TimeoutSeconds -ConversationId $conversationId -Headers $case.Headers
    $result = Evaluate-ChatCase -Case $case -Invocation $invocation
    $results.Add($result) | Out-Null

    $status = if ($result.Passed) { "PASS" } else { "FAIL" }
    Write-Host "[$status] $($result.Name) :: $($result.Message)"
    if ($null -ne $result.StatusCode) {
        Write-Host "  status=$($result.StatusCode)"
    }
    if ($result.Route -or $result.SourceType -or $result.FallbackReason) {
        Write-Host "  route=$($result.Route); sourceType=$($result.SourceType); fallbackReason=$($result.FallbackReason)"
    }
    if ($result.AnswerPreview) {
        Write-Host "  answer=$($result.AnswerPreview)"
    }
    foreach ($failure in $result.Failures) {
        Write-Host "  $failure"
    }
}

$passedCount = @($results | Where-Object { $_.Passed }).Count
$failedCount = $results.Count - $passedCount
$transportFailureCount = @($results | Where-Object { (-not $_.Passed) -and $null -eq $_.StatusCode }).Count
$allFailuresAreTransport = ($failedCount -gt 0 -and $transportFailureCount -eq $failedCount)
$knowledgeRequiredFailures = @(
    foreach ($result in $results) {
        $case = @($cases | Where-Object { $_.Name -eq $result.Name })[0]
        if ($case -and $case.RequiresKnowledgeRuntime -and -not $result.Passed) {
            $result
        }
    }
)

Write-Host ""
Write-Host "Summary: $passedCount passed, $failedCount failed, total $($results.Count)"
if ($allFailuresAreTransport) {
    Write-Host "Likely cause: backend chat endpoint is not reachable at $normalizedBaseUrl/v1/customer-service/chat."
    Write-Host "Start the backend first or pass -BackendBaseUrl to the running environment."
} elseif ($knowledgeRequiredFailures.Count -gt 0) {
    Write-Host "Knowledge-dependent failures: $($knowledgeRequiredFailures.Count)"
    if ($lightRagRuntime -and -not $lightRagRuntime.Reachable) {
        Write-Host "Likely cause: LightRAG runtime is not reachable. Run:"
        Write-Host "  powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-lightrag-runtime.ps1 -RunSmoke"
    } elseif (@($knowledgeRequiredFailures | Where-Object { $_.FallbackReason -eq "Knowledge retrieval is temporarily unavailable." }).Count -gt 0) {
        Write-Host "Likely cause: backend/ai-service could not query the active knowledge retriever."
    } else {
        Write-Host "Likely cause: knowledge content, indexing, routing, or answer assertions need inspection."
    }
}

if ($failedCount -gt 0) {
    exit 1
}

exit 0
