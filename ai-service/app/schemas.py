from typing import Optional

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    message: str
    conversationId: Optional[str] = None
    productId: Optional[int] = None
    orderId: Optional[int] = None
    scene: Optional[str] = None
    authToken: Optional[str] = None


class Citation(BaseModel):
    sourceType: str
    sourceId: str
    title: str


class Action(BaseModel):
    type: str
    label: str
    url: Optional[str] = None


class HitLog(BaseModel):
    documentId: int
    chunkId: int


class RetrievalTrace(BaseModel):
    route: Optional[str] = None
    sourceType: Optional[str] = None
    retriever: Optional[str] = None
    requestedTopK: Optional[int] = None
    returnedChunkCount: int = 0
    selectedChunkCount: int = 0
    citationCount: int = 0
    hitLogCount: int = 0
    attributionStatus: str = "none"
    selectedCategories: list[str] = Field(default_factory=list)
    selectedSourceIds: list[str] = Field(default_factory=list)
    fallbackReason: Optional[str] = None
    notes: list[str] = Field(default_factory=list)


class ChatResponse(BaseModel):
    answer: str
    confidence: Optional[float] = None
    route: Optional[str] = None
    sourceType: Optional[str] = None
    citations: list[Citation] = Field(default_factory=list)
    actions: list[Action] = Field(default_factory=list)
    hitLogs: list[HitLog] = Field(default_factory=list)
    fallbackReason: Optional[str] = None
    retrievalTrace: Optional[RetrievalTrace] = None


class IndexChunk(BaseModel):
    chunkId: int
    chunkIndex: int
    content: str
    charCount: Optional[int] = None


class IndexRequest(BaseModel):
    documentId: int
    version: int
    title: Optional[str] = None
    category: Optional[str] = None
    recoverMapping: bool = False
    chunks: list[IndexChunk] = Field(default_factory=list)


class IndexResponse(BaseModel):
    documentId: int
    indexedChunkCount: int
    embeddingProvider: str
    vectorCollection: str


class DeleteDocumentRequest(BaseModel):
    documentId: int


class DeleteDocumentResponse(BaseModel):
    documentId: int
