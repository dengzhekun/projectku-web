# Deployment FAQ

## Which deployment entry should I use?

Use the files under `deploy/` first:

- `deploy/prod.env.example`
- `deploy/ai-service.env.example`
- `deploy/prepare_lightrag_env.sh`
- `deploy/bootstrap-prod.sh`

## Which env files are private?

These should stay local to your machine or server:

- `deploy/prod.env`
- `deploy/ai-service.env`
- `deploy/lightrag.env`

They are intentionally ignored by git.

## Which retriever mode should I use first?

Start with:

```env
KNOWLEDGE_RETRIEVER=chroma
```

Then move to:

```env
KNOWLEDGE_RETRIEVER=lightrag_with_chroma_fallback
```

Only use pure `lightrag` after the full chain is stable.

## Why does LightRAG need its own env file?

Because it needs its own API auth, PostgreSQL credentials, graph settings, and embedding binding configuration.

`prepare_lightrag_env.sh` copies the shared values that should match `ai-service.env`, but it does not guess your server passwords.

## What is the fastest rollback if AI retrieval becomes unstable?

1. Set `KNOWLEDGE_RETRIEVER=chroma`
2. Restart only `ai-service`

That keeps the rest of the deployment unchanged.

## Can embeddings run on another server?

Yes. Set:

- `AI_EMBEDDING_PROVIDER=remote_http`
- `AI_EMBEDDING_REMOTE_URL=...`
- `AI_EMBEDDING_REMOTE_API_KEY=...`

That is the preferred option for low-memory application servers.

## Why does local git show ahead/behind after GitHub changes?

If the machine cannot use normal `git push` or `git fetch` against `github.com`, but repo updates were applied through the GitHub API, your local tracking refs can temporarily drift.

Once network access is normal again, run:

```bash
git fetch origin
```

Then reconcile local branches as needed.

## What if Docker Desktop is unavailable on Windows?

For local development, you can still run:

- MySQL separately
- backend with `mvn spring-boot:run`
- AI service with `uvicorn`
- frontend with Vite

Docker is mainly required for the packaged production stack.

## How do I move this project to another Windows PC for private self-use?

Use the portable workflow from the repo root after you unzip the project on the target machine.

Prerequisites:

- Docker Desktop
- Java 17 + Maven
- Node.js 20 + npm
- Python 3.11+

Run:

```powershell
.\run-portable.bat
.\run-portable.bat doctor
.\run-portable.bat stop
```

`run-portable.bat` is the main entrypoint. On a new machine it performs first-time setup automatically, then starts the local stack. After that, use `run-portable.bat` for daily startup, `run-portable.bat doctor` for checks, and `run-portable.bat stop` to shut the stack down. Portable runtime files stay in repo-local directories (such as `.portable`, `.pids`, and `.runtime-logs`).

## How do I generate a zip to send to another Windows machine?

Run this from the repo root on the source machine:

```powershell
.\package-portable.bat
```

The zip is created under `.portable\dist\`. It excludes heavy local build/runtime folders by default and stages your current private AI config into `.portable\private\ai-service.env` inside the package so `run-portable.bat` on the target machine can restore it during first-time setup.
