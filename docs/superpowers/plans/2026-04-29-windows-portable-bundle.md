# Windows Portable Bundle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Windows-first portable setup/start/stop/doctor workflow so this repo can be zipped, moved to another PC, and brought up with a small set of `.bat` entrypoints.

**Architecture:** Keep the current repo layout and startup model, but add a thin Windows portable layer around it. New root `.bat` launchers forward into `scripts/portable/*.ps1`, which centralize environment checks, repo-local state handling, Docker MySQL bootstrap, dependency installation, and service lifecycle control while reusing the existing `start_all.ps1` behavior where that is already correct.

**Tech Stack:** Windows batch, PowerShell 5+, Docker Compose, MySQL 8 container, Maven/Spring Boot, Node/Vite, Python/FastAPI.

---

## File Map

- Modify: `.gitignore`
- Create: `setup-portable.bat`
- Create: `start-portable.bat`
- Create: `stop-portable.bat`
- Create: `doctor-portable.bat`
- Create: `scripts/portable/common.ps1`
- Create: `scripts/portable/setup-portable.ps1`
- Create: `scripts/portable/start-portable.ps1`
- Create: `scripts/portable/stop-portable.ps1`
- Create: `scripts/portable/doctor-portable.ps1`
- Modify: `start_all.ps1`
- Modify: `README.md`
- Modify: `docs/deployment-faq.md`

### Task 1: Add the portable workspace boundaries and Windows entry wrappers

**Files:**
- Modify: `.gitignore`
- Create: `setup-portable.bat`
- Create: `start-portable.bat`
- Create: `stop-portable.bat`
- Create: `doctor-portable.bat`

- [ ] **Step 1: Write the failing smoke expectation for missing wrapper scripts**

Use this command from repo root:

```powershell
@( 'setup-portable.bat', 'start-portable.bat', 'stop-portable.bat', 'doctor-portable.bat' ) |
  ForEach-Object {
    [pscustomobject]@{
      Name = $_
      Exists = Test-Path $_
    }
  }
```

Expected before implementation: all four rows show `Exists = False`.

- [ ] **Step 2: Run the check to verify the wrappers are missing**

Run:

```powershell
@( 'setup-portable.bat', 'start-portable.bat', 'stop-portable.bat', 'doctor-portable.bat' ) |
  ForEach-Object {
    [pscustomobject]@{
      Name = $_
      Exists = Test-Path $_
    }
  }
```

Expected: FAIL against the desired state because none of the wrapper files exist yet.

- [ ] **Step 3: Add `.gitignore` coverage and create thin `.bat` forwarders**

Update `.gitignore` with the portable runtime area:

```gitignore
.portable/
```

Create `setup-portable.bat`:

```bat
@echo off
setlocal
set ROOT=%~dp0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\portable\setup-portable.ps1" %*
exit /b %ERRORLEVEL%
```

Create `start-portable.bat`:

```bat
@echo off
setlocal
set ROOT=%~dp0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\portable\start-portable.ps1" %*
exit /b %ERRORLEVEL%
```

Create `stop-portable.bat`:

```bat
@echo off
setlocal
set ROOT=%~dp0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\portable\stop-portable.ps1" %*
exit /b %ERRORLEVEL%
```

Create `doctor-portable.bat`:

```bat
@echo off
setlocal
set ROOT=%~dp0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\portable\doctor-portable.ps1" %*
exit /b %ERRORLEVEL%
```

- [ ] **Step 4: Re-run the wrapper existence check**

Run:

```powershell
@( 'setup-portable.bat', 'start-portable.bat', 'stop-portable.bat', 'doctor-portable.bat' ) |
  ForEach-Object {
    [pscustomobject]@{
      Name = $_
      Exists = Test-Path $_
    }
  }
```

Expected: all four rows show `Exists = True`.

- [ ] **Step 5: Commit the wrapper layer**

Run:

```powershell
git add .gitignore setup-portable.bat start-portable.bat stop-portable.bat doctor-portable.bat
git commit -m "feat: add windows portable wrapper scripts"
```

Expected: one commit containing only the wrapper scripts and `.gitignore` update.

### Task 2: Build shared PowerShell helpers for repo-local state, command checks, and process control

**Files:**
- Create: `scripts/portable/common.ps1`
- Test by command: `doctor-portable.bat`

- [ ] **Step 1: Write the failing doctor invocation**

Run:

```powershell
.\doctor-portable.bat
```

Expected before implementation: PowerShell fails because `scripts/portable/doctor-portable.ps1` and shared helpers do not exist.

- [ ] **Step 2: Run the command to verify the failure mode**

Run:

```powershell
.\doctor-portable.bat
```

Expected: FAIL with a file-not-found or script-not-found error.

- [ ] **Step 3: Create `scripts/portable/common.ps1` with focused shared helpers**

Create `scripts/portable/common.ps1` with the core helper surface:

```powershell
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-PortablePaths {
  param([string]$Root)

  return @{
    Root = $Root
    PortableDir = Join-Path $Root ".portable"
    PrivateDir = Join-Path $Root ".portable\\private"
    SetupMarker = Join-Path $Root ".portable\\setup-complete.json"
    LogsDir = Join-Path $Root "logs"
    PidsDir = Join-Path $Root ".pids"
    DataDir = Join-Path $Root "data"
    MysqlDataDir = Join-Path $Root "mysql-data"
    AiEnvTarget = Join-Path $Root "deploy\\ai-service.env"
    AiEnvPrivate = Join-Path $Root ".portable\\private\\ai-service.env"
    BackendDir = Join-Path $Root "back"
    FrontendDir = Join-Path $Root "frontend"
    AiServiceDir = Join-Path $Root "ai-service"
  }
}

function Ensure-PortableDirectories {
  param([hashtable]$Paths)

  @( $Paths.PortableDir, $Paths.PrivateDir, $Paths.LogsDir, $Paths.PidsDir, $Paths.DataDir ) |
    ForEach-Object { New-Item -ItemType Directory -Force -Path $_ | Out-Null }
}

function Test-CommandExists {
  param([string]$Name)
  return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-RequiredCommands {
  $names = @("docker", "java", "mvn", "node", "npm", "python")
  foreach ($name in $names) {
    [pscustomobject]@{
      Name = $name
      Exists = Test-CommandExists -Name $name
    }
  }
}

function Test-PortableSetupComplete {
  param([hashtable]$Paths)
  return Test-Path $Paths.SetupMarker
}

function Write-PortableSetupMarker {
  param([hashtable]$Paths)

  $payload = @{
    completedAt = (Get-Date).ToString("s")
    machineName = $env:COMPUTERNAME
  } | ConvertTo-Json

  Set-Content -Path $Paths.SetupMarker -Value $payload -Encoding UTF8
}

function Get-PidFilePath {
  param([hashtable]$Paths, [string]$Name)
  return Join-Path $Paths.PidsDir "$Name.pid"
}

function Stop-ProcessFromPidFile {
  param([hashtable]$Paths, [string]$Name)

  $pidFile = Get-PidFilePath -Paths $Paths -Name $Name
  if (-not (Test-Path $pidFile)) { return }
  $pidValue = Get-Content $pidFile | Select-Object -First 1
  if ($pidValue) {
    $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
    if ($process) { Stop-Process -Id $pidValue -Force }
  }
  Remove-Item -Force -ErrorAction SilentlyContinue $pidFile
}
```

- [ ] **Step 4: Add a temporary lightweight doctor script that imports the helpers**

Create `scripts/portable/doctor-portable.ps1` as the first consumer:

```powershell
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\\portable\\common.ps1")

$paths = Get-PortablePaths -Root $Root
Ensure-PortableDirectories -Paths $paths

Write-Host "Portable root: $Root"
Write-Host "Setup complete: $(Test-PortableSetupComplete -Paths $paths)"
Test-RequiredCommands | Format-Table -AutoSize
```

Run:

```powershell
.\doctor-portable.bat
```

Expected: PASS and prints command availability plus setup marker status.

- [ ] **Step 5: Commit the shared helper foundation**

Run:

```powershell
git add scripts/portable/common.ps1 scripts/portable/doctor-portable.ps1
git commit -m "feat: add portable powershell common helpers"
```

Expected: one commit containing only the common helper layer and the first working doctor script.

### Task 3: Implement first-run setup for config copy, Docker MySQL bootstrap, dependency install, and setup marker

**Files:**
- Create: `scripts/portable/setup-portable.ps1`
- Modify: `scripts/portable/common.ps1`
- Test by command: `setup-portable.bat`

- [ ] **Step 1: Write the failing setup-path check**

Run:

```powershell
.\setup-portable.bat
```

Expected before implementation: FAIL because `scripts/portable/setup-portable.ps1` does not exist.

- [ ] **Step 2: Run the setup command to verify the failure**

Run:

```powershell
.\setup-portable.bat
```

Expected: FAIL with a script-not-found error.

- [ ] **Step 3: Extend helpers for env copy, MySQL readiness, and dependency install**

Add these helpers to `scripts/portable/common.ps1`:

```powershell
function Ensure-AiServiceEnv {
  param([hashtable]$Paths)

  if (Test-Path $Paths.AiEnvTarget) { return }
  if (Test-Path $Paths.AiEnvPrivate) {
    Copy-Item $Paths.AiEnvPrivate $Paths.AiEnvTarget
    return
  }
  Copy-Item (Join-Path $Paths.Root "deploy\\ai-service.env.example") $Paths.AiEnvTarget
}

function Start-PortableMysql {
  param([hashtable]$Paths)

  Push-Location $Paths.Root
  try {
    docker compose up -d mysql
  }
  finally {
    Pop-Location
  }
}

function Wait-PortableMysqlReady {
  param([int]$TimeoutSeconds = 120)

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $status = docker inspect -f "{{.State.Health.Status}}" projectku-mysql 2>$null
    if ($LASTEXITCODE -eq 0 -and $status -eq "healthy") { return }
    Start-Sleep -Seconds 3
  }
  throw "MySQL did not become healthy within $TimeoutSeconds seconds."
}

function Invoke-FrontendInstall {
  param([hashtable]$Paths)

  Push-Location $Paths.FrontendDir
  try { npm install }
  finally { Pop-Location }
}

function Invoke-AiServiceInstall {
  param([hashtable]$Paths)

  Push-Location $Paths.AiServiceDir
  try {
    python -m pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com
  }
  finally { Pop-Location }
}

function Invoke-DatabaseInit {
  param([hashtable]$Paths)

  $sqlPath = Join-Path $Paths.Root "back\\sql\\init_db.sql"
  Get-Content $sqlPath -Raw |
    docker exec -i projectku-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 web
}
```

Create `scripts/portable/setup-portable.ps1`:

```powershell
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\\portable\\common.ps1")

$paths = Get-PortablePaths -Root $Root
Ensure-PortableDirectories -Paths $paths

$missing = @(Test-RequiredCommands | Where-Object { -not $_.Exists })
if ($missing.Count -gt 0) {
  $names = ($missing.Name -join ", ")
  throw "Missing required commands: $names"
}

Ensure-AiServiceEnv -Paths $paths
Start-PortableMysql -Paths $paths
Wait-PortableMysqlReady
Invoke-DatabaseInit -Paths $paths
Invoke-FrontendInstall -Paths $paths
Invoke-AiServiceInstall -Paths $paths
Write-PortableSetupMarker -Paths $paths

Write-Host "Portable setup complete."
```

- [ ] **Step 4: Run setup from a clean marker state**

Run:

```powershell
if (Test-Path .portable\setup-complete.json) { Remove-Item .portable\setup-complete.json -Force }
.\setup-portable.bat
```

Expected: PASS. It should create `.portable\setup-complete.json`, ensure `deploy\ai-service.env` exists, start `projectku-mysql`, initialize the `web` database, and install frontend plus AI-service dependencies.

- [ ] **Step 5: Commit the first-run setup flow**

Run:

```powershell
git add scripts/portable/common.ps1 scripts/portable/setup-portable.ps1
git commit -m "feat: add portable setup workflow"
```

Expected: one commit containing the setup orchestration and common helper extensions.

### Task 4: Implement daily start, stop, and full doctor output while reusing current startup behavior

**Files:**
- Create: `scripts/portable/start-portable.ps1`
- Create: `scripts/portable/stop-portable.ps1`
- Modify: `scripts/portable/doctor-portable.ps1`
- Modify: `start_all.ps1`

- [ ] **Step 1: Write the failing daily-start invocation**

Run:

```powershell
.\start-portable.bat
```

Expected before implementation: FAIL because `scripts/portable/start-portable.ps1` does not exist.

- [ ] **Step 2: Run the start command to verify the failure**

Run:

```powershell
.\start-portable.bat
```

Expected: FAIL with a script-not-found error.

- [ ] **Step 3: Add reusable flags to `start_all.ps1` so portable start can reuse it cleanly**

Modify `start_all.ps1` parameter block to include:

```powershell
[switch]$NoInstall,
[switch]$NoSeed,
[switch]$Portable
```

Guard the existing install/seed flow with the new flags:

```powershell
if ($InstallAiDeps -and -not $NoInstall) {
  Push-Location $AiServiceDir
  python -m pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com
  Pop-Location
}

if ($SeedAiKb -and -not $NoSeed) {
  Push-Location $AiServiceDir
  python app/ingest/sync_job.py
  Pop-Location
}
```

When `$Portable` is set, add one line before launch:

```powershell
Write-Host "Portable mode enabled."
```

- [ ] **Step 4: Create start, stop, and richer doctor scripts**

Create `scripts/portable/start-portable.ps1`:

```powershell
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\\portable\\common.ps1")

$paths = Get-PortablePaths -Root $Root
if (-not (Test-PortableSetupComplete -Paths $paths)) {
  throw "Portable setup has not completed. Run setup-portable.bat first."
}

Start-PortableMysql -Paths $paths

Push-Location $Root
try {
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\\start_all.ps1" -Mode dev -Portable -NoInstall -NoSeed
}
finally {
  Pop-Location
}
```

Create `scripts/portable/stop-portable.ps1`:

```powershell
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\\portable\\common.ps1")

$paths = Get-PortablePaths -Root $Root
@("frontend", "backend", "ai-service") | ForEach-Object {
  Stop-ProcessFromPidFile -Paths $paths -Name $_
}

Push-Location $paths.Root
try {
  docker compose stop mysql
}
finally {
  Pop-Location
}

Write-Host "Portable services stopped."
```

Replace `scripts/portable/doctor-portable.ps1` with:

```powershell
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "scripts\\portable\\common.ps1")

$paths = Get-PortablePaths -Root $Root
Ensure-PortableDirectories -Paths $paths

$ports = 3306, 5173, 8080, 9000 | ForEach-Object {
  [pscustomobject]@{
    Port = $_
    Listening = [bool](Get-NetTCPConnection -LocalPort $_ -State Listen -ErrorAction SilentlyContinue)
  }
}

Write-Host "Portable root: $Root"
Write-Host "Setup complete: $(Test-PortableSetupComplete -Paths $paths)"
Write-Host "AI env present: $(Test-Path $paths.AiEnvTarget)"
Test-RequiredCommands | Format-Table -AutoSize
$ports | Format-Table -AutoSize
```

- [ ] **Step 5: Run the end-to-end portable lifecycle check**

Run:

```powershell
.\start-portable.bat
Start-Sleep -Seconds 15
.\doctor-portable.bat
.\stop-portable.bat
```

Expected: PASS. `doctor-portable.bat` should report setup complete, `deploy\ai-service.env` present, MySQL and app ports listening while services are up, and `stop-portable.bat` should shut down frontend/backend/AI plus stop Docker MySQL.

- [ ] **Step 6: Commit the service lifecycle scripts**

Run:

```powershell
git add start_all.ps1 scripts/portable/start-portable.ps1 scripts/portable/stop-portable.ps1 scripts/portable/doctor-portable.ps1
git commit -m "feat: add portable lifecycle scripts"
```

Expected: one commit containing start/stop/doctor behavior and the small `start_all.ps1` reuse hook.

### Task 5: Document zip packaging, first-run steps, and recovery

**Files:**
- Modify: `README.md`
- Modify: `docs/deployment-faq.md`

- [ ] **Step 1: Write the failing documentation lookup**

Run:

```powershell
rg -n "setup-portable|start-portable|doctor-portable|zip" README.md docs/deployment-faq.md
```

Expected before implementation: no portable bundle instructions are found.

- [ ] **Step 2: Run the lookup to verify the docs gap**

Run:

```powershell
rg -n "setup-portable|start-portable|doctor-portable|zip" README.md docs/deployment-faq.md
```

Expected: FAIL against desired coverage because the portable workflow is not documented yet.

- [ ] **Step 3: Add a Windows portable section to `README.md` and a targeted FAQ entry**

Add this section to `README.md` near local startup guidance:

```markdown
## Windows Portable Bundle

Use this path when you want to move the project to another Windows PC for private self-use.

Prerequisites on the target PC:

- Docker Desktop
- Java 17 + Maven
- Node.js 20 + npm
- Python 3.11+

Steps:

1. unzip the project to a non-system path such as `D:\web-main`
2. run `setup-portable.bat`
3. after setup completes, run `start-portable.bat`
4. use `doctor-portable.bat` for environment checks
5. use `stop-portable.bat` to stop the local services

Portable runtime state stays under repo-local paths such as `.portable/`, `.pids/`, `logs/`, `data/`, and `mysql-data/`.
```

Add this FAQ entry to `docs/deployment-faq.md`:

```markdown
## How do I move this project to another Windows PC and keep setup simple?

Use the portable workflow:

1. copy or unzip the repo to the target PC
2. keep your private `deploy/ai-service.env` or `.portable/private/ai-service.env`
3. run `setup-portable.bat`
4. run `start-portable.bat`

If startup fails, run `doctor-portable.bat` first to check command availability, setup marker state, env-file presence, and port listeners.
```

- [ ] **Step 4: Re-run the documentation lookup**

Run:

```powershell
rg -n "setup-portable|start-portable|doctor-portable|zip" README.md docs/deployment-faq.md
```

Expected: PASS and the command prints the new portable-bundle instructions.

- [ ] **Step 5: Commit the documentation updates**

Run:

```powershell
git add README.md docs/deployment-faq.md
git commit -m "docs: add windows portable workflow guide"
```

Expected: one documentation-only commit.

### Task 6: Run the final verification sweep for the portable Windows flow

**Files:**
- No production file changes

- [ ] **Step 1: Run the wrapper and doctor checks**

Run:

```powershell
.\doctor-portable.bat
```

Expected: PASS and prints command availability, setup marker status, env presence, and port visibility.

- [ ] **Step 2: Re-run first-time setup idempotently**

Run:

```powershell
.\setup-portable.bat
```

Expected: PASS. Existing env file is preserved, dependency install is repeatable, MySQL stays healthy, and setup marker remains valid.

- [ ] **Step 3: Start the portable stack and verify health**

Run:

```powershell
.\start-portable.bat
Start-Sleep -Seconds 20
Invoke-RestMethod -Uri "http://127.0.0.1:9000/health"
Invoke-WebRequest -Uri "http://127.0.0.1:5173/" -UseBasicParsing | Select-Object -ExpandProperty StatusCode
Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/v1/products?page=1&size=1" -UseBasicParsing | Select-Object -ExpandProperty StatusCode
```

Expected: AI health responds, frontend returns `200`, backend products API returns `200`.

- [ ] **Step 4: Verify a known AI customer-service product query**

Run:

```powershell
$body = @{ message = '你好，苹果15Pro多少钱？'; conversationId = 'portable-final-check' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/v1/customer-service/chat' -ContentType 'application/json' -Body $body
```

Expected: PASS and the response mentions a matching `iPhone 15 Pro` product plus price information.

- [ ] **Step 5: Stop the stack and verify shutdown**

Run:

```powershell
.\stop-portable.bat
Start-Sleep -Seconds 5
.\doctor-portable.bat
```

Expected: frontend/backend/AI pid-managed processes are stopped and doctor no longer reports the application ports as listening.
