[CmdletBinding()]
param(
    [string]$BackendBaseUrl = $(if ($env:PROJECTKU_BACKEND_BASE_URL) { $env:PROJECTKU_BACKEND_BASE_URL } else { "http://127.0.0.1:8080/api" }),
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
        [string]$ExpectedRoute,
        [string]$ExpectedSourceType,
        [bool]$RequireNullFallbackReason = $false,
        [bool]$AllowNonNullFallbackReason = $true
    )

    return [pscustomobject]@{
        Name = $Name
        Message = $Message
        AnswerMustContain = $AnswerMustContain
        AnswerMustContainAny = $AnswerMustContainAny
        ExpectedRoute = $ExpectedRoute
        ExpectedSourceType = $ExpectedSourceType
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
            Name = "after-sales-quality-return-shipping";
            Message = (Decode-Utf8Base64 "5ZSu5ZCO6LSo6YeP6Zeu6aKY6YCA5Zue6L+Q6LS56LCB5om/5ouF");
            AnswerMustContainAny = @((Decode-Utf8Base64 "5ZWG5a625om/5ouF"), (Decode-Utf8Base64 "6L+Q6LS56YCa5bi455Sx5ZWG5a625om/5ouF"));
            ExpectedRoute = "after_sales";
            ExpectedSourceType = "knowledge";
            RequireNullFallbackReason = $true
        },
        @{
            Name = "coupon-threshold";
            Message = (Decode-Utf8Base64 "5LyY5oOg5Yi45rKh5Yiw6Zeo5qeb5Li65LuA5LmI5LiN6IO955So");
            AnswerMustContainAny = @((Decode-Utf8Base64 "6Zeo5qeb"));
            ExpectedRoute = "coupon"
        },
        @{
            Name = "logistics-stuck";
            Message = (Decode-Utf8Base64 "54mp5rWB5LiA55u05LiN5Yqo5oCO5LmI5Yqe");
            AnswerMustContainAny = @((Decode-Utf8Base64 "54mp5rWB"));
            ExpectedRoute = "logistics"
        }
    )

    return @($definitions | ForEach-Object { New-RegressionCase @_ })
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
        [string]$ConversationId
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
        $hasResponse = ($_.Exception.PSObject.Properties.Name -contains "Response") -and $null -ne $_.Exception.Response
        if ($hasResponse) {
            try {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }
            catch {
                $statusCode = $null
            }
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
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

    if (-not $Invocation.Ok) {
        $httpPart = if ($null -ne $Invocation.StatusCode) { "HTTP $($Invocation.StatusCode)" } else { "no HTTP response" }
        $detail = if ([string]::IsNullOrWhiteSpace([string]$Invocation.Body)) { $Invocation.Error } else { [string]$Invocation.Body }
        $failures.Add("request failed: $httpPart; $detail") | Out-Null
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
        Route = $route
        SourceType = $sourceType
        FallbackReason = $fallbackReason
        AnswerPreview = (Format-AnswerPreview -Answer $answer)
    }
}

$normalizedBaseUrl = Normalize-BaseUrl -Url $BackendBaseUrl
$cases = Get-RegressionCases
$results = New-Object System.Collections.Generic.List[object]

Write-Host "AI customer-service regression target: $normalizedBaseUrl/v1/customer-service/chat"
Write-Host "Cases: $($cases.Count)"

for ($i = 0; $i -lt $cases.Count; $i++) {
    $case = $cases[$i]
    $conversationId = "$ConversationIdPrefix-$($i + 1)"
    $invocation = Invoke-ChatCase -BaseUrl $normalizedBaseUrl -Case $case -TimeoutSec $TimeoutSeconds -ConversationId $conversationId
    $result = Evaluate-ChatCase -Case $case -Invocation $invocation
    $results.Add($result) | Out-Null

    $status = if ($result.Passed) { "PASS" } else { "FAIL" }
    Write-Host "[$status] $($result.Name) :: $($result.Message)"
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

Write-Host ""
Write-Host "Summary: $passedCount passed, $failedCount failed, total $($results.Count)"

if ($failedCount -gt 0) {
    exit 1
}

exit 0
