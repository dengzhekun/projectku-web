# LightRAG Phase 1 Retrieval Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple the AI service from direct Chroma usage so LightRAG can be added later behind a stable knowledge retrieval interface without changing product-tool routing.

**Architecture:** Keep the current runtime behavior as Chroma-only in Phase 1. Add a `KnowledgeRetriever` protocol/factory, route chat and indexing through that abstraction, and keep `ChromaRetriever` as the only enabled implementation. This creates a clear insertion point for LightRAG in Phase 2 while preserving current fallback behavior and tests.

**Tech Stack:** Python 3, FastAPI, Pydantic settings, pytest, existing ChromaDB retriever, existing Spring Boot caller.

---

## Scope

This phase does **not** install or run LightRAG. It only prepares the AI service so LightRAG can be connected cleanly in the next phase.

In scope:

- Add a knowledge retrieval abstraction.
- Add a factory function with a config switch.
- Keep Chroma as the default and only working backend.
- Update chat and indexing API to use the abstraction.
- Add tests proving behavior is unchanged and unsupported retriever values fail clearly.

Out of scope:

- LightRAG container.
- LightRAG API client.
- LightRAG indexing.
- Chroma deletion.
- Frontend changes.
- Backend Java changes.
- Real-time web search.

## File Structure

- Create: `ai-service/app/retrieval/knowledge_retriever.py`  
  Defines the retriever protocol, supported retriever modes, and `build_knowledge_retriever(...)`.

- Modify: `ai-service/app/config.py`  
  Adds `knowledge_retriever` setting, defaulting to `chroma`.

- Modify: `ai-service/app/api/chat.py`  
  Adds `get_knowledge_retriever()` and replaces direct Chroma calls in chat paths.

- Modify: `ai-service/app/api/indexing.py`  
  Uses `get_knowledge_retriever()` for document indexing.

- Modify: `ai-service/app/ingest/sync_job.py`  
  Uses the factory for seed sync.

- Test: `ai-service/tests/test_knowledge_retriever.py`  
  Verifies the factory selects Chroma and rejects unknown modes.

- Modify tests:
  - `ai-service/tests/test_chat_api.py`
  - `ai-service/tests/test_indexing_api.py`

## Task 1: Add Retrieval Factory

**Files:**
- Create: `ai-service/app/retrieval/knowledge_retriever.py`
- Test: `ai-service/tests/test_knowledge_retriever.py`

- [ ] **Step 1: Write failing tests for retriever factory**

Create `ai-service/tests/test_knowledge_retriever.py`:

```python
from types import SimpleNamespace

import pytest

from app.retrieval.chroma_retriever import ChromaRetriever
from app.retrieval.knowledge_retriever import build_knowledge_retriever


class DummyEmbeddingClient:
    def embed(self, text: str):
        return [0.1, 0.2, 0.3]


def test_build_knowledge_retriever_returns_chroma_by_default(monkeypatch, tmp_path):
    created = {}

    class DummyChromaRetriever:
        def __init__(self, settings, embedding_client):
            created["settings"] = settings
            created["embedding_client"] = embedding_client

    monkeypatch.setattr(
        "app.retrieval.knowledge_retriever.ChromaRetriever",
        DummyChromaRetriever,
    )
    settings = SimpleNamespace(
        knowledge_retriever="chroma",
        chroma_path=str(tmp_path),
        chroma_collection="ecommerce_kb_v1",
        chroma_anonymized_telemetry=False,
    )
    embedding_client = DummyEmbeddingClient()

    retriever = build_knowledge_retriever(settings, embedding_client)

    assert isinstance(retriever, DummyChromaRetriever)
    assert created["settings"] is settings
    assert created["embedding_client"] is embedding_client


def test_build_knowledge_retriever_rejects_unknown_backend(tmp_path):
    settings = SimpleNamespace(
        knowledge_retriever="unknown",
        chroma_path=str(tmp_path),
        chroma_collection="ecommerce_kb_v1",
        chroma_anonymized_telemetry=False,
    )

    with pytest.raises(ValueError) as exc:
        build_knowledge_retriever(settings, DummyEmbeddingClient())

    assert "Unsupported knowledge retriever" in str(exc.value)
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
python -m pytest ai-service/tests/test_knowledge_retriever.py -q
```

Expected: FAIL because `app.retrieval.knowledge_retriever` does not exist.

- [ ] **Step 3: Add minimal factory implementation**

Create `ai-service/app/retrieval/knowledge_retriever.py`:

```python
from typing import Any, Protocol

from app.retrieval.chroma_retriever import ChromaRetriever


class KnowledgeRetriever(Protocol):
    def query(self, text: str, top_k: int = 6) -> list[dict[str, Any]]:
        ...

    def upsert(self, records: list[dict[str, Any]]) -> None:
        ...


def build_knowledge_retriever(settings, embedding_client) -> KnowledgeRetriever:
    retriever = getattr(settings, "knowledge_retriever", "chroma").strip().lower()
    if retriever == "chroma":
        return ChromaRetriever(settings, embedding_client)
    raise ValueError(f"Unsupported knowledge retriever: {retriever}")
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
python -m pytest ai-service/tests/test_knowledge_retriever.py -q
```

Expected: PASS.

## Task 2: Add Config Switch

**Files:**
- Modify: `ai-service/app/config.py`
- Test: `ai-service/tests/test_knowledge_retriever.py`

- [ ] **Step 1: Add failing test for default config value**

Append to `ai-service/tests/test_knowledge_retriever.py`:

```python
def test_settings_default_knowledge_retriever_is_chroma(monkeypatch, tmp_path):
    from app.config import Settings

    monkeypatch.setenv("CHROMA_PATH", str(tmp_path / "chroma"))
    settings = Settings(_env_file=None)

    assert settings.knowledge_retriever == "chroma"
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
python -m pytest ai-service/tests/test_knowledge_retriever.py::test_settings_default_knowledge_retriever_is_chroma -q
```

Expected: FAIL because `Settings` has no `knowledge_retriever` field.

- [ ] **Step 3: Add setting**

Modify `ai-service/app/config.py` inside `class Settings`, placing the field near Chroma settings:

```python
    knowledge_retriever: str = "chroma"

    chroma_path: str = "./data/chroma"
    chroma_collection: str = "ecommerce_kb_v1"
    chroma_anonymized_telemetry: bool = False
```

- [ ] **Step 4: Run focused tests**

Run:

```powershell
python -m pytest ai-service/tests/test_knowledge_retriever.py -q
```

Expected: PASS.

## Task 3: Route Chat Through Knowledge Retriever

**Files:**
- Modify: `ai-service/app/api/chat.py`
- Modify: `ai-service/tests/test_chat_api.py`

- [ ] **Step 1: Update tests to patch the abstraction instead of Chroma**

In `ai-service/tests/test_chat_api.py`, replace each direct patch:

```python
monkeypatch.setattr(chat_api, "get_chroma_retriever", lambda: DummyRetriever())
```

with:

```python
monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyRetriever())
```

Apply the same replacement for `FailingRetriever()` in the product-tool test.

- [ ] **Step 2: Run chat tests to verify failure**

Run:

```powershell
python -m pytest ai-service/tests/test_chat_api.py -q
```

Expected: FAIL because `get_knowledge_retriever` does not exist in `chat.py`.

- [ ] **Step 3: Add chat retriever factory accessor**

Modify imports in `ai-service/app/api/chat.py`:

```python
from app.retrieval.chroma_retriever import ChromaRetriever, merge_context
from app.retrieval.knowledge_retriever import build_knowledge_retriever, KnowledgeRetriever
```

Add this function below `get_chroma_retriever()`:

```python
@lru_cache(maxsize=1)
def get_knowledge_retriever() -> KnowledgeRetriever:
    retriever = build_knowledge_retriever(get_settings(), get_embedding_client())
    if hasattr(retriever, "collection") and retriever.collection.count() == 0:
        retriever.upsert(build_seed_records())
    return retriever
```

Then replace both direct query calls in `handle_chat` and `handle_chat_stream`:

```python
chunks = get_chroma_retriever().query(message, top_k=6)
```

with:

```python
chunks = get_knowledge_retriever().query(message, top_k=6)
```

Keep `get_chroma_retriever()` for now because the factory and older tests may still use it during this phase.

- [ ] **Step 4: Run chat tests**

Run:

```powershell
python -m pytest ai-service/tests/test_chat_api.py -q
```

Expected: PASS.

## Task 4: Route Indexing Through Knowledge Retriever

**Files:**
- Modify: `ai-service/app/api/indexing.py`
- Modify: `ai-service/tests/test_indexing_api.py`

- [ ] **Step 1: Update indexing test patch**

In `ai-service/tests/test_indexing_api.py`, replace:

```python
monkeypatch.setattr("app.api.indexing.get_chroma_retriever", lambda: DummyRetriever())
```

with:

```python
monkeypatch.setattr("app.api.indexing.get_knowledge_retriever", lambda: DummyRetriever())
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
python -m pytest ai-service/tests/test_indexing_api.py -q
```

Expected: FAIL because `indexing.py` has not imported `get_knowledge_retriever`.

- [ ] **Step 3: Modify indexing API**

In `ai-service/app/api/indexing.py`, replace:

```python
from app.api.chat import get_chroma_retriever
```

with:

```python
from app.api.chat import get_knowledge_retriever
```

Replace:

```python
get_chroma_retriever().upsert(records)
```

with:

```python
get_knowledge_retriever().upsert(records)
```

Keep `IndexResponse.vectorCollection` unchanged for this phase:

```python
vectorCollection=settings.chroma_collection,
```

This preserves the API contract until LightRAG indexing is introduced.

- [ ] **Step 4: Run indexing test**

Run:

```powershell
python -m pytest ai-service/tests/test_indexing_api.py -q
```

Expected: PASS.

## Task 5: Route Seed Sync Through Factory

**Files:**
- Modify: `ai-service/app/ingest/sync_job.py`
- Test: existing import/runtime smoke test

- [ ] **Step 1: Modify sync job imports**

In `ai-service/app/ingest/sync_job.py`, replace:

```python
from app.retrieval.chroma_retriever import ChromaRetriever
```

with:

```python
from app.retrieval.knowledge_retriever import build_knowledge_retriever
```

- [ ] **Step 2: Modify retriever construction**

Replace:

```python
retriever = ChromaRetriever(settings, embedding_client)
```

with:

```python
retriever = build_knowledge_retriever(settings, embedding_client)
```

- [ ] **Step 3: Run import smoke check**

Run:

```powershell
python -c "from app.ingest.sync_job import sync_seed_documents; print(sync_seed_documents)"
```

from the `ai-service` directory.

Expected: prints a function object without import errors.

## Task 6: Full Verification

**Files:**
- No code changes unless tests expose an issue.

- [ ] **Step 1: Run focused AI service tests**

Run:

```powershell
python -m pytest ai-service/tests/test_knowledge_retriever.py ai-service/tests/test_chat_api.py ai-service/tests/test_indexing_api.py -q
```

Expected: PASS.

- [ ] **Step 2: Run all AI service tests**

Run:

```powershell
python -m pytest ai-service/tests -q
```

Expected: PASS.

- [ ] **Step 3: Verify no direct Chroma calls remain in orchestration files**

Run:

```powershell
rg -n "get_chroma_retriever\\(\\)\\.query|get_chroma_retriever\\(\\)\\.upsert|from app.api.chat import get_chroma_retriever" ai-service
```

Expected: no matches.

It is acceptable for `ChromaRetriever` itself and `get_chroma_retriever()` to remain in the codebase during Phase 1.

## Acceptance Criteria

- `KNOWLEDGE_RETRIEVER` defaults to `chroma`.
- Chat still answers with the same behavior as before.
- Product real-time queries still bypass knowledge retrieval when product data is found.
- Indexing API writes through the knowledge retriever abstraction.
- Seed sync writes through the knowledge retriever abstraction.
- Unsupported retriever names fail with a clear `ValueError`.
- Chroma remains available and is not deleted.

## Phase 2 Handoff

After this plan is implemented, Phase 2 can add:

- `ai-service/app/clients/lightrag_client.py`
- `ai-service/app/retrieval/lightrag_retriever.py`
- `KNOWLEDGE_RETRIEVER=lightrag`
- `KNOWLEDGE_RETRIEVER=lightrag_with_chroma_fallback`
- LightRAG container and environment config
- LightRAG indexing bridge

Do not start Phase 2 until Phase 1 tests pass.

## Self-Review

- Spec coverage: This plan only implements Phase 1 retrieval decoupling. It deliberately excludes LightRAG runtime and indexing migration.
- Placeholder scan: No placeholder tasks remain; each task includes file paths, code snippets, commands, and expected results.
- Type consistency: The protocol uses the existing Chroma record shape: `list[dict[str, Any]]` for both query results and upsert records.
