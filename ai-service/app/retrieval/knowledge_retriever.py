from typing import Any, Protocol

from app.config import Settings
from app.retrieval.chroma_retriever import ChromaRetriever
from app.retrieval.lightrag_retriever import LightRagRetriever


class KnowledgeRetriever(Protocol):
    def query(self, text: str, top_k: int = 6) -> list[dict[str, Any]]:
        ...

    def upsert(self, records: list[dict[str, Any]]) -> None:
        ...

    def delete_document(self, document_id: int) -> None:
        ...


class FallbackKnowledgeRetriever:
    def __init__(self, primary: KnowledgeRetriever, fallback: KnowledgeRetriever):
        self.primary = primary
        self.fallback = fallback

    def query(self, text: str, top_k: int = 6) -> list[dict[str, Any]]:
        try:
            primary_result = self.primary.query(text, top_k=top_k)
            if primary_result:
                return primary_result
        except Exception:
            pass

        return self.fallback.query(text, top_k=top_k)

    def upsert(self, records: list[dict[str, Any]]) -> None:
        try:
            self.primary.upsert(records)
        except Exception:
            pass

        self.fallback.upsert(records)

    def delete_document(self, document_id: int) -> None:
        try:
            self.primary.delete_document(document_id)
        except Exception:
            pass

        self.fallback.delete_document(document_id)


def _build_lightrag_retriever(settings: Settings) -> LightRagRetriever:
    from app.clients.lightrag_client import LightRagClient

    return LightRagRetriever(LightRagClient(settings))


def build_knowledge_retriever(settings: Settings, embedding_client) -> KnowledgeRetriever:
    backend = settings.knowledge_retriever.strip().lower()
    if backend == "chroma":
        return ChromaRetriever(settings, embedding_client)
    if backend == "lightrag":
        return _build_lightrag_retriever(settings)
    if backend == "lightrag_with_chroma_fallback":
        return FallbackKnowledgeRetriever(
            primary=_build_lightrag_retriever(settings),
            fallback=ChromaRetriever(settings, embedding_client),
        )

    raise ValueError(f"Unsupported knowledge retriever: {settings.knowledge_retriever}")
