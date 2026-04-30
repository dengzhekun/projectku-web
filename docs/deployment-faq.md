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

## Which retriever mode should production use?

Use the current production default:

```env
KNOWLEDGE_RETRIEVER=lightrag
```

AI customer-service KB retrieval, reindex, and delete now run through LightRAG. Chroma is no longer part of the current production retrieval chain.

Older documents or private env files may mention `chroma` or `lightrag_with_chroma_fallback`. Those were cutover modes used during the Chroma-to-LightRAG migration and should not be selected for current deployments.

If an older environment still has a historical value, change it to `KNOWLEDGE_RETRIEVER=lightrag`, confirm LightRAG/PostgreSQL/Neo4j are healthy, and reindex the KB if needed.

## Why does LightRAG need its own env file?

Because it needs its own API auth, PostgreSQL credentials, graph settings, and embedding binding configuration.

`prepare_lightrag_env.sh` copies the shared values that should match `ai-service.env`, but it does not guess your server passwords.

## What should I check if AI retrieval becomes unstable?

Do not roll back to Chroma for current production deployments. Chroma is not in the running retrieval path.

Check these first:

1. LightRAG health and logs
2. PostgreSQL and Neo4j connectivity
3. `LIGHTRAG_API_KEY`, `LIGHTRAG_BASE_URL`, and `LIGHTRAG_DOC_REGISTRY_PATH`
4. Whether the KB needs reindexing after a data or registry change

After fixing the dependency or configuration issue, restart `ai-service` and LightRAG as needed.

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
