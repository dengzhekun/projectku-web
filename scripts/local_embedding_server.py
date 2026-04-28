from __future__ import annotations

import os
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel


ROOT = Path(__file__).resolve().parent.parent
AI_ENV = ROOT / "deploy" / "ai-service.env"


def load_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


env_values = load_env_file(AI_ENV)
for key, value in env_values.items():
    os.environ.setdefault(key, value)

cache_dir = os.getenv("AI_EMBEDDING_CACHE_DIR", "./data/huggingface")
cache_path = Path(cache_dir)
if not cache_path.is_absolute():
    cache_path = ROOT / "ai-service" / cache_path

os.environ.setdefault("HF_ENDPOINT", os.getenv("AI_EMBEDDING_HF_ENDPOINT", "https://hf-mirror.com").rstrip("/"))
os.environ.setdefault("HF_HOME", str(cache_path))
os.environ.setdefault("HUGGINGFACE_HUB_CACHE", str(cache_path / "hub"))
os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")
os.environ.setdefault("TORCH_NUM_THREADS", "1")

try:
    import torch

    torch.set_num_threads(int(os.getenv("TORCH_NUM_THREADS", "1")))
except Exception:
    pass

from sentence_transformers import SentenceTransformer


API_KEY = os.getenv("AI_EMBEDDING_REMOTE_API_KEY", "")
MODEL_NAME = os.getenv("AI_EMBEDDING_MODEL", "BAAI/bge-m3")
BATCH_SIZE = int(os.getenv("AI_EMBEDDING_BATCH_SIZE", "4"))

app = FastAPI(title="local-embedding-server")
model = SentenceTransformer(MODEL_NAME, device=os.getenv("AI_EMBEDDING_DEVICE", "cpu"))


class EmbedRequest(BaseModel):
    texts: list[str]


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/embed")
def embed(request: EmbedRequest, x_api_key: str | None = Header(default=None)) -> dict:
    if API_KEY and x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="invalid api key")
    if not request.texts:
        raise HTTPException(status_code=400, detail="texts is required")

    vectors = model.encode(
        request.texts,
        normalize_embeddings=True,
        batch_size=BATCH_SIZE,
    )
    return {
        "model": MODEL_NAME,
        "dimension": len(vectors[0]) if len(vectors) else 0,
        "embeddings": vectors.tolist(),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=9001)
