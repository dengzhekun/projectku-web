from app.retrieval.chroma_retriever import build_chroma_client_settings
from app.retrieval.chroma_retriever import merge_context


def test_merge_context_includes_source_metadata():
    chunks = [
        {"document": "七天无理由适用于未拆封商品", "metadata": {"source_type": "policy", "source_id": "return_7d"}},
        {"document": "订单详情页可以发起售后", "metadata": {"source_type": "faq", "source_id": "after_sale_entry"}},
    ]
    merged = merge_context(chunks)
    assert "七天无理由适用于未拆封商品" in merged
    assert "policy:return_7d" in merged


def test_build_chroma_client_settings_disables_anonymized_telemetry():
    settings = build_chroma_client_settings(anonymized_telemetry=False)

    assert settings.anonymized_telemetry is False


def test_chroma_delete_document_removes_vectors_by_document_id():
    class DummyCollection:
        def __init__(self):
            self.deleted = []

        def delete(self, where):
            self.deleted.append(where)

    retriever = object.__new__(__import__("app.retrieval.chroma_retriever", fromlist=["ChromaRetriever"]).ChromaRetriever)
    retriever.collection = DummyCollection()

    retriever.delete_document(5)

    assert retriever.collection.deleted == [{"document_id": 5}]
