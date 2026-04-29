# Windows Portable Bundle Design

## Goal

Provide a private self-use Windows package flow for this project so a new Windows computer can:

1. unzip the project,
2. run a first-time setup batch file,
3. run a daily start batch file,
4. get frontend, backend, AI service, database, and existing private AI configuration working with minimal manual steps.

This is not a public installer. It is a pragmatic portable workspace for the project owner across multiple Windows machines.

## Why This Change

The current repository already has local startup helpers, but they still assume too much ambient setup:

- Java, Node.js, Python, Maven, Docker, and optional MySQL client must already be available,
- secrets are handled separately from startup,
- first-run database setup is not packaged into a single user action,
- runtime logs and caches can end up outside a clearly managed workspace,
- the operating model is still "developer local repo" rather than "portable private bundle".

That makes cross-machine migration slow and fragile. The target here is a repeatable first-run experience that hides most operational steps behind a small set of Windows entry scripts.

## Scope

This design covers:

1. Windows-first portable startup scripts
2. First-run environment checks
3. Dependency bootstrap for frontend and AI service
4. Database initialization through local Docker MySQL
5. Private AI config bundling for self-use migration
6. Local path normalization for logs, data, and caches
7. Simple operational scripts for start, stop, and health checks
8. Packaging guidance for creating a zip to move to another computer

This design does not cover:

- building a real installer or MSI,
- bundling offline copies of Java/Node/Python runtimes,
- making the package suitable for public redistribution,
- replacing remote LightRAG or remote embedding infrastructure with local services.

## User Experience Target

The intended user flow on a new Windows machine is:

1. unzip the project to a non-system path such as `D:\projectku-portable`,
2. double-click `setup-portable.bat`,
3. wait for dependency install, database init, and config preparation,
4. double-click `start-portable.bat`,
5. open the printed frontend/admin/backend URLs.

Daily use after first run becomes:

1. double-click `start-portable.bat`,
2. use the app,
3. double-click `stop-portable.bat` when done if desired.

## Approaches Considered

### Option 1: Extend the existing `start_all.bat` only

This keeps changes small by pushing all behavior into the existing startup scripts.

Pros:

- least code churn,
- fast to implement.

Cons:

- mixes first-run bootstrap and daily startup into one script,
- hard to recover from partial setup failure,
- not clear enough for non-developer use.

### Option 2: Add a portable script set around the current repo

This introduces dedicated scripts for first-run setup, daily start, stop, health checks, and packaging while reusing current project structure.

Pros:

- clear operator workflow,
- reuses existing startup and repo conventions,
- practical to maintain as the project keeps changing.

Cons:

- still relies on internet for first-time dependency download,
- still depends on local Java/Node/Python/Docker presence.

### Option 3: Build a heavy semi-installer bundle

This would prepackage more dependencies and compiled assets.

Pros:

- smoother first-run experience.

Cons:

- large bundle size,
- high maintenance burden,
- poor fit for an actively changing development repository.

## Decision

Choose Option 2.

This gives the best balance between automation and maintainability. It can genuinely reduce setup work on a new Windows computer without turning the repository into a brittle installer project.

## Target Script Set

The repository should add four Windows entry scripts at the repo root:

- `setup-portable.bat`
- `start-portable.bat`
- `stop-portable.bat`
- `doctor-portable.bat`

Supporting PowerShell should live under `scripts/portable/` so the batch files stay thin and readable.

Recommended script layout:

- `scripts/portable/setup-portable.ps1`
- `scripts/portable/start-portable.ps1`
- `scripts/portable/stop-portable.ps1`
- `scripts/portable/doctor-portable.ps1`
- `scripts/portable/common.ps1`

The batch files should only forward into PowerShell with `-ExecutionPolicy Bypass`.

## Architecture

### Portable workspace boundary

All machine-local operational state should be kept inside the repository or a dedicated subdirectory so the bundle stays self-contained.

Use a repo-local `.portable/` directory for:

- setup markers,
- generated env files for local machine use,
- temporary bootstrap outputs,
- optional cached metadata for setup status.

Continue using repo-local:

- `logs/` for service logs,
- `.pids/` for process ids,
- `data/` for AI-service cache and registry data,
- `mysql-data/` for Docker MySQL volume data if present.

Avoid writing project-specific state into random `C:\Users\...` paths unless a dependency tool does so internally and cannot be redirected.

### Setup phase

`setup-portable.bat` is first-run only and should perform these stages in order:

1. Verify required commands:
   - `docker`
   - `docker compose`
   - `java`
   - `mvn`
   - `node`
   - `npm`
   - `python`
2. Report missing tools clearly and stop before partial setup if a hard requirement is absent.
3. Create local working directories:
   - `.portable/`
   - `logs/`
   - `.pids/`
   - `data/`
4. Prepare AI config:
   - if `deploy/ai-service.env` exists, keep it,
   - otherwise copy from a private portable template or from `deploy/ai-service.env.example`,
   - if a private bundled config is available, prefer it.
5. Start MySQL with Docker Compose.
6. Wait for MySQL readiness.
7. Initialize the `web` database from `back/sql/init_db.sql`.
8. Install frontend dependencies with `npm install`.
9. Install AI-service dependencies with `python -m pip install -r requirements.txt`.
10. Optionally seed AI knowledge-base documents if explicitly enabled or if no seed marker exists yet.
11. Write a success marker such as `.portable/setup-complete.json`.

### Daily start phase

`start-portable.bat` should:

1. confirm setup marker exists,
2. ensure Docker MySQL is up,
3. start backend,
4. start AI service,
5. start frontend,
6. print URLs,
7. store process ids and log file paths.

Where practical, it should reuse the current `start_all.ps1` behavior rather than reimplementing every launch detail.

### Stop phase

`stop-portable.bat` should:

1. read pid files from `.pids/`,
2. stop frontend, backend, and AI-service processes if they are alive,
3. optionally stop Docker MySQL,
4. leave data intact.

This is an operational convenience script, not a destructive cleanup script.

### Doctor phase

`doctor-portable.bat` should print the current environment status:

- required commands present or missing,
- key ports in use,
- whether setup marker exists,
- whether `deploy/ai-service.env` exists,
- whether Docker MySQL is reachable,
- whether frontend/backend/AI URLs respond.

This gives a quick recovery path when migration to a new computer does not work on the first try.

## Configuration Strategy

Because this is a private self-use bundle, the package may carry working AI secrets/configuration.

Rules:

1. Keep `deploy/ai-service.env.example` as the public-safe template.
2. Add a private portable source file that is intentionally ignored by git, for example:
   - `.portable/private/ai-service.env`
3. During setup, copy that file into `deploy/ai-service.env` if the target file is missing.
4. Never overwrite an existing `deploy/ai-service.env` without an explicit force flag.

This preserves convenience for the project owner while avoiding accidental secret churn on repeated runs.

## Database Strategy

For portability, the simplest stable path is to standardize on Docker MySQL rather than depending on a separately installed local MySQL service.

Rules:

1. Docker Compose remains the default DB runtime.
2. `setup-portable` initializes schema/data from `back/sql/init_db.sql`.
3. `start-portable` only starts MySQL if it is not already listening.
4. Data persists in repo-local `mysql-data/`.

This means a zipped, already-initialized workspace can optionally carry MySQL data to another machine, but the canonical first-run path still works from SQL initialization alone.

## AI and LightRAG Strategy

Portable Windows setup should not try to recreate the full remote AI infrastructure locally.

Rules:

1. Preserve the existing remote/private configuration in `deploy/ai-service.env`.
2. Continue using the current configured remote LLM and current configured remote LightRAG or embedding endpoints.
3. Keep local AI-service runtime on the Windows machine.
4. Do not require a local Neo4j or local PostgreSQL for this portable path unless the current runtime truly needs them on startup.

This keeps the portable setup lightweight enough to be practical.

## Error Handling

### Setup

- Missing hard dependencies should stop setup with a direct fix message.
- MySQL readiness timeout should stop setup and point to Docker logs.
- Dependency install failure should stop setup and keep partial progress visible in logs.
- Database import failure should stop setup and avoid writing the success marker.

### Start

- If setup was never completed, start should refuse to continue and tell the user to run `setup-portable.bat`.
- If one service fails, print which one failed and where its log is located.
- Start should not silently ignore occupied ports.

### Stop

- Missing pid files should be treated as "already stopped".
- Dead pids should be cleaned up quietly.

## Testing Strategy

This work should be validated with practical script-level checks instead of only unit tests.

Required verification:

1. Dry-run or doctor output confirms dependency detection works.
2. First-run setup on the current machine succeeds from a clean portable marker state.
3. Daily start brings up:
   - frontend on `127.0.0.1:5173`
   - backend on `127.0.0.1:8080`
   - AI service on `127.0.0.1:9000`
4. Login and basic product browsing still work.
5. AI customer service still answers a known product query using the current private config.
6. Stop script successfully shuts down the launched processes.

## Packaging Output

The repo should support a simple zip distribution workflow:

1. run setup locally if needed,
2. ensure private config file is present,
3. optionally keep or exclude heavy cache directories depending on transfer needs,
4. compress the whole project directory,
5. on the target PC, unzip and run `setup-portable.bat`.

The project should also document which directories are safe to exclude from the zip when the user wants a smaller transfer package.

## Success Criteria

This work is complete when all of the following are true:

1. A new Windows PC can run a dedicated first-time setup script successfully.
2. A dedicated daily start script launches the full local app stack.
3. The setup flow initializes the database automatically.
4. The setup flow can provision the current private AI config automatically.
5. Logs, cache, and runtime state stay within the project workspace as much as practical.
6. The operator has a doctor script and a stop script for recovery and cleanup.
7. The repository documents how to zip and move the project to another Windows machine.
