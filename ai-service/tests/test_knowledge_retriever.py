import sys
import types

import pytest

from app.config import Settings
from app.retrieval.knowledge_retriever import build_knowledge_retriever
from app.retrieval.lightrag_retriever import LightRagRetriever


def test_build_knowledge_retriever_rejects_chroma_backend():
    settings = Settings(knowledge_retriever="chroma")

    with pytest.raises(ValueError, match="Unsupported knowledge retriever"):
        build_knowledge_retriever(settings, object())


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


def test_build_knowledge_retriever_rejects_lightrag_with_chroma_fallback():
    settings = Settings(knowledge_retriever="lightrag_with_chroma_fallback")

    with pytest.raises(ValueError, match="Unsupported knowledge retriever"):
        build_knowledge_retriever(settings, object())


def test_build_knowledge_retriever_rejects_unsupported_backend():
    settings = Settings(knowledge_retriever="unknown")

    with pytest.raises(ValueError, match="Unsupported knowledge retriever"):
        build_knowledge_retriever(settings, object())


def test_settings_default_knowledge_retriever_is_lightrag():
    settings = Settings(_env_file=None)

    assert settings.knowledge_retriever == "lightrag"
