from fastapi import APIRouter, HTTPException

from app.api.chat import get_knowledge_retriever
from app.config import get_settings
from app.schemas import DeleteDocumentRequest, DeleteDocumentResponse, IndexRequest, IndexResponse

router = APIRouter()


def _try_recover_mapping(retriever, records: list[dict]) -> bool:
    recovery = getattr(retriever, "seed_registry_for_records", None)
    if not callable(recovery):
        return False
    return bool(recovery(records))


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
    try:
        retriever.delete_document(request.documentId, require_existing=request.version > 1)
    except LookupError as exc:
        if request.recoverMapping and _try_recover_mapping(retriever, records):
            try:
                retriever.delete_document(request.documentId, require_existing=True)
            except LookupError as retry_exc:
                raise HTTPException(status_code=409, detail=str(retry_exc)) from retry_exc
        else:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
    retriever.upsert(records)
    settings = get_settings()
    return IndexResponse(
        documentId=request.documentId,
        indexedChunkCount=len(records),
        embeddingProvider=settings.ai_embedding_provider,
        vectorCollection=settings.lightrag_collection,
    )


@router.post("/internal/delete", response_model=DeleteDocumentResponse)
def delete_document(request: DeleteDocumentRequest) -> DeleteDocumentResponse:
    retriever = get_knowledge_retriever()
    try:
        retriever.delete_document(request.documentId, require_existing=True)
    except LookupError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    return DeleteDocumentResponse(documentId=request.documentId)
