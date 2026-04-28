from functools import lru_cache

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, StrictStr

from app.clients.embedding_client import EmbeddingClient
from app.config import get_settings

router = APIRouter()


class EmbeddingsRequest(BaseModel):
    model: StrictStr
    input: StrictStr | list[StrictStr]
    encoding_format: StrictStr | None = None


@lru_cache(maxsize=1)
def get_embedding_client() -> EmbeddingClient:
    return EmbeddingClient(get_settings())


def require_gateway_auth(authorization: str | None, api_key: str) -> None:
    if not api_key:
        return
    if not authorization:
        raise HTTPException(status_code=401, detail="Unauthorized")
    parts = authorization.split(" ", 1)
    if len(parts) != 2 or parts[0].lower() != "bearer" or parts[1] != api_key:
        raise HTTPException(status_code=401, detail="Unauthorized")


def normalize_input(raw_input: str | list[str]) -> list[str]:
    texts = [raw_input] if isinstance(raw_input, str) else raw_input
    if not texts:
        raise HTTPException(status_code=400, detail="input must not be empty")
    if any(not text.strip() for text in texts):
        raise HTTPException(status_code=400, detail="input must not contain empty text")
    return texts


@router.post("/v1/embeddings")
def create_embeddings(
    request: EmbeddingsRequest,
    authorization: str | None = Header(default=None),
) -> dict:
    settings = get_settings()
    require_gateway_auth(authorization, settings.ai_embedding_gateway_api_key)

    if request.encoding_format and request.encoding_format != "float":
        raise HTTPException(status_code=400, detail="encoding_format must be 'float'")

    texts = normalize_input(request.input)
    client = get_embedding_client()
    data = [
        {"object": "embedding", "index": index, "embedding": client.embed(text)}
        for index, text in enumerate(texts)
    ]
    prompt_tokens = sum(len(text) for text in texts)
    return {
        "object": "list",
        "data": data,
        "model": request.model,
        "usage": {"prompt_tokens": prompt_tokens, "total_tokens": prompt_tokens},
    }
