# ProjectKu LightRAG Runtime Check

This note describes the lightweight local runtime verification script for ProjectKu LightRAG and AI customer service.

## Script

Run the default non-destructive checks:

```powershell
.\scripts\verify-lightrag-runtime.ps1
```

Defaults:

- Backend base URL: `http://127.0.0.1:8080/api`
- AI service base URL: `http://127.0.0.1:9000`
- LightRAG base URL: `http://127.0.0.1:19621`
- LightRAG API key: `$env:LIGHTRAG_API_KEY`
- LightRAG API key header: `$env:LIGHTRAG_API_KEY_HEADER`, or `X-API-Key`

Environment overrides:

```powershell
$env:PROJECTKU_BACKEND_BASE_URL = "http://127.0.0.1:8080/api"
$env:AI_SERVICE_BASE_URL = "http://127.0.0.1:9000"
$env:LIGHTRAG_BASE_URL = "http://127.0.0.1:19621"
$env:LIGHTRAG_API_KEY = "<local-lightrag-api-key>"
.\scripts\verify-lightrag-runtime.ps1
```

## What It Checks

The default run avoids indexing, mutation, and AI chat completion calls.

- Backend TCP port from `-BackendBaseUrl`, normally `8080`
- AI service TCP port from `-AiServiceBaseUrl`, normally `9000`
- Backend root endpoint, normally `GET /api/`
- AI service health endpoint, `GET /health`
- LightRAG documentation endpoint, `GET /docs` with fallback to `GET /redoc`

The script exits with code `0` only when all default checks pass.

## Optional Chat Smoke

The chat smoke sends one customer-service question and can consume model quota. It is disabled by default.

Call ai-service directly:

```powershell
.\scripts\verify-lightrag-runtime.ps1 -RunSmoke -SmokeTarget AiService
```

Call through the Java backend:

```powershell
.\scripts\verify-lightrag-runtime.ps1 -RunSmoke -SmokeTarget Backend
```

Use a custom low-cost question:

```powershell
.\scripts\verify-lightrag-runtime.ps1 -RunSmoke -SmokeMessage "退货运费谁承担？"
```

The script prints only metadata such as status code, answer length, route, source type, fallback reason, and citation count. It does not print the full answer text.

## Common Failures

- `Backend TCP` fails: the Java backend is not listening on the configured host and port.
- `Backend root` fails but TCP passes: the backend may be running with a different context path than `/api`.
- `AI service TCP` fails: the FastAPI service is not listening on the configured host and port.
- `AI service health` fails: the service is reachable but `/health` is unavailable or returning an error.
- `LightRAG docs` fails: LightRAG is not reachable at the configured base URL, the service is still starting, or the API key/header is wrong.

For the older LightRAG-only check, use:

```powershell
.\scripts\check_lightrag.ps1 -BaseUrl http://127.0.0.1:19621
```
