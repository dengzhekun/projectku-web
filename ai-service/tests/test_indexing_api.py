from app.api import indexing
from app.schemas import IndexChunk, IndexRequest


def test_index_document_replaces_existing_vectors_before_upsert(monkeypatch):
    calls = []

    class DummyRetriever:
        def delete_document(self, document_id: int):
            calls.append(("delete", document_id))

        def upsert(self, records):
            calls.append(("upsert", [record["id"] for record in records]))

    monkeypatch.setattr(indexing, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(
        indexing,
        "get_settings",
        lambda: type(
            "Settings",
            (),
            {"ai_embedding_provider": "test-embedding", "chroma_collection": "test-kb"},
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

    assert calls == [("delete", 5), ("upsert", ["kb:5:3:0"])]
    assert response.indexedChunkCount == 1
