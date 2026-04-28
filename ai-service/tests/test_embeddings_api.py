from types import SimpleNamespace

from fastapi.testclient import TestClient

from app.api import embeddings as embeddings_api
from app.main import app

client = TestClient(app)


def test_openai_embeddings_single_input(monkeypatch):
    monkeypatch.setattr(
        embeddings_api,
        "get_settings",
        lambda: SimpleNamespace(ai_embedding_gateway_api_key=""),
    )
    monkeypatch.setattr(
        embeddings_api,
        "get_embedding_client",
        lambda: SimpleNamespace(embed=lambda text: [float(len(text)), 0.5]),
    )

    response = client.post(
        "/v1/embeddings",
        json={"model": "bge-m3", "input": "hello"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["object"] == "list"
    assert body["model"] == "bge-m3"
    assert body["data"] == [
        {"object": "embedding", "index": 0, "embedding": [5.0, 0.5]},
    ]
    assert body["usage"] == {"prompt_tokens": 5, "total_tokens": 5}


def test_openai_embeddings_batch_input(monkeypatch):
    monkeypatch.setattr(
        embeddings_api,
        "get_settings",
        lambda: SimpleNamespace(ai_embedding_gateway_api_key=""),
    )
    monkeypatch.setattr(
        embeddings_api,
        "get_embedding_client",
        lambda: SimpleNamespace(embed=lambda text: [float(len(text))]),
    )

    response = client.post(
        "/v1/embeddings",
        json={"model": "bge-m3", "input": ["a", "bb"]},
    )

    assert response.status_code == 200
    assert response.json()["data"] == [
        {"object": "embedding", "index": 0, "embedding": [1.0]},
        {"object": "embedding", "index": 1, "embedding": [2.0]},
    ]


def test_openai_embeddings_requires_bearer_when_configured(monkeypatch):
    monkeypatch.setattr(
        embeddings_api,
        "get_settings",
        lambda: SimpleNamespace(ai_embedding_gateway_api_key="internal-key"),
    )
    monkeypatch.setattr(
        embeddings_api,
        "get_embedding_client",
        lambda: SimpleNamespace(embed=lambda text: [0.1]),
    )

    response = client.post("/v1/embeddings", json={"model": "bge-m3", "input": "hello"})
    assert response.status_code == 401

    response = client.post(
        "/v1/embeddings",
        json={"model": "bge-m3", "input": "hello"},
        headers={"Authorization": "Bearer wrong"},
    )
    assert response.status_code == 401

    response = client.post(
        "/v1/embeddings",
        json={"model": "bge-m3", "input": "hello"},
        headers={"Authorization": "Bearer internal-key"},
    )
    assert response.status_code == 200


def test_openai_embeddings_rejects_empty_input(monkeypatch):
    monkeypatch.setattr(
        embeddings_api,
        "get_settings",
        lambda: SimpleNamespace(ai_embedding_gateway_api_key=""),
    )
    monkeypatch.setattr(
        embeddings_api,
        "get_embedding_client",
        lambda: SimpleNamespace(embed=lambda text: [0.1]),
    )

    response = client.post("/v1/embeddings", json={"model": "bge-m3", "input": ""})
    assert response.status_code == 400

    response = client.post("/v1/embeddings", json={"model": "bge-m3", "input": []})
    assert response.status_code == 400

    response = client.post("/v1/embeddings", json={"model": "bge-m3", "input": ["ok", ""]})
    assert response.status_code == 400


def test_openai_embeddings_invalid_input_type_returns_422(monkeypatch):
    monkeypatch.setattr(
        embeddings_api,
        "get_settings",
        lambda: SimpleNamespace(ai_embedding_gateway_api_key=""),
    )
    monkeypatch.setattr(
        embeddings_api,
        "get_embedding_client",
        lambda: SimpleNamespace(embed=lambda text: [0.1]),
    )

    response = client.post("/v1/embeddings", json={"model": "bge-m3", "input": {"a": 1}})
    assert response.status_code == 422
