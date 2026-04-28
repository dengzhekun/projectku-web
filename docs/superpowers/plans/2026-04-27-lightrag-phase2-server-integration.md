# LightRAG Phase 2 Server Integration (2026-04-27)

## Scope in this slice

This Phase 2 slice focuses on server integration tests and operational documentation for retriever-mode compatibility.

Implemented in this worker:

1. Chat integration test coverage for knowledge retrieval path via `get_knowledge_retriever` using a LightRAG-like dummy retriever.
2. Indexing API integration test coverage proving `/internal/index` writes through `get_knowledge_retriever().upsert(...)` independent of backend mode (`chroma` or `lightrag`).
3. No production retrieval logic changes in this slice.

## Expected LightRAG client/retriever files

Phase 2 expects (from retrieval implementation workers) server/runtime files in `ai-service/app` similar to:

- `clients/lightrag_client.py` (or equivalent HTTP/local runtime client)
- `retrieval/lightrag_retriever.py` implementing the `KnowledgeRetriever` protocol (`query`, `upsert`)
- updates in `retrieval/knowledge_retriever.py` to instantiate LightRAG backend when configured

Test coverage added here is backend-agnostic and validates behavior through the retriever abstraction entrypoints.

## Expected environment variables

Expected baseline retriever selector:

- `KNOWLEDGE_RETRIEVER=chroma|lightrag`

Likely LightRAG runtime vars (final naming depends on implementation worker decisions):

- `LIGHTRAG_BASE_URL`
- `LIGHTRAG_API_KEY` (if auth required)
- `LIGHTRAG_TIMEOUT_SECONDS`
- `LIGHTRAG_QUERY_MODE`

Existing embedding/vector env vars may still be required for `chroma` mode and for fallback compatibility.

## Deployment/startup status

Docker image changes, LightRAG service startup wiring, and compose/server orchestration are **future work** unless implemented by another worker in parallel.

This document does not claim LightRAG runtime deployment is complete.

## Verification commands

Primary test command for this slice:

```powershell
$env:PYTHONPATH='ai-service'; python -m pytest ai-service/tests/test_chat_api.py ai-service/tests/test_indexing_api.py -q
```

Optional follow-up command after retrieval implementation lands:

```powershell
$env:PYTHONPATH='ai-service'; python -m pytest ai-service/tests -q
```

## Rollback mode

If LightRAG runtime/deployment is unstable, force rollback to current backend:

```powershell
$env:KNOWLEDGE_RETRIEVER='chroma'
```

This keeps chat/indexing on the known Chroma path while preserving retriever-abstraction tests.
