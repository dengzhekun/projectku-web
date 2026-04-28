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


class ChatResponse(BaseModel):
    answer: str
    confidence: Optional[float] = None
    route: Optional[str] = None
    sourceType: Optional[str] = None
    citations: list[Citation] = Field(default_factory=list)
    actions: list[Action] = Field(default_factory=list)
    hitLogs: list[HitLog] = Field(default_factory=list)
    fallbackReason: Optional[str] = None


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
    chunks: list[IndexChunk] = Field(default_factory=list)


class IndexResponse(BaseModel):
    documentId: int
    indexedChunkCount: int
    embeddingProvider: str
    vectorCollection: str
