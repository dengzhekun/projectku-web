import sys
import types

import pytest

from app.config import Settings
from app.retrieval.knowledge_retriever import FallbackKnowledgeRetriever
from app.retrieval.knowledge_retriever import build_knowledge_retriever
from app.retrieval.lightrag_retriever import LightRagRetriever


def test_build_knowledge_retriever_returns_chroma_instance(monkeypatch):
    class DummyChromaRetriever:
        def __init__(self, settings, embedding_client):
            self.settings = settings
            self.embedding_client = embedding_client

    monkeypatch.setattr(
        "app.retrieval.knowledge_retriever.ChromaRetriever",
        DummyChromaRetriever,
    )

    settings = Settings(knowledge_retriever="chroma")
    embedding_client = object()

    retriever = build_knowledge_retriever(settings, embedding_client)

    assert isinstance(retriever, DummyChromaRetriever)
    assert retriever.settings is settings
    assert retriever.embedding_client is embedding_client


def test_build_knowledge_retriever_returns_lightrag_instance(monkeypatch):
    class DummyLightRagClient:
        def __init__(self, settings):
            self.settings = settings

    fake_module = types.ModuleType("app.clients.lightrag_client")
    fake_module.LightRagClient = DummyLightRagClient
    monkeypatch.setitem(sys.modules, "app.clients.lightrag_client", fake_module)

    settings = Settings(knowledge_retriever="lightrag")

    retriever = build_knowledge_retriever(settings, object())

    assert isinstance(retriever, LightRagRetriever)
    assert isinstance(retriever.client, DummyLightRagClient)
    assert retriever.client.settings is settings


def test_build_knowledge_retriever_returns_lightrag_with_chroma_fallback(monkeypatch):
    class DummyLightRagClient:
        def __init__(self, settings):
            self.settings = settings

    class DummyChromaRetriever:
        def __init__(self, settings, embedding_client):
            self.settings = settings
            self.embedding_client = embedding_client

    fake_module = types.ModuleType("app.clients.lightrag_client")
    fake_module.LightRagClient = DummyLightRagClient
    monkeypatch.setitem(sys.modules, "app.clients.lightrag_client", fake_module)
    monkeypatch.setattr(
        "app.retrieval.knowledge_retriever.ChromaRetriever",
        DummyChromaRetriever,
    )

    settings = Settings(knowledge_retriever="lightrag_with_chroma_fallback")
    embedding_client = object()

    retriever = build_knowledge_retriever(settings, embedding_client)

    assert isinstance(retriever, FallbackKnowledgeRetriever)
    assert isinstance(retriever.primary, LightRagRetriever)
    assert isinstance(retriever.primary.client, DummyLightRagClient)
    assert isinstance(retriever.fallback, DummyChromaRetriever)
    assert retriever.fallback.settings is settings
    assert retriever.fallback.embedding_client is embedding_client


def test_fallback_query_uses_fallback_on_empty_results():
    class EmptyPrimary:
        def query(self, text: str, top_k: int = 6):
            return []

        def upsert(self, records):
            return None

    class Fallback:
        def __init__(self):
            self.calls = []

        def query(self, text: str, top_k: int = 6):
            self.calls.append((text, top_k))
            return [{"document": "fallback", "metadata": {"source_type": "chroma", "source_id": "1"}}]

        def upsert(self, records):
            return None

    fallback = Fallback()
    retriever = FallbackKnowledgeRetriever(primary=EmptyPrimary(), fallback=fallback)

    result = retriever.query("hello", top_k=3)

    assert fallback.calls == [("hello", 3)]
    assert result[0]["document"] == "fallback"


def test_fallback_upsert_writes_fallback_even_if_primary_fails():
    class FailingPrimary:
        def query(self, text: str, top_k: int = 6):
            return []

        def upsert(self, records):
            raise RuntimeError("boom")

    class Fallback:
        def __init__(self):
            self.upserts = []

        def query(self, text: str, top_k: int = 6):
            return []

        def upsert(self, records):
            self.upserts.append(records)

    fallback = Fallback()
    retriever = FallbackKnowledgeRetriever(primary=FailingPrimary(), fallback=fallback)
    records = [{"id": "1", "document": "doc", "metadata": {"source_type": "faq", "source_id": "1"}}]

    retriever.upsert(records)

    assert fallback.upserts == [records]


def test_build_knowledge_retriever_rejects_unsupported_backend():
    settings = Settings(knowledge_retriever="unknown")

    with pytest.raises(ValueError, match="Unsupported knowledge retriever"):
        build_knowledge_retriever(settings, object())


def test_settings_default_knowledge_retriever_is_chroma():
    settings = Settings(_env_file=None)

    assert settings.knowledge_retriever == "chroma"
