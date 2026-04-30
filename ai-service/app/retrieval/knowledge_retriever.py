from typing import Any, Protocol

from app.config import Settings
from app.retrieval.lightrag_doc_registry import LightRagDocRegistry
from app.retrieval.lightrag_retriever import LightRagRetriever


class KnowledgeRetriever(Protocol):
    def query(self, text: str, top_k: int = 6) -> list[dict[str, Any]]:
        ...

    def upsert(self, records: list[dict[str, Any]]) -> None:
        ...

    def delete_document(self, document_id: int, require_existing: bool = False) -> None:
        ...


def _build_lightrag_retriever(settings: Settings) -> LightRagRetriever:
    from app.clients.lightrag_client import LightRagClient

    return LightRagRetriever(
        LightRagClient(settings),
        registry=LightRagDocRegistry(settings.lightrag_doc_registry_path),
    )


def build_knowledge_retriever(settings: Settings, embedding_client) -> KnowledgeRetriever:
    backend = settings.knowledge_retriever.strip().lower()
    if backend == "lightrag":
        return _build_lightrag_retriever(settings)

    raise ValueError(f"Unsupported knowledge retriever: {settings.knowledge_retriever}")
