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


def test_query_data_posts_query_data_payload(monkeypatch):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["headers"] = dict(request.headers)
        captured["payload"] = json.loads(request.read().decode("utf-8"))
        return httpx.Response(200, json={"status": "success", "data": {"chunks": []}})

    _mock_http_client(monkeypatch, handler)
    client = LightRagClient(
        Settings(
            lightrag_base_url="http://127.0.0.1:9621/",
            lightrag_api_key="test-key",
            lightrag_api_key_header="X-API-Key",
            lightrag_query_mode="naive",
        )
    )

    result = client.query_data("refund policy", top_k=5, chunk_top_k=8, enable_rerank=False)

    assert result == {"status": "success", "data": {"chunks": []}}
    assert captured["url"] == "http://127.0.0.1:9621/query/data"
    assert captured["headers"]["x-api-key"] == "test-key"
    assert captured["payload"] == {
        "query": "refund policy",
        "mode": "naive",
        "top_k": 5,
        "chunk_top_k": 8,
        "enable_rerank": False,
    }


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


def test_delete_documents_uses_official_delete_endpoint(monkeypatch):
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["url"] = str(request.url)
        captured["payload"] = json.loads(request.read().decode("utf-8"))
        return httpx.Response(200, json={"status": "deleted"})

    _mock_http_client(monkeypatch, handler)
    client = LightRagClient(Settings(lightrag_base_url="http://127.0.0.1:9621/"))

    result = client.delete_documents(["doc-a", "doc-b"])

    assert result == {"status": "deleted"}
    assert captured["method"] == "DELETE"
    assert captured["url"] == "http://127.0.0.1:9621/documents/delete_document"
    assert captured["payload"] == {
        "doc_ids": ["doc-a", "doc-b"],
        "delete_file": False,
        "delete_llm_cache": False,
    }


def test_delete_documents_accepts_no_content_success(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(204)

    _mock_http_client(monkeypatch, handler)
    client = LightRagClient(Settings(lightrag_base_url="http://127.0.0.1:9621/"))

    result = client.delete_documents(["doc-a"])

    assert result == {}


@pytest.mark.parametrize(
    "handler,method,expected_message",
    [
        (
            lambda request: (_ for _ in ()).throw(httpx.ReadTimeout("timed out", request=request)),
            "query",
            "/query",
        ),
        (
            lambda request: (_ for _ in ()).throw(httpx.ReadTimeout("timed out", request=request)),
            "query_data",
            "/query/data",
        ),
        (
            lambda request: httpx.Response(500, json={"detail": "boom"}),
            "insert_texts",
            "/documents/texts",
        ),
        (
            lambda request: httpx.Response(500, json={"detail": "boom"}),
            "delete_documents",
            "/documents/delete_document",
        ),
    ],
)
def test_request_failures_raise_runtime_error(monkeypatch, handler, method, expected_message):
    _mock_http_client(monkeypatch, handler)
    client = LightRagClient(Settings(lightrag_base_url="http://127.0.0.1:9621"))

    with pytest.raises(RuntimeError, match=expected_message):
        if method == "query":
            client.query("hello")
        elif method == "query_data":
            client.query_data("hello")
        elif method == "delete_documents":
            client.delete_documents(["doc-1"])
        else:
            client.insert_texts(["hello"])
