import json

import httpx
import pytest

from app.config import Settings
from app.clients.lightrag_client import LightRagClient


def _mock_http_client(monkeypatch, handler):
    original_client = httpx.Client
    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(httpx, "Client", lambda **kwargs: original_client(transport=transport, **kwargs))


def test_query_posts_query_and_mode(monkeypatch):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["headers"] = dict(request.headers)
        captured["payload"] = json.loads(request.read().decode("utf-8"))
        return httpx.Response(200, json={"answer": "ok"})

    _mock_http_client(monkeypatch, handler)
    client = LightRagClient(
        Settings(
            lightrag_base_url="http://127.0.0.1:9621/",
            lightrag_api_key="test-key",
            lightrag_api_key_header="X-API-Key",
            lightrag_query_mode="hybrid",
        )
    )

    result = client.query("what is lightrag?", top_k=4)

    assert result == {"answer": "ok"}
    assert captured["url"] == "http://127.0.0.1:9621/query"
    assert captured["headers"]["x-api-key"] == "test-key"
    assert captured["payload"] == {"query": "what is lightrag?", "mode": "hybrid", "top_k": 4}


def test_insert_texts_posts_texts_payload(monkeypatch):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["payload"] = json.loads(request.read().decode("utf-8"))
        return httpx.Response(200, json={"inserted": 2})

    _mock_http_client(monkeypatch, handler)
    client = LightRagClient(Settings(lightrag_base_url="http://127.0.0.1:9621/"))

    result = client.insert_texts(["doc 1", "doc 2"])

    assert result == {"inserted": 2}
    assert captured["url"] == "http://127.0.0.1:9621/documents/texts"
    assert captured["payload"] == {"texts": ["doc 1", "doc 2"]}


def test_track_status_uses_official_track_status_endpoint(monkeypatch):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["url"] = str(request.url)
        return httpx.Response(200, json={"status": "processed"})

    _mock_http_client(monkeypatch, handler)
    client = LightRagClient(Settings(lightrag_base_url="http://127.0.0.1:9621/"))

    result = client.track_status("track-1")

    assert result == {"status": "processed"}
    assert captured["method"] == "GET"
    assert captured["url"] == "http://127.0.0.1:9621/documents/track_status/track-1"


@pytest.mark.parametrize(
    "handler,method,expected_message",
    [
        (
            lambda request: (_ for _ in ()).throw(httpx.ReadTimeout("timed out", request=request)),
            "query",
            "/query",
        ),
        (
            lambda request: httpx.Response(500, json={"detail": "boom"}),
            "insert_texts",
            "/documents/texts",
        ),
    ],
)
def test_request_failures_raise_runtime_error(monkeypatch, handler, method, expected_message):
    _mock_http_client(monkeypatch, handler)
    client = LightRagClient(Settings(lightrag_base_url="http://127.0.0.1:9621"))

    with pytest.raises(RuntimeError, match=expected_message):
        if method == "query":
            client.query("hello")
        else:
            client.insert_texts(["hello"])
