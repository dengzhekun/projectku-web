# LightRAG Admin Indexing Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve KB admin chunk metadata end-to-end when `/v1/kb/documents/{id}/index` bridges to ai-service `/internal/index` and `KnowledgeRetriever` is LightRAG-backed.

**Architecture:** Keep the existing Java -> ai-service `/internal/index` call path unchanged. Implement metadata preservation inside ai-service retriever/client layer by encoding metadata into LightRAG-ingested text payloads (or compatible insert endpoint usage), then decoding metadata from LightRAG query `sources` so chat citations/hitLogs remain populated. Keep retriever backend switch-driven behavior and preserve Chroma fallback.

**Tech Stack:** Spring Boot (existing bridge caller), FastAPI, Pydantic, LightRAG HTTP client, pytest.

---

## Current Flow Audit (What Already Works)

1. Java admin indexing already bridges correctly:
   - `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java`
   - `back/src/main/java/com/web/service/impl/AiKnowledgeBaseClientImpl.java`
   - Calls ai-service `POST /internal/index` with `documentId/version/title/category/chunks`.
2. ai-service endpoint already calls retriever abstraction:
   - `ai-service/app/api/indexing.py` calls `get_knowledge_retriever().upsert(records)`.
3. Chroma path already preserves metadata (`metadatas=...` in upsert).
4. LightRAG path currently drops metadata:
   - `ai-service/app/retrieval/lightrag_retriever.py` upsert only sends `record["document"]` to `insert_texts`.
   - Returned query metadata currently only includes `source_type/source_id/title`; no `document_id/chunk_id/chunk_index/version/category`.
   - Result: citations/hit logs for admin KB chunks are incomplete/unreliable with `KNOWLEDGE_RETRIEVER=lightrag`.

## Minimal Design Decision

Use **metadata envelope in text payload** for `insert_texts` compatibility:

1. On LightRAG upsert, transform each record document into a deterministic text payload:
   - Prefix/suffix with a compact machine-parseable metadata block (JSON line/tag format).
   - Include: `source_type`, `source_id`, `document_id`, `chunk_id`, `chunk_index`, `title`, `category`, `version`.
2. On LightRAG query, parse metadata block from each returned source content:
   - Recover original clean content for prompt context.
   - Rebuild `metadata` dict expected by chat path (`document_id/chunk_id` required for hit logs).
3. If parse fails, gracefully fall back to current behavior (no regression).

Why this minimal approach:
- No Java contract change.
- No new LightRAG server dependency/feature assumptions.
- Works with current `insert_texts(texts: list[str])` client API.

---

### Task 1: Add LightRAG metadata codec utility

**Files:**
- Create: `ai-service/app/retrieval/lightrag_metadata_codec.py`
- Test: `ai-service/tests/test_lightrag_metadata_codec.py`

- [ ] **Step 1: Write failing tests for encode/decode roundtrip**
  - Validate metadata + content roundtrip.
  - Validate parse fallback on plain text/non-envelope content.
  - Validate numeric fields decode as ints where possible.

- [ ] **Step 2: Implement codec**
  - Add `encode_document_with_metadata(document: str, metadata: dict[str, Any]) -> str`.
  - Add `decode_document_with_metadata(text: str) -> tuple[str, dict[str, Any] | None]`.
  - Keep format compact and deterministic.

- [ ] **Step 3: Run targeted tests**
  - Run: `cd ai-service; pytest tests/test_lightrag_metadata_codec.py -q`
  - Expected: PASS.

### Task 2: Wire codec into LightRagRetriever upsert/query

**Files:**
- Modify: `ai-service/app/retrieval/lightrag_retriever.py`
- Test: `ai-service/tests/test_lightrag_retriever.py`

- [ ] **Step 1: Add failing retriever tests**
  - Upsert should send encoded texts containing metadata envelope.
  - Query should decode envelope and expose `metadata.document_id/chunk_id/chunk_index/version/category`.
  - Query on non-envelope content should keep current fallback behavior.

- [ ] **Step 2: Implement retriever changes**
  - Upsert: encode each record's document + metadata before `insert_texts`.
  - Query: decode source content; merge decoded metadata with LightRAG source fields.
  - Ensure returned `document` is clean text (envelope stripped).

- [ ] **Step 3: Run targeted tests**
  - Run: `cd ai-service; pytest tests/test_lightrag_retriever.py -q`
  - Expected: PASS.

### Task 3: Lock `/internal/index` + chat behavior with LightRAG metadata

**Files:**
- Modify: `ai-service/tests/test_indexing_api.py`
- Modify: `ai-service/tests/test_chat_api.py`

- [ ] **Step 1: Extend indexing API test assertions**
  - Keep assertion that `/internal/index` uses `get_knowledge_retriever().upsert(records)`.
  - Add assertions that records include required metadata keys for codec (`document_id`, `chunk_id`, `chunk_index`, `version`, `title`, `category`).

- [ ] **Step 2: Extend chat metadata safety tests**
  - Assert hit log creation when decoded LightRAG metadata includes numeric strings.
  - Assert citations/hit logs degrade safely when metadata cannot be decoded.

- [ ] **Step 3: Run targeted tests**
  - Run: `cd ai-service; pytest tests/test_indexing_api.py tests/test_chat_api.py -q`
  - Expected: PASS.

### Task 4: End-to-end bridge verification + rollback instructions

**Files:**
- Modify: `deploy/ai-service.env.example`
- Optional Modify (if present in repo docs): `docs/superpowers/specs/2026-04-27-lightrag-*.md` or relevant runbook

- [ ] **Step 1: Verify env switch guidance is explicit**
  - Keep/confirm retriever modes:
    - `KNOWLEDGE_RETRIEVER=chroma`
    - `KNOWLEDGE_RETRIEVER=lightrag`
    - `KNOWLEDGE_RETRIEVER=lightrag_with_chroma_fallback`

- [ ] **Step 2: Add rollback section**
  - Document immediate rollback command/value:
    - `KNOWLEDGE_RETRIEVER=chroma`
  - Note that this bypasses LightRAG metadata codec path and restores stable Chroma metadata behavior.

- [ ] **Step 3: Run verification commands**
  - `cd ai-service; pytest tests/test_lightrag_metadata_codec.py tests/test_lightrag_retriever.py tests/test_indexing_api.py tests/test_chat_api.py -q`
  - `cd ai-service; pytest tests/test_knowledge_retriever.py tests/test_lightrag_client.py -q`
  - Optional bridge smoke (services running):
    1. POST backend `/api/v1/kb/documents/{id}/index`
    2. POST ai-service `/chat`
    3. Confirm `hitLogs` includes expected `documentId/chunkId`.

---

## Non-Goals (Keep Scope Minimal)

- No changes to Java production request/response contracts unless metadata fields are proven missing.
- No LightRAG server schema/storage redesign.
- No broad chat pipeline refactor outside retriever metadata handling.

## Rollback Mode

If production/staging LightRAG metadata parsing causes regressions:
1. Set `KNOWLEDGE_RETRIEVER=chroma`.
2. Restart ai-service.
3. Re-run a KB indexing request and chat smoke test.
4. Keep LightRAG code path disabled until fix is validated.
