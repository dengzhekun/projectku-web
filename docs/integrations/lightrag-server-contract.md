# LightRAG Server REST Integration Contract (Phase 2)

Date: 2026-04-27
Scope: Interface-level contract for integrating `ai-service` with LightRAG Server through REST only.

## 1. Integration Mode Decision

We standardize on **LightRAG Server REST API** for Phase 2.

Reasons:
- Keeps `ai-service` and LightRAG runtime decoupled (network boundary, independent deploy/rollback).
- Matches production-friendly operations (containerized server, health/docs endpoints, async track status).
- Avoids coupling to embedded Core APIs and local runtime details.

Out of scope for this contract:
- Embedded LightRAG Core integration inside `ai-service`.
- Storage migration scripts and production data backfill.

## 2. Server Endpoints (Authoritative Surface)

Primary endpoints to integrate:
- `POST /documents/text`
- `POST /documents/texts`
- `POST /documents/upload`
- `GET /documents/track_status/{track_id}`
- `POST /query`
- `POST /query/stream`
- `GET /docs`
- `GET /redoc`

Local smoke check:

```powershell
.\scripts\check_lightrag.ps1 -BaseUrl http://127.0.0.1:19621
```

## 3. Project-Side Operations

`ai-service` should expose the following operation mapping:

1. Ingest single text
   - Use `POST /documents/text`
   - Input: one text payload plus optional document metadata.
   - Output: accepted status and/or `track_id` for async indexing.

2. Ingest batch texts
   - Use `POST /documents/texts`
   - Input: list of text items plus optional metadata per item.
   - Output: accepted status and/or `track_id`.

3. Upload file
   - Use `POST /documents/upload` (`multipart/form-data`)
   - Input: file binary plus optional metadata.
   - Output: accepted status and/or `track_id`.

4. Track indexing status
   - Use `GET /documents/track_status/{track_id}`
   - Input: `track_id`.
   - Output: current state (`pending/running/success/failed` style states per server implementation).

5. Query
   - Use `POST /query`
   - Input: query text and retrieval/generation options required by server.
   - Output: non-stream response for retrieval + answer data.

6. Stream query
   - Use `POST /query/stream`
   - Input: same logical request as `/query`.
   - Output: streamed tokens/chunks/events.

## 4. Auth Contract Assumption

- API key auth header must be **configurable** in `ai-service`.
- Default assumption:
  - `ai-service` sends `${LIGHTRAG_API_KEY_HEADER}: ${LIGHTRAG_API_KEY}` when `LIGHTRAG_API_KEY` is set.
  - Current LightRAG docs describe `X-API-Key` as the API key header, so `LIGHTRAG_API_KEY_HEADER` defaults to `X-API-Key`.
- Exact auth header name/value format must be finalized after LightRAG Server startup by checking `/docs` (OpenAPI) or `/redoc`.

## 5. Response Normalization for `ai-service`

`ai-service` should normalize retrieval outputs to:

```json
{
  "chunks": [
    {
      "text": "document chunk text",
      "metadata": {
        "source": "optional",
        "doc_id": "optional",
        "score": "optional"
      },
      "citations": [
        {
          "title": "optional",
          "url": "optional",
          "locator": "optional"
        }
      ]
    }
  ]
}
```

Normalization rules:
- `text` is required for each chunk.
- `metadata` is always present as object (empty object `{}` when unavailable).
- `citations` is always present as array (empty array `[]` when unavailable).
- If server returns answer-only content without chunk list, fallback to one synthetic chunk with answer text and empty metadata/citations.

## 6. Error and Fallback Contract

When `KNOWLEDGE_RETRIEVER=lightrag_with_chroma_fallback`:
- On LightRAG timeout, connection failure, 5xx, or invalid response shape, `ai-service` must fallback to Chroma retrieval path.
- Fallback should be logged with request id / reason.
- If both LightRAG and fallback fail, return the existing retriever error envelope.

When `KNOWLEDGE_RETRIEVER=lightrag` (no fallback):
- Surface LightRAG errors directly through existing API error handling.

## 7. Storage Recommendation (Phase 2)

Recommended primary direction:
- Start with a **documented unified backend** profile (for example PostgreSQL-based unified storage, or OpenSearch-based profile if infra prefers search-native stack).

Rules:
- Do **not** switch LightRAG storage implementation after documents are indexed, unless you perform a full re-index migration.
- Treat Chroma as **non-primary** for LightRAG Server Phase 2 (docs are inconsistent for server-primary usage).
- Neo4j remains an optional graph backend path, controlled by explicit graph storage selection.

## 8. Required Config Groups Checklist

LightRAG Server runtime should provide/configure at least:
- `LLM_BINDING`, `LLM_BINDING_HOST`, `LLM_BINDING_API_KEY`, `LLM_MODEL`
- `EMBEDDING_BINDING=openai`
- `EMBEDDING_BINDING_HOST=http://ai-service:9000/v1` (compose internal network)
- `EMBEDDING_BINDING_API_KEY` (same value as `AI_EMBEDDING_GATEWAY_API_KEY` in `deploy/ai-service.env`)
- `EMBEDDING_MODEL=BAAI/bge-m3`, `EMBEDDING_DIM=1024`
- `EMBEDDING_TOKEN_LIMIT`, `EMBEDDING_SEND_DIM=false`, `EMBEDDING_USE_BASE64=false`
- `LIGHTRAG_KV_STORAGE`, `LIGHTRAG_VECTOR_STORAGE`, `LIGHTRAG_GRAPH_STORAGE`, `LIGHTRAG_DOC_STATUS_STORAGE`
- Neo4j options when used: `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`, `NEO4J_DATABASE`

Gateway contract note:
- Worker B provides `POST /v1/embeddings` in `ai-service`.
- LightRAG calls `EMBEDDING_BINDING_HOST + /embeddings` using OpenAI-compatible request/response shape.

## 9. Local Smoke Check Script

For a non-destructive connectivity/env readiness check, run:

```powershell
.\scripts\check_lightrag.ps1
```

Optional parameters:
- `-BaseUrl` (default `http://127.0.0.1:19621`)
- `-ApiKey` (default from env `LIGHTRAG_API_KEY`)
- `-TimeoutSeconds` (default `10`)
