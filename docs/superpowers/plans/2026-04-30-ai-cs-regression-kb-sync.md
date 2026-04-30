# AI Customer Service Regression And KB Sync Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a durable AI customer-service regression suite and make KB-to-LightRAG sync health visible and easy to operate from the admin UI.

**Architecture:** Keep runtime retrieval LightRAG-only. Add deterministic tests around routing, query expansion, product ambiguity, and policy answers; add a backend KB sync health endpoint computed from existing documents/chunks/index records; show that health in the Vue admin panel next to the existing batch sync button.

**Tech Stack:** FastAPI/Python pytest, Spring Boot/MyBatis/JUnit, Vue 3/TypeScript, existing LightRAG and KB admin APIs.

---

### Task 1: AI Customer-Service Regression Coverage

**Files:**
- Modify: `ai-service/app/retrieval/lightrag_retriever.py`
- Modify: `ai-service/tests/test_lightrag_retriever.py`
- Modify: `ai-service/tests/test_chat_api.py`
- Optional create: `ai-service/tests/test_customer_service_regression_cases.py`

- [ ] Add deterministic tests for common questions:
  - `苹果多少钱？` stays a broad fruit/product clarification, not Apple brand.
  - `苹果15多少钱？` routes to realtime product lookup.
  - `售后质量问题退回运费谁承担？` retrieves after-sales shipping chunks.
  - `优惠券没到门槛为什么不能用？` routes to coupon policy.
  - `物流一直不动怎么办？` routes to logistics policy.
- [ ] Add or refine query expansion only where a real regression is proven.
- [ ] Run:
  - `cd ai-service; python -m pytest tests/test_chat_api.py tests/test_lightrag_retriever.py -q`

### Task 2: Backend KB Sync Health API

**Files:**
- Modify: `back/src/main/java/com/web/controller/KnowledgeBaseController.java`
- Modify: `back/src/main/java/com/web/service/KnowledgeBaseService.java`
- Modify: `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java`
- Modify: `back/src/main/java/com/web/dto/KnowledgeBaseResponses.java`
- Modify: `back/src/test/java/com/web/service/impl/KnowledgeBaseServiceImplTest.java`

- [ ] Add `GET /api/v1/kb/documents/sync-health`.
- [ ] Response includes totals: documents, parsed, chunked, indexed, failed, stale, missingChunks, latestFailedIndex.
- [ ] For each document include id, title, category, status, version, chunkCount, latestIndexStatus, latestIndexedChunkCount, latestIndexError, needsSync.
- [ ] Compute `needsSync=true` when document is parsed/chunked/failed, has no chunks, has no successful latest index, or latest indexed chunk count differs from current chunk count.
- [ ] Run:
  - `cd back; mvn "-Dtest=KnowledgeBaseServiceImplTest" test`

### Task 3: KB Admin Sync Health UI

**Files:**
- Modify: `frontend/src/lib/knowledgeBase.ts`
- Modify: `frontend/src/views/KnowledgeBaseAdminView.vue`

- [ ] Add TypeScript types and `fetchKbSyncHealth()`.
- [ ] Show a compact health bar above document list: total, indexed, needs sync, failed.
- [ ] Add a refresh button.
- [ ] After batch sync, single index, delete, update, or chunk operations, refresh sync health.
- [ ] Highlight documents that need sync in the list.
- [ ] Run:
  - `cd frontend; npm run build`

### Task 4: Operator Script And Documentation

**Files:**
- Create: `scripts/run-ai-cs-regression.ps1`
- Modify: `docs/ai-service-runbook.md`
- Optional create: `docs/knowledge-base/ai-cs-regression-cases.md`

- [ ] Add a PowerShell script that sends a small set of live chat questions to backend `/api/v1/customer-service/chat`.
- [ ] Script should print PASS/FAIL summaries based on required answer substrings/source type/fallback reason.
- [ ] Document when to run it: after LightRAG rebuild, KB reindex, prompt/router changes, or before GitHub release.
- [ ] Run:
  - `.\scripts\run-ai-cs-regression.ps1`

### Final Verification

- [ ] `.\scripts\verify-lightrag-runtime.ps1 -CheckEmbeddingGateway`
- [ ] `cd ai-service; python -m pytest tests/test_chat_api.py tests/test_lightrag_retriever.py tests/test_indexing_api.py tests/test_knowledge_retriever.py -q`
- [ ] `cd back; mvn "-Dtest=KnowledgeBaseServiceImplTest,AiServiceClientEncodingTest" test`
- [ ] `cd frontend; npm run build`
- [ ] `.\scripts\run-ai-cs-regression.ps1`
