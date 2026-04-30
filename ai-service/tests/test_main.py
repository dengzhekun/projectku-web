from app.main import health


def test_health_exposes_lightrag_runtime_shape(monkeypatch):
    monkeypatch.setattr(
        "app.main.get_settings",
        lambda: type(
            "Settings",
            (),
            {
                "ai_llm_provider": "xfyun_codingplan",
                "ai_embedding_provider": "remote_http",
                "knowledge_retriever": "lightrag",
                "lightrag_collection": "kb-light",
                "neo4j_password": "secret",
            },
        )(),
    )

    payload = health()

    assert payload == {
        "status": "ok",
        "llmProvider": "xfyun_codingplan",
        "embeddingProvider": "remote_http",
        "knowledgeRetriever": "lightrag",
        "indexTarget": "kb-light",
        "neo4jEnabled": True,
    }
