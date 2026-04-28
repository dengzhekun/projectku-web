from fastapi import FastAPI

from app.api.chat import router as chat_router
from app.api.embeddings import router as embeddings_router
from app.api.indexing import router as indexing_router
from app.config import get_settings

app = FastAPI(title="projectku-ai-service")
app.include_router(chat_router)
app.include_router(indexing_router)
app.include_router(embeddings_router)


@app.get("/health")
def health() -> dict:
    settings = get_settings()
    return {
        "status": "ok",
        "llmProvider": settings.ai_llm_provider,
        "embeddingProvider": settings.ai_embedding_provider,
        "chromaPath": settings.chroma_path,
        "neo4jEnabled": bool(settings.neo4j_password),
    }
