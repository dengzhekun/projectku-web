# LightRAG Pure Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Chroma from the active AI customer-service runtime and make LightRAG the single authoritative backend for KB retrieval, reindex, and deletion.

**Architecture:** Keep the current backend -> ai-service indexing bridge, but replace mixed retriever behavior with a pure LightRAG retriever mode. Add explicit LightRAG delete wiring, make attribution derive from LightRAG-returned metadata only, and fail loudly on indexing/deletion inconsistencies instead of silently falling back to Chroma.

**Tech Stack:** FastAPI, Spring Boot, LightRAG HTTP server, pytest, JUnit, Docker Compose.

---

## File Map

- Modify: `ai-service/app/retrieval/knowledge_retriever.py`
- Modify: `ai-service/app/retrieval/lightrag_retriever.py`
- Modify: `ai-service/app/clients/lightrag_client.py`
- Modify: `ai-service/app/api/chat.py`
- Modify: `ai-service/app/api/indexing.py`
- Modify: `ai-service/app/ingest/sync_job.py`
- Modify: `ai-service/tests/test_knowledge_retriever.py`
- Modify: `ai-service/tests/test_lightrag_client.py`
- Modify: `ai-service/tests/test_lightrag_retriever.py`
- Modify: `ai-service/tests/test_chat_api.py`
- Modify: `ai-service/tests/test_indexing_api.py`
- Modify: `back/src/main/java/com/web/service/AiKnowledgeBaseClient.java`
- Modify: `back/src/main/java/com/web/service/impl/AiKnowledgeBaseClientImpl.java`
- Modify: `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java`
- Modify: `back/src/test/java/com/web/service/impl/KnowledgeBaseServiceImplTest.java`
- Modify: `deploy/ai-service.env.example`
- Modify: `deploy/README.md`

### Task 1: Remove mixed retriever runtime and lock pure LightRAG mode

**Files:**
- Modify: `ai-service/app/retrieval/knowledge_retriever.py`
- Test: `ai-service/tests/test_knowledge_retriever.py`

- [ ] **Step 1: Write failing tests for supported runtime mode**
  - Add/adjust tests so supported runtime mode is `lightrag` only.
  - Reject `chroma` and `lightrag_with_chroma_fallback`.

- [ ] **Step 2: Run targeted tests to verify failure**

Run:

```powershell
cd ai-service
python -m pytest tests/test_knowledge_retriever.py -q
```

Expected: FAIL on old mixed-mode assumptions.

- [ ] **Step 3: Implement pure LightRAG retriever factory**
  - Remove fallback retriever usage from runtime selection.
  - Keep code changes scoped to retriever construction first.

- [ ] **Step 4: Re-run targeted tests**

Run:

```powershell
cd ai-service
python -m pytest tests/test_knowledge_retriever.py -q
```

Expected: PASS.

### Task 2: Add LightRAG delete contract and verify delete-before-upsert

**Files:**
- Modify: `ai-service/app/clients/lightrag_client.py`
- Modify: `ai-service/app/retrieval/lightrag_retriever.py`
- Modify: `ai-service/app/api/indexing.py`
- Test: `ai-service/tests/test_lightrag_client.py`
- Test: `ai-service/tests/test_lightrag_retriever.py`
- Test: `ai-service/tests/test_indexing_api.py`

- [ ] **Step 1: Write failing tests for delete support**
  - LightRAG client should expose document delete.
  - `LightRagRetriever.delete_document(document_id)` should call client delete.
  - indexing should still do delete then upsert in order.

- [ ] **Step 2: Run targeted tests to verify failure**

Run:

```powershell
cd ai-service
python -m pytest tests/test_lightrag_client.py tests/test_lightrag_retriever.py tests/test_indexing_api.py -q
```

Expected: FAIL because delete contract is missing/no-op.

- [ ] **Step 3: Implement delete client + retriever behavior**
  - Add LightRAG delete HTTP method using the server’s document deletion capability.
  - Replace the current no-op `delete_document`.
  - Preserve delete-before-upsert order in indexing path.

- [ ] **Step 4: Re-run targeted tests**

Run:

```powershell
cd ai-service
python -m pytest tests/test_lightrag_client.py tests/test_lightrag_retriever.py tests/test_indexing_api.py -q
```

Expected: PASS.

### Task 3: Remove Chroma-dependent attribution and harden pure-LightRAG citations

**Files:**
- Modify: `ai-service/app/api/chat.py`
- Test: `ai-service/tests/test_chat_api.py`

- [ ] **Step 1: Write failing tests for attribution without Chroma**
  - LightRAG valid chunk metadata should produce hit logs directly.
  - LightRAG answer-level-only results should degrade safely without Chroma source tracing.
  - Citation selection should skip invalid early chunks and still return valid citations.

- [ ] **Step 2: Run targeted tests to verify failure**

Run:

```powershell
cd ai-service
python -m pytest tests/test_chat_api.py -q
```

Expected: FAIL on old Chroma-dependent assumptions.

- [ ] **Step 3: Implement attribution cutover**
  - Remove Chroma tracing fallback from chat.
  - Make `build_citations` filter validity before truncating to top 3.
  - Keep malformed metadata non-fatal.

- [ ] **Step 4: Re-run targeted tests**

Run:

```powershell
cd ai-service
python -m pytest tests/test_chat_api.py -q
```

Expected: PASS.

### Task 4: Wire backend KB deletion to ai-service and fail on orphan risk

**Files:**
- Modify: `back/src/main/java/com/web/service/AiKnowledgeBaseClient.java`
- Modify: `back/src/main/java/com/web/service/impl/AiKnowledgeBaseClientImpl.java`
- Modify: `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java`
- Test: `back/src/test/java/com/web/service/impl/KnowledgeBaseServiceImplTest.java`

- [ ] **Step 1: Write failing backend tests**
  - Deleting a KB document should call ai-service delete before local DB/file cleanup.
  - If ai-service delete fails, backend delete should fail.

- [ ] **Step 2: Run targeted tests to verify failure**

Run:

```powershell
cd back
mvn -Dtest=KnowledgeBaseServiceImplTest test
```

Expected: FAIL because delete bridge does not exist yet.

- [ ] **Step 3: Implement backend delete bridge**
  - Extend `AiKnowledgeBaseClient` with delete method.
  - Implement HTTP call in `AiKnowledgeBaseClientImpl`.
  - Call it from `KnowledgeBaseServiceImpl.deleteDocument()` before DB/file deletion.

- [ ] **Step 4: Re-run targeted tests**

Run:

```powershell
cd back
mvn -Dtest=KnowledgeBaseServiceImplTest test
```

Expected: PASS.

### Task 5: Make LightRAG rebuild paths idempotent enough for production

**Files:**
- Modify: `ai-service/app/ingest/sync_job.py`
- Modify: `ai-service/app/retrieval/lightrag_retriever.py`
- Test: `ai-service/tests/test_lightrag_retriever.py`

- [ ] **Step 1: Write failing test for repeated logical document sync**
  - Repeated sync of the same logical records must not rely on append-only semantics.

- [ ] **Step 2: Run targeted tests to verify failure**

Run:

```powershell
cd ai-service
python -m pytest tests/test_lightrag_retriever.py -q
```

Expected: FAIL on duplicate-prone behavior assumptions.

- [ ] **Step 3: Implement idempotent rebuild behavior**
  - Clear document scope before reinsert where logical identity is known.
  - Keep seed/rebuild path aligned with pure-LightRAG lifecycle.

- [ ] **Step 4: Re-run targeted tests**

Run:

```powershell
cd ai-service
python -m pytest tests/test_lightrag_retriever.py -q
```

Expected: PASS.

### Task 6: Cut deployment defaults to LightRAG and remove mixed-mode docs

**Files:**
- Modify: `deploy/ai-service.env.example`
- Modify: `deploy/README.md`

- [ ] **Step 1: Update env default**
  - Set `KNOWLEDGE_RETRIEVER=lightrag`.
  - Remove mixed-mode recommendation text.

- [ ] **Step 2: Update deployment docs**
  - Document LightRAG as the default production retriever.
  - Remove Chroma fallback wording from operator docs.

- [ ] **Step 3: Verify docs diff**

Run:

```powershell
git diff -- deploy/ai-service.env.example deploy/README.md
```

Expected: only LightRAG-first operator guidance remains.

### Task 7: Run focused regression suite for the cutover

**Files:**
- No production file changes

- [ ] **Step 1: Run ai-service regression**

Run:

```powershell
cd ai-service
python -m pytest tests/test_knowledge_retriever.py tests/test_lightrag_client.py tests/test_lightrag_retriever.py tests/test_indexing_api.py tests/test_chat_api.py -q
```

Expected: PASS.

- [ ] **Step 2: Run backend regression**

Run:

```powershell
cd back
mvn -Dtest=KnowledgeBaseServiceImplTest test
```

Expected: PASS.

- [ ] **Step 3: Sanity-check deployment config**

Run:

```powershell
Select-String -Path deploy/ai-service.env.example -Pattern '^KNOWLEDGE_RETRIEVER='
```

Expected: `KNOWLEDGE_RETRIEVER=lightrag`

---

## Self-Review

- Spec coverage: retrieval cutover, delete contract, attribution cutover, rebuild consistency, and deployment defaults are all covered by explicit tasks.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: tasks refer consistently to `delete_document(document_id)` on ai-service side and backend delete bridge through `AiKnowledgeBaseClient`.
