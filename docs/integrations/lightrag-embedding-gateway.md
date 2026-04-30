# LightRAG Embedding Gateway Wiring

Date: 2026-04-27

## Goal

Use `ai-service` as an OpenAI-compatible embeddings gateway for LightRAG on compose network.

## Runtime Target

- LightRAG base: `http://ai-service:9000/v1`
- Endpoint used by LightRAG: `POST /embeddings`
- Full gateway path: `http://ai-service:9000/v1/embeddings`

## LightRAG env settings

Set in `deploy/lightrag.env` (from `.template` or `.example`):

```env
EMBEDDING_BINDING=openai
EMBEDDING_BINDING_HOST=http://ai-service:9000/v1
EMBEDDING_BINDING_API_KEY=<same value as AI_EMBEDDING_GATEWAY_API_KEY>
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIM=1024
EMBEDDING_TOKEN_LIMIT=8192
EMBEDDING_SEND_DIM=false
EMBEDDING_USE_BASE64=false
```

## Key alignment

- `EMBEDDING_BINDING_API_KEY` (LightRAG) must equal `AI_EMBEDDING_GATEWAY_API_KEY` (`ai-service`).
- Do not commit real values to git.

## Notes

- `EMBEDDING_USE_BASE64=false` is required because the gateway returns float-vector lists.
- Production retriever mode is `KNOWLEDGE_RETRIEVER=lightrag`.
- If embedding backend is unavailable, LightRAG docs/status can still appear healthy while real query/index requests fail.

## Smoke check script

Use local PowerShell script to verify embedding gateway quickly (no Docker dependency):

```powershell
scripts/check_embedding_gateway.ps1
```

Optional parameters:

```powershell
scripts/check_embedding_gateway.ps1 `
  -BaseUrl http://127.0.0.1:9000/v1 `
  -ApiKey $env:AI_EMBEDDING_GATEWAY_API_KEY `
  -Model BAAI/bge-m3 `
  -TimeoutSeconds 30
```

Expected pass output includes embedding dimension only (not vector content, not key value).

## Local embedding backend for development

If `AI_EMBEDDING_REMOTE_URL=http://127.0.0.1:9001/embed`, start the local BGE backend first:

```powershell
python scripts/local_embedding_server.py
```

It reads `deploy/ai-service.env`, serves `GET /health` and `POST /embed`, and uses the same `AI_EMBEDDING_REMOTE_API_KEY` expected by `ai-service`.

Before production query tests, run:

```powershell
scripts/verify-lightrag-runtime.ps1
```

and confirm `http://127.0.0.1:9001/health` is reachable when local embedding backend is configured.
