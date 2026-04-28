from functools import lru_cache

import httpx

from app.config import Settings


@lru_cache(maxsize=1)
def get_embedding_model(model_name: str, device: str):
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(model_name, device=device)


class EmbeddingClient:
    def __init__(self, settings: Settings):
        self.provider = settings.ai_embedding_provider
        self.model_name = settings.ai_embedding_model
        self.device = settings.ai_embedding_device
        self.normalize = settings.ai_embedding_normalize
        self.remote_url = settings.ai_embedding_remote_url
        self.remote_api_key = settings.ai_embedding_remote_api_key
        self.remote_timeout_seconds = settings.ai_embedding_remote_timeout_seconds

    def embed(self, text: str) -> list[float]:
        if self.provider == "remote_http":
            return self._embed_remote(text)

        model = get_embedding_model(self.model_name, self.device)
        vector = model.encode(text, normalize_embeddings=self.normalize)
        return vector.tolist()

    def _embed_remote(self, text: str) -> list[float]:
        if not self.remote_url:
            raise RuntimeError("ai_embedding_remote_url is required for remote_http embeddings")

        headers = {}
        if self.remote_api_key:
            headers["x-api-key"] = self.remote_api_key

        with httpx.Client(timeout=self.remote_timeout_seconds) as client:
            response = client.post(self.remote_url, json={"texts": [text]}, headers=headers)
            response.raise_for_status()

        embeddings = response.json().get("embeddings") or []
        if not embeddings:
            raise RuntimeError("remote embedding service returned empty embedding")

        return [float(value) for value in embeddings[0]]
