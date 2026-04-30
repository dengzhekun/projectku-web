from app.api import indexing
from fastapi import HTTPException
from app.schemas import DeleteDocumentRequest, IndexChunk, IndexRequest


def test_index_document_replaces_existing_vectors_before_upsert(monkeypatch):
    calls = []

    class DummyRetriever:
        def delete_document(self, document_id: int, require_existing: bool = False):
            calls.append(("delete", document_id, require_existing))

        def upsert(self, records):
            calls.append(("upsert", [record["id"] for record in records]))

    monkeypatch.setattr(indexing, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(
        indexing,
        "get_settings",
        lambda: type(
            "Settings",
            (),
            {"ai_embedding_provider": "test-embedding", "lightrag_collection": "kb-light"},
        )(),
    )

    response = indexing.index_document(
        IndexRequest(
            documentId=5,
            version=3,
            title="After Sales",
            category="after_sales",
            chunks=[IndexChunk(chunkId=701, chunkIndex=0, content="current policy")],
        )
    )

    assert calls == [("delete", 5, True), ("upsert", ["kb:5:3:0"])]
    assert response.indexedChunkCount == 1


def test_index_document_allows_first_version_without_existing_mapping(monkeypatch):
    calls = []

    class DummyRetriever:
        def delete_document(self, document_id: int, require_existing: bool = False):
            calls.append(("delete", document_id, require_existing))

        def upsert(self, records):
            calls.append(("upsert", [record["id"] for record in records]))

    monkeypatch.setattr(indexing, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(
        indexing,
        "get_settings",
        lambda: type(
            "Settings",
            (),
            {"ai_embedding_provider": "test-embedding", "lightrag_collection": "kb-light"},
        )(),
    )

    response = indexing.index_document(
        IndexRequest(
            documentId=5,
            version=1,
            title="After Sales",
            category="after_sales",
            chunks=[IndexChunk(chunkId=701, chunkIndex=0, content="current policy")],
        )
    )

    assert calls == [("delete", 5, False), ("upsert", ["kb:5:1:0"])]
    assert response.indexedChunkCount == 1


def test_index_document_returns_conflict_when_reindex_mapping_is_missing(monkeypatch):
    class DummyRetriever:
        def delete_document(self, document_id: int, require_existing: bool = False):
            if require_existing:
                raise LookupError(f"missing mapping for {document_id}")

        def upsert(self, records):
            raise AssertionError("reindex should stop before upsert when mapping is missing")

    monkeypatch.setattr(indexing, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(
        indexing,
        "get_settings",
        lambda: type(
            "Settings",
            (),
            {"ai_embedding_provider": "test-embedding", "lightrag_collection": "kb-light"},
        )(),
    )

    try:
        indexing.index_document(
            IndexRequest(
                documentId=5,
                version=2,
                title="After Sales",
                category="after_sales",
                chunks=[IndexChunk(chunkId=701, chunkIndex=0, content="current policy")],
            )
        )
        raised = None
    except HTTPException as exc:
        raised = exc

    assert raised is not None
    assert raised.status_code == 409
    assert raised.detail == "missing mapping for 5"


def test_index_document_recovers_missing_mapping_when_requested(monkeypatch):
    calls = []

    class DummyRetriever:
        def __init__(self):
            self.seeded = False

        def delete_document(self, document_id: int, require_existing: bool = False):
            calls.append(("delete", document_id, require_existing))
            if require_existing and not self.seeded:
                raise LookupError(f"missing mapping for {document_id}")

        def seed_registry_for_records(self, records):
            calls.append(("seed", [record["id"] for record in records]))
            self.seeded = True
            return True

        def upsert(self, records):
            calls.append(("upsert", [record["id"] for record in records]))

    monkeypatch.setattr(indexing, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(
        indexing,
        "get_settings",
        lambda: type(
            "Settings",
            (),
            {"ai_embedding_provider": "test-embedding", "lightrag_collection": "kb-light"},
        )(),
    )

    response = indexing.index_document(
        IndexRequest(
            documentId=5,
            version=3,
            title="After Sales",
            category="after_sales",
            recoverMapping=True,
            chunks=[IndexChunk(chunkId=701, chunkIndex=0, content="current policy")],
        )
    )

    assert calls == [
        ("delete", 5, True),
        ("seed", ["kb:5:3:0"]),
        ("delete", 5, True),
        ("upsert", ["kb:5:3:0"]),
    ]
    assert response.indexedChunkCount == 1


def test_delete_document_calls_retriever_delete(monkeypatch):
    calls = []

    class DummyRetriever:
        def delete_document(self, document_id: int, require_existing: bool = False):
            calls.append(("delete", document_id, require_existing))

        def upsert(self, records):
            raise AssertionError("delete endpoint should not upsert")

    monkeypatch.setattr(indexing, "get_knowledge_retriever", lambda: DummyRetriever())

    response = indexing.delete_document(DeleteDocumentRequest(documentId=9))

    assert calls == [("delete", 9, True)]
    assert response.documentId == 9


def test_delete_document_returns_conflict_when_mapping_is_missing(monkeypatch):
    class DummyRetriever:
        def delete_document(self, document_id: int, require_existing: bool = False):
            raise LookupError(f"missing mapping for {document_id}")

        def upsert(self, records):
            raise AssertionError("delete endpoint should not upsert")

    monkeypatch.setattr(indexing, "get_knowledge_retriever", lambda: DummyRetriever())

    try:
        indexing.delete_document(DeleteDocumentRequest(documentId=9))
        raised = None
    except HTTPException as exc:
        raised = exc

    assert raised is not None
    assert raised.status_code == 409
    assert raised.detail == "missing mapping for 9"
