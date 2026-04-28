import json

import httpx
import pytest

from app.clients.embedding_client import EmbeddingClient
from app.clients import embedding_client as embedding_client_module
from app.config import Settings


def test_remote_embedding_client_posts_text_with_api_key(monkeypatch):
    captured = {}
    original_client = httpx.Client
    monkeypatch.setattr(
        embedding_client_module,
        "get_embedding_model",
        lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("local model should not load")),
    )

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["api_key"] = request.headers.get("x-api-key")
        captured["payload"] = request.read().decode("utf-8")
        return httpx.Response(
            200,
            json={
                "model": "BAAI/bge-small-zh-v1.5",
                "dimension": 3,
                "embeddings": [[0.1, 0.2, 0.3]],
            },
        )

    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(httpx, "Client", lambda **kwargs: original_client(transport=transport, **kwargs))

    client = EmbeddingClient(
        Settings(
            ai_embedding_provider="remote_http",
            ai_embedding_remote_url="http://embedding.example.com/embed",
            ai_embedding_remote_api_key="secret-key",
        )
    )

    vector = client.embed("苹果15多少钱")

    assert vector == [0.1, 0.2, 0.3]
    assert captured["url"] == "http://embedding.example.com/embed"
    assert captured["api_key"] == "secret-key"
    assert json.loads(captured["payload"]) == {"texts": ["苹果15多少钱"]}


def test_remote_embedding_client_rejects_empty_response(monkeypatch):
    original_client = httpx.Client
    monkeypatch.setattr(
        embedding_client_module,
        "get_embedding_model",
        lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("local model should not load")),
    )
    transport = httpx.MockTransport(lambda request: httpx.Response(200, json={"embeddings": []}))
    monkeypatch.setattr(httpx, "Client", lambda **kwargs: original_client(transport=transport, **kwargs))

    client = EmbeddingClient(
        Settings(
            ai_embedding_provider="remote_http",
            ai_embedding_remote_url="http://embedding.example.com/embed",
            ai_embedding_remote_api_key="secret-key",
        )
    )

    with pytest.raises(RuntimeError, match="empty embedding"):
        client.embed("苹果15多少钱")
