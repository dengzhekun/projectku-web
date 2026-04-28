from fastapi import APIRouter, HTTPException

from app.api.chat import get_knowledge_retriever
from app.config import get_settings
from app.schemas import IndexRequest, IndexResponse

router = APIRouter()


@router.post("/internal/index", response_model=IndexResponse)
def index_document(request: IndexRequest) -> IndexResponse:
    if not request.chunks:
        raise HTTPException(status_code=400, detail="chunks are required")

    records = [
        {
            "id": f"kb:{request.documentId}:{request.version}:{chunk.chunkIndex}",
            "document": chunk.content,
            "metadata": {
                "source_type": "kb",
                "source_id": f"kb:{request.documentId}:{request.version}:{chunk.chunkIndex}",
                "document_id": request.documentId,
                "chunk_id": chunk.chunkId,
                "chunk_index": chunk.chunkIndex,
                "title": request.title or f"kb-{request.documentId}",
                "category": request.category or "",
                "version": request.version,
            },
        }
        for chunk in request.chunks
    ]
    retriever = get_knowledge_retriever()
    retriever.delete_document(request.documentId)
    retriever.upsert(records)
    settings = get_settings()
    return IndexResponse(
        documentId=request.documentId,
        indexedChunkCount=len(records),
        embeddingProvider=settings.ai_embedding_provider,
        vectorCollection=settings.chroma_collection,
    )
