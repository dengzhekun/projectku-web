from typing import Any

from app.retrieval.lightrag_metadata_codec import (
    decode_document_with_metadata,
    encode_document_with_metadata,
)


class LightRagRetriever:
    def __init__(self, client):
        self.client = client

    def query(self, text: str, top_k: int = 6) -> list[dict[str, Any]]:
        result = self.client.query(text, top_k=top_k)
        if not isinstance(result, dict):
            return []

        sources = result.get("sources")
        if isinstance(sources, list):
            chunks = []
            for source in sources:
                if not isinstance(source, dict):
                    continue
                content = source.get("content")
                if not isinstance(content, str) or not content:
                    continue
                document, decoded_metadata = decode_document_with_metadata(content)
                metadata: dict[str, Any] = {
                    "source_type": "lightrag",
                    "source_id": source.get("id"),
                    "title": source.get("title"),
                }
                if isinstance(decoded_metadata, dict):
                    metadata.update(decoded_metadata)
                chunks.append({"document": document, "metadata": metadata})
            if chunks:
                return chunks

        answer = result.get("answer") or result.get("response")
        if isinstance(answer, str) and answer.strip():
            return [
                {
                    "document": answer,
                    "metadata": {
                        "source_type": "lightrag",
                        "source_id": "answer",
                        "title": "LightRAG answer",
                    },
                }
            ]

        return []

    def upsert(self, records: list[dict[str, Any]]) -> None:
        if not records:
            return

        documents = []
        for record in records:
            if not isinstance(record, dict) or not record.get("document"):
                continue
            documents.append(
                encode_document_with_metadata(
                    document=record["document"],
                    metadata=record.get("metadata") if isinstance(record.get("metadata"), dict) else None,
                )
            )
        if not documents:
            return

        self.client.insert_texts(documents)

    def delete_document(self, document_id: int) -> None:
        # LightRAG's HTTP API used by this project has no stable chunk-level delete.
        # Chat retrieval filters stale document versions, while Chroma can delete exactly.
        return None
