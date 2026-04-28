# LightRAG Runtime Readiness (Phase 4)

Date: 2026-04-27  
Scope: `docker-compose.prod.yml` + `deploy/lightrag.env.template` + `deploy/lightrag.env.example` + `deploy/ai-service.env(.example)`.

## 1) Required env before starting LightRAG

LightRAG runtime must have these groups set:

1. Server/auth:
   - `HOST`, `PORT`
   - `LIGHTRAG_API_KEY` (if auth enabled)
2. LLM binding (OpenAI-compatible):
   - `LLM_BINDING=openai`
   - `LLM_BINDING_HOST`
   - `LLM_BINDING_API_KEY`
   - `LLM_MODEL`
3. Embedding binding (OpenAI-compatible via ai-service gateway):
   - `EMBEDDING_BINDING=openai`
   - `EMBEDDING_BINDING_HOST=http://ai-service:9000/v1`
   - `EMBEDDING_BINDING_API_KEY` (same value as `AI_EMBEDDING_GATEWAY_API_KEY`)
   - `EMBEDDING_MODEL` (current project value: `BAAI/bge-m3`)
   - `EMBEDDING_DIM` (current project value: `1024`)
   - `EMBEDDING_TOKEN_LIMIT`
   - `EMBEDDING_SEND_DIM=false`
   - `EMBEDDING_USE_BASE64=false` (gateway returns float list, not base64 payload)
4. Storage profile:
   - `LIGHTRAG_KV_STORAGE`
   - `LIGHTRAG_VECTOR_STORAGE`
   - `LIGHTRAG_GRAPH_STORAGE`
   - `LIGHTRAG_DOC_STATUS_STORAGE`
5. PostgreSQL:
   - `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DATABASE`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
6. Optional Neo4j:
   - `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`, `NEO4J_DATABASE`

For `ai-service` consumption:
- keep `KNOWLEDGE_RETRIEVER` unchanged unless doing explicit rollout
- `LIGHTRAG_BASE_URL`, `LIGHTRAG_API_KEY`, `LIGHTRAG_TIMEOUT_SECONDS`, `LIGHTRAG_QUERY_MODE`
- optional `LIGHTRAG_API_KEY_HEADER` (defaults to `X-API-Key`)

## 2) Secret and value mapping

Recommended cross-service mapping:
- `LLM_BINDING_HOST` <= `AI_LLM_BASE_URL`
- `LLM_BINDING_API_KEY` <= `AI_LLM_API_KEY`
- `LLM_MODEL` <= `AI_LLM_MODEL`
- `EMBEDDING_MODEL` <= `AI_EMBEDDING_MODEL`
- `EMBEDDING_DIM` <= `AI_EMBEDDING_DIMENSIONS`
- `EMBEDDING_BINDING_API_KEY` == `AI_EMBEDDING_GATEWAY_API_KEY` (shared secret)

Still operator-supplied placeholders:
- `deploy/lightrag.env.template` / `.example`: `LIGHTRAG_API_KEY`, `LLM_BINDING_API_KEY`, `EMBEDDING_BINDING_API_KEY`, `POSTGRES_PASSWORD` (and `NEO4J_PASSWORD` if enabled)
- compose defaults: `LIGHTRAG_POSTGRES_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `NEO4J_PASSWORD` must be overridden for production

## 3) Compose/runtime wiring

- `docker-compose.prod.yml` reads `./deploy/lightrag.env` for `lightrag`.
- `deploy/lightrag.env` is intentionally gitignored; generate it from `deploy/lightrag.env.template` or `.example`.
- `lightrag` depends on:
  - `lightrag-postgres` healthy
  - `ai-service` started

## 4) Safe startup order

1. Create `deploy/lightrag.env` from template/example (never commit real keys).
2. Fill LightRAG/DB keys and gateway API key placeholders.
3. Start and verify `lightrag-postgres` health.
4. Start `ai-service` and verify OpenAI-compatible embeddings endpoint `/v1/embeddings`.
5. If `AI_EMBEDDING_REMOTE_URL` points to `127.0.0.1:9001/embed`, start the local embedding backend with `python scripts/local_embedding_server.py`.
6. Run embedding gateway smoke check:
   - `scripts/check_embedding_gateway.ps1 -BaseUrl http://127.0.0.1:9000/v1`
7. Start `lightrag`.
8. Run smoke check: `scripts/check_lightrag.ps1 -BaseUrl http://127.0.0.1:19621`.
9. Keep `KNOWLEDGE_RETRIEVER=chroma` as default; trial with `lightrag_with_chroma_fallback` first when needed.

## 5) Rollback notes

1. Set `KNOWLEDGE_RETRIEVER=chroma` in `deploy/ai-service.env`.
2. Restart only `ai-service`.
3. Keep LightRAG/Postgres volumes for later retry.
