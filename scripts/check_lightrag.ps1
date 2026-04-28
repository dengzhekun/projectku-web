param(
  [string]$BaseUrl = "http://127.0.0.1:19621",
  [string]$ApiKey = $env:LIGHTRAG_API_KEY,
  [string]$ApiKeyHeader = $(if ($env:LIGHTRAG_API_KEY_HEADER) { $env:LIGHTRAG_API_KEY_HEADER } else { "X-API-Key" }),
  [int]$TimeoutSeconds = 10
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Normalize-BaseUrl {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Url
  )

  return $Url.TrimEnd("/")
}

function Invoke-GetStatus {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Url,
    [Parameter(Mandatory = $true)]
    [int]$TimeoutSec,
    [string]$AuthApiKey,
    [string]$AuthApiKeyHeader
  )

  $headers = @{}
  if (-not [string]::IsNullOrWhiteSpace($AuthApiKey)) {
    $headers[$AuthApiKeyHeader] = $AuthApiKey
  }

  try {
    if ($headers.Count -gt 0) {
      $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec $TimeoutSec -Headers $headers -ErrorAction Stop
    } else {
      $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec $TimeoutSec -ErrorAction Stop
    }
    return @{
      StatusCode = [int]$response.StatusCode
      Error = $null
    }
  } catch [System.Net.WebException] {
    $httpResponse = $_.Exception.Response
    if ($httpResponse -and $httpResponse -is [System.Net.HttpWebResponse]) {
      return @{
        StatusCode = [int]$httpResponse.StatusCode
        Error = $_.Exception.Message
      }
    }
    return @{
      StatusCode = $null
      Error = $_.Exception.Message
    }
  } catch {
    return @{
      StatusCode = $null
      Error = $_.Exception.Message
    }
  }
}

function Test-ReachableStatus {
  param(
    [int]$StatusCode
  )

  return ($StatusCode -ge 200 -and $StatusCode -lt 400)
}

$normalizedBaseUrl = Normalize-BaseUrl -Url $BaseUrl

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
  Write-Host "[WARN] LIGHTRAG_API_KEY is empty; API key header will be skipped."
} else {
  Write-Host ("[OK] LIGHTRAG_API_KEY detected; using header {0}." -f $ApiKeyHeader)
}

$docsUrl = "$normalizedBaseUrl/docs"
$redocUrl = "$normalizedBaseUrl/redoc"

$docsResult = Invoke-GetStatus -Url $docsUrl -TimeoutSec $TimeoutSeconds -AuthApiKey $ApiKey -AuthApiKeyHeader $ApiKeyHeader
$docsReachable = $false

if ($docsResult.StatusCode -ne $null -and (Test-ReachableStatus -StatusCode $docsResult.StatusCode)) {
  $docsReachable = $true
  Write-Host ("[OK] GET /docs -> {0}" -f $docsResult.StatusCode)
} else {
  if ($docsResult.StatusCode -ne $null) {
    Write-Host ("[WARN] GET /docs -> {0}" -f $docsResult.StatusCode)
  } else {
    Write-Host ("[WARN] GET /docs failed: {0}" -f $docsResult.Error)
  }

  $redocResult = Invoke-GetStatus -Url $redocUrl -TimeoutSec $TimeoutSeconds -AuthApiKey $ApiKey -AuthApiKeyHeader $ApiKeyHeader
  if ($redocResult.StatusCode -ne $null -and (Test-ReachableStatus -StatusCode $redocResult.StatusCode)) {
    $docsReachable = $true
    Write-Host ("[OK] GET /redoc -> {0}" -f $redocResult.StatusCode)
  } else {
    if ($redocResult.StatusCode -ne $null) {
      Write-Host ("[ERROR] GET /redoc -> {0}" -f $redocResult.StatusCode)
    } else {
      Write-Host ("[ERROR] GET /redoc failed: {0}" -f $redocResult.Error)
    }
  }
}

if ($docsReachable) {
  $trackUrl = "$normalizedBaseUrl/documents/track_status/test-placeholder"
  $trackResult = Invoke-GetStatus -Url $trackUrl -TimeoutSec $TimeoutSeconds -AuthApiKey $ApiKey -AuthApiKeyHeader $ApiKeyHeader
  if ($trackResult.StatusCode -ne $null) {
    if ((Test-ReachableStatus -StatusCode $trackResult.StatusCode) -or $trackResult.StatusCode -eq 404 -or $trackResult.StatusCode -eq 422) {
      Write-Host ("[OK] GET /documents/track_status/test-placeholder -> {0} (accepted for reachability check)" -f $trackResult.StatusCode)
    } else {
      Write-Host ("[WARN] GET /documents/track_status/test-placeholder -> {0}" -f $trackResult.StatusCode)
    }
  } else {
    Write-Host ("[WARN] GET /documents/track_status/test-placeholder failed: {0}" -f $trackResult.Error)
  }

  Write-Host "[OK] LightRAG smoke check passed."
  exit 0
}

Write-Host "[ERROR] LightRAG smoke check failed: neither /docs nor /redoc was reachable."
exit 1
