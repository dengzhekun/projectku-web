from app.retrieval.lightrag_retriever import LightRagRetriever
from app.retrieval.lightrag_metadata_codec import (
    decode_document_with_metadata,
    encode_document_with_metadata,
)


def test_lightrag_query_normalizes_sources_shape():
    class DummyClient:
        def __init__(self):
            self.calls = []

        def query(self, text: str, top_k: int = 6):
            self.calls.append((text, top_k))
            return {
                "response": "answer text",
                "sources": [
                    {"content": "source text", "id": "s1", "title": "Doc"},
                ],
            }

        def insert_texts(self, texts):
            return None

    client = DummyClient()
    retriever = LightRagRetriever(client)

    chunks = retriever.query("what", top_k=4)

    assert client.calls == [("what", 4)]
    assert chunks == [
        {
            "document": "source text",
            "metadata": {
                "source_type": "lightrag",
                "source_id": "s1",
                "title": "Doc",
            },
        }
    ]


def test_lightrag_query_prefers_decoded_metadata_from_source_content():
    encoded_content = encode_document_with_metadata(
        "indexed chunk text",
        {
            "source_type": "kb",
            "source_id": "chunk-81",
            "document_id": "8",
            "chunk_id": "81",
            "chunk_index": "0",
            "category": "policy",
            "version": "2",
            "title": "Doc 8",
        },
    )

    class DummyClient:
        def query(self, text: str, top_k: int = 6):
            return {
                "sources": [
                    {
                        "id": "fallback-id",
                        "title": "fallback-title",
                        "content": encoded_content,
                    }
                ]
            }

        def insert_texts(self, texts):
            return None

    retriever = LightRagRetriever(DummyClient())
    chunks = retriever.query("what")

    assert chunks == [
        {
            "document": "indexed chunk text",
            "metadata": {
                "source_type": "kb",
                "source_id": "chunk-81",
                "title": "Doc 8",
                "document_id": 8,
                "chunk_id": 81,
                "chunk_index": 0,
                "category": "policy",
                "version": 2,
            },
        }
    ]


def test_lightrag_query_skips_non_string_source_content():
    class DummyClient:
        def query(self, text: str, top_k: int = 6):
            return {
                "sources": [
                    {"content": {"nested": "not-valid"}, "id": "s1", "title": "Doc 1"},
                    {"content": ["not-valid"], "id": "s2", "title": "Doc 2"},
                    {"content": "valid content", "id": "s3", "title": "Doc 3"},
                ]
            }

        def insert_texts(self, texts):
            return None

    retriever = LightRagRetriever(DummyClient())
    chunks = retriever.query("what")

    assert chunks == [
        {
            "document": "valid content",
            "metadata": {
                "source_type": "lightrag",
                "source_id": "s3",
                "title": "Doc 3",
            },
        }
    ]


def test_lightrag_query_keeps_fallback_metadata_when_no_envelope():
    class DummyClient:
        def query(self, text: str, top_k: int = 6):
            return {"sources": [{"content": "plain source text", "id": "s1", "title": "Doc"}]}

        def insert_texts(self, texts):
            return None

    retriever = LightRagRetriever(DummyClient())
    chunks = retriever.query("what")

    assert chunks == [
        {
            "document": "plain source text",
            "metadata": {
                "source_type": "lightrag",
                "source_id": "s1",
                "title": "Doc",
            },
        }
    ]


def test_lightrag_query_fallbacks_to_answer_text():
    class DummyClient:
        def query(self, text: str, top_k: int = 6):
            return {"answer": "answer text"}

        def insert_texts(self, texts):
            return None

    retriever = LightRagRetriever(DummyClient())

    chunks = retriever.query("what")

    assert chunks == [
        {
            "document": "answer text",
            "metadata": {
                "source_type": "lightrag",
                "source_id": "answer",
                "title": "LightRAG answer",
            },
        }
    ]


def test_lightrag_upsert_calls_insert_texts_with_documents():
    class DummyClient:
        def __init__(self):
            self.inserted = []

        def query(self, text: str, top_k: int = 6):
            return {}

        def insert_texts(self, texts):
            self.inserted.append(texts)

    client = DummyClient()
    retriever = LightRagRetriever(client)
    records = [
        {"id": "1", "document": "doc-1", "metadata": {"source_type": "faq", "source_id": "1"}},
        {"id": "2", "document": "doc-2", "metadata": {"source_type": "faq", "source_id": "2"}},
    ]

    retriever.upsert(records)

    assert len(client.inserted) == 1
    assert len(client.inserted[0]) == 2

    first_doc, first_metadata = decode_document_with_metadata(client.inserted[0][0])
    second_doc, second_metadata = decode_document_with_metadata(client.inserted[0][1])
    assert first_doc == "doc-1"
    assert first_metadata == {"source_type": "faq", "source_id": "1"}
    assert second_doc == "doc-2"
    assert second_metadata == {"source_type": "faq", "source_id": "2"}
