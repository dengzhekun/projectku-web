from typing import Any

from app.config import Settings


def merge_context(chunks: list[dict[str, Any]]) -> str:
    lines: list[str] = []
    for chunk in chunks:
        metadata = chunk["metadata"]
        lines.append(f"[{metadata['source_type']}:{metadata['source_id']}] {chunk['document']}")
    return "\n".join(lines)


def build_chroma_client_settings(anonymized_telemetry: bool):
    from chromadb.config import Settings as ChromaSettings

    return ChromaSettings(anonymized_telemetry=anonymized_telemetry)


class ChromaRetriever:
    def __init__(self, settings: Settings, embedding_client):
        import chromadb

        self.client = chromadb.PersistentClient(
            path=settings.chroma_path,
            settings=build_chroma_client_settings(settings.chroma_anonymized_telemetry),
        )
        self.collection = self.client.get_or_create_collection(name=settings.chroma_collection)
        self.embedding_client = embedding_client

    def query(self, text: str, top_k: int = 6) -> list[dict[str, Any]]:
        if self.collection.count() == 0:
            return []
        embedding = self.embedding_client.embed(text)
        result = self.collection.query(query_embeddings=[embedding], n_results=top_k)
        documents = result.get("documents", [[]])[0]
        metadatas = result.get("metadatas", [[]])[0]
        return [
            {"document": document, "metadata": metadata}
            for document, metadata in zip(documents, metadatas)
        ]

    def upsert(self, records: list[dict[str, Any]]) -> None:
        if not records:
            return
        self.collection.upsert(
            ids=[record["id"] for record in records],
            documents=[record["document"] for record in records],
            metadatas=[record["metadata"] for record in records],
            embeddings=[self.embedding_client.embed(record["document"]) for record in records],
        )

    def delete_document(self, document_id: int) -> None:
        self.collection.delete(where={"document_id": int(document_id)})
