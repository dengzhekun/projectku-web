param(
  [string]$BaseUrl = "http://127.0.0.1:9000/v1",
  [string]$ApiKey = $env:AI_EMBEDDING_GATEWAY_API_KEY,
  [string]$Model = "BAAI/bge-m3",
  [int]$TimeoutSeconds = 30
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

function Get-HttpStatusCodeFromException {
  param(
    [Parameter(Mandatory = $true)]
    [System.Management.Automation.ErrorRecord]$ErrorRecord
  )

  $response = $ErrorRecord.Exception.Response
  if ($null -ne $response) {
    try {
      if ($response -is [System.Net.HttpWebResponse]) {
        return [int]$response.StatusCode
      }
      if ($response.StatusCode) {
        return [int]$response.StatusCode
      }
    } catch {
      return $null
    }
  }

  return $null
}

$normalizedBaseUrl = Normalize-BaseUrl -Url $BaseUrl
$endpoint = "$normalizedBaseUrl/embeddings"

$headers = @{
  "Content-Type" = "application/json"
}

if (-not [string]::IsNullOrWhiteSpace($ApiKey)) {
  $headers["Authorization"] = "Bearer $ApiKey"
  Write-Host "[OK] API key detected; Authorization header enabled."
} else {
  Write-Host "[WARN] API key is empty; sending request without Authorization header."
}

$payload = @{
  model = $Model
  input = "ping"
  encoding_format = "float"
} | ConvertTo-Json -Depth 5

try {
  $response = Invoke-WebRequest -Method Post -Uri $endpoint -Headers $headers -Body $payload -TimeoutSec $TimeoutSeconds -ErrorAction Stop
} catch {
  $statusCode = Get-HttpStatusCodeFromException -ErrorRecord $_

  if ($statusCode -eq 401) {
    Write-Host "[ERROR] HTTP 401 Unauthorized: API key mismatch or invalid key for embedding gateway."
    exit 1
  }

  if ($_.Exception -is [System.Net.WebException]) {
    $webEx = [System.Net.WebException]$_.Exception
    if ($webEx.Status -eq [System.Net.WebExceptionStatus]::ConnectFailure -or $webEx.Status -eq [System.Net.WebExceptionStatus]::NameResolutionFailure -or $webEx.Status -eq [System.Net.WebExceptionStatus]::Timeout) {
      Write-Host ("[ERROR] Request failed ({0}). Please confirm ai-service is running and reachable at {1}." -f $webEx.Status, $endpoint)
      exit 1
    }
  }

  if ($null -ne $statusCode) {
    Write-Host ("[ERROR] HTTP {0} from embedding gateway. Message: {1}" -f $statusCode, $_.Exception.Message)
  } else {
    Write-Host ("[ERROR] Request to embedding gateway failed: {0}" -f $_.Exception.Message)
    Write-Host ("[HINT] Verify ai-service is started and reachable at {0}." -f $endpoint)
  }
  exit 1
}

$statusCode = [int]$response.StatusCode
if ($statusCode -lt 200 -or $statusCode -ge 300) {
  Write-Host ("[ERROR] Unexpected HTTP status: {0}. Expected 2xx." -f $statusCode)
  exit 1
}

$body = $null
try {
  $body = $response.Content | ConvertFrom-Json
} catch {
  Write-Host ("[ERROR] Response is not valid JSON: {0}" -f $_.Exception.Message)
  exit 1
}

if ($body.object -ne "list") {
  Write-Host ("[ERROR] Invalid response: expected object='list', got '{0}'." -f $body.object)
  exit 1
}

if ($null -eq $body.data -or $body.data.Count -lt 1) {
  Write-Host "[ERROR] Invalid response: data array is empty."
  exit 1
}

$embedding = $body.data[0].embedding
if ($null -eq $embedding -or $embedding.Count -lt 1) {
  Write-Host "[ERROR] Invalid response: data[0].embedding is empty."
  exit 1
}

Write-Host ("[OK] Embedding gateway smoke check passed. model={0}, dimension={1}" -f $Model, $embedding.Count)
exit 0
