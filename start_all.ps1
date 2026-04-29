param(
  [ValidateSet("dev", "prod")]
  [string]$Mode = "dev",
  [switch]$DryRun,
  [switch]$SkipDb,
  [switch]$InitDb,
  [switch]$InstallAiDeps,
  [switch]$SeedAiKb,
  [switch]$SkipAiDependencyInstall,
  [switch]$SkipKbSeed,
  [switch]$Portable
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = if (Test-Path (Join-Path $Root "back")) { Join-Path $Root "back" } else { Join-Path $Root "backend" }
$FrontendDir = Join-Path $Root "frontend"
$AiServiceDir = Join-Path $Root "ai-service"
$LogsDir = Join-Path $Root "logs"
$PidsDir = Join-Path $Root ".pids"

New-Item -ItemType Directory -Force -Path $LogsDir, $PidsDir | Out-Null

function Test-Command([string]$Name) {
  return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-Port([int]$Port) {
  return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Start-Window([string]$Name, [string]$WorkingDirectory, [string]$Command) {
  if ($DryRun) {
    Write-Host "[dry-run] $Name`: cd `"$WorkingDirectory`"; $Command"
    return
  }

  $log = Join-Path $LogsDir "$Name.log"
  $pidFile = Join-Path $PidsDir "$Name.pid"
  $escapedWorkingDirectory = $WorkingDirectory.Replace("'", "''")
  $escapedLog = $log.Replace("'", "''")
  $script = "& { Set-Location -LiteralPath '$escapedWorkingDirectory'; $Command } *>&1 | Tee-Object -FilePath '$escapedLog'"
  $process = Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoExit", "-Command", $script) -PassThru
  if ($Portable) {
    $startedAt = ""
    try {
      $startedAt = $process.StartTime.ToUniversalTime().ToString("o")
    } catch {
      $startedAt = (Get-Date).ToUniversalTime().ToString("o")
    }
    $pidData = [PSCustomObject]@{
      pid = $process.Id
      processName = $process.ProcessName
      startedAt = $startedAt
    }
    $pidData | ConvertTo-Json | Set-Content -Path $pidFile -Encoding ASCII
  } else {
    Set-Content -Path $pidFile -Value $process.Id -Encoding ASCII
  }
  Write-Host "Started $Name (pid=$($process.Id)), logs: $log"
}

function Start-DatabaseIfNeeded {
  if ($SkipDb) { return }

  if (Test-Port 3306) {
    Write-Host "MySQL already listening on 3306, skip docker mysql."
  } elseif (Test-Command "docker") {
    if ($DryRun) {
      Write-Host "[dry-run] docker compose up -d mysql"
    } else {
      Push-Location $Root
      docker compose up -d mysql
      Pop-Location
    }
  } else {
    Write-Warning "MySQL is not listening on 3306 and docker is unavailable. Start MySQL manually before backend."
  }

  if ($InitDb) {
    if (-not (Test-Command "mysql")) {
      Write-Warning "mysql client not found in this terminal PATH; skip database import."
      return
    }
    if ($DryRun) {
      Write-Host "[dry-run] mysql -uroot -p123456 web < back/sql/init_db.sql"
    } else {
      mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
      cmd /c "mysql -uroot -p123456 --default-character-set=utf8mb4 web < `"$Root\back\sql\init_db.sql`""
    }
  }
}

function Start-Dev {
  Start-DatabaseIfNeeded
  $doInstallAiDeps = $InstallAiDeps -and (-not $SkipAiDependencyInstall)
  $doSeedAiKb = $SeedAiKb -and (-not $SkipKbSeed)

  if (-not (Test-Path (Join-Path $BackendDir "pom.xml"))) {
    throw "Backend pom.xml not found: $BackendDir"
  }
  if (-not (Test-Path (Join-Path $FrontendDir "package.json"))) {
    throw "Frontend package.json not found: $FrontendDir"
  }
  if (-not (Test-Path (Join-Path $AiServiceDir "app\main.py"))) {
    throw "AI service entry not found: $AiServiceDir"
  }
  if (-not (Test-Command "mvn")) { throw "mvn not found" }
  if (-not (Test-Command "npm.cmd")) { throw "npm not found" }
  if (-not (Test-Command "python")) { throw "python not found" }

  if ($doInstallAiDeps) {
    if ($DryRun) {
      Write-Host "[dry-run] python -m pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com"
    } else {
      Push-Location $AiServiceDir
      python -m pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com
      Pop-Location
    }
  }

  if ($doSeedAiKb) {
    if ($DryRun) {
      Write-Host "[dry-run] python app/ingest/sync_job.py"
    } else {
      Push-Location $AiServiceDir
      python app/ingest/sync_job.py
      Pop-Location
    }
  }

  Start-Window "backend" $BackendDir "mvn spring-boot:run"
  $aiCommand = "python -m uvicorn app.main:app --host 127.0.0.1 --port 9000 --reload"
  if ($Portable) {
    $aiCommand = "python -m uvicorn app.main:app --host 127.0.0.1 --port 9000"
  }
  Start-Window "ai-service" $AiServiceDir $aiCommand
  Start-Window "frontend" $FrontendDir "npm run dev -- --host 127.0.0.1"
  Write-Host "Frontend: http://127.0.0.1:5173/"
  Write-Host "Backend:  http://localhost:8080/api"
  Write-Host "AI:       http://127.0.0.1:9000/health"
}

function Start-Prod {
  if (-not (Test-Command "docker")) { throw "docker not found; install/start Docker Desktop or use dev mode" }
  $envArgs = @()
  if (Test-Path (Join-Path $Root "deploy\prod.env")) {
    $envArgs += @("--env-file", "deploy/prod.env")
  }
  if ($DryRun) {
    Write-Host ("[dry-run] docker compose " + ($envArgs -join " ") + " -f docker-compose.prod.yml up -d --build")
    return
  }
  Push-Location $Root
  docker compose @envArgs -f docker-compose.prod.yml up -d --build
  Pop-Location
  Write-Host "Production entry: http://localhost/"
}

if ($Mode -eq "prod") {
  Start-Prod
} else {
  Start-Dev
}
