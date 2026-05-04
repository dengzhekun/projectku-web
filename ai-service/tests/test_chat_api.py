from types import SimpleNamespace

from fastapi.testclient import TestClient

from app.api import chat as chat_api
from app.main import app
from app.schemas import ChatRequest

client = TestClient(app)


def test_classify_customer_service_route_separates_product_and_policy_questions():
    assert chat_api.classify_customer_service_route("苹果15多少钱？") == "product"
    assert chat_api.classify_customer_service_route("注册送多少钱余额？余额不足怎么办？") == "payment_refund"
    assert chat_api.classify_customer_service_route("我的余额是多少？") == "wallet"
    assert chat_api.classify_customer_service_route("我的订单到哪了？") == "order"
    assert chat_api.classify_customer_service_route("加入购物车会锁库存吗？") == "shopping_guide"
    assert chat_api.classify_customer_service_route("NEW300为什么不能用？") == "coupon"


def test_chat_returns_structured_response(monkeypatch):
    monkeypatch.setattr(
        "app.api.chat.handle_chat",
        lambda request: {
            "answer": "Please confirm the order status first.",
            "confidence": 0.91,
            "citations": [],
            "actions": [],
            "hitLogs": [{"documentId": 1, "chunkId": 11}],
            "fallbackReason": None,
        },
    )
    response = client.post("/chat", json={"message": "refund", "conversationId": "c1"})
    assert response.status_code == 200
    assert response.json()["answer"] == "Please confirm the order status first."
    assert response.json()["hitLogs"] == [{"documentId": 1, "chunkId": 11}]


def test_chat_rejects_blank_message():
    response = client.post("/chat", json={"message": "   "})
    assert response.status_code == 400


def test_chat_stream_returns_sse_delta_and_final_events(monkeypatch):
    def fake_stream(request):
        yield {"type": "status", "message": "retrieving"}
        yield {"type": "delta", "text": "Hello"}
        yield {
            "type": "final",
            "reply": {
                "answer": "Hello",
                "confidence": 0.9,
                "citations": [],
                "actions": [],
                "hitLogs": [],
                "fallbackReason": None,
            },
        }

    monkeypatch.setattr(chat_api, "handle_chat_stream", fake_stream)

    response = client.post("/chat/stream", json={"message": "refund", "conversationId": "c1"})

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    assert 'data: {"type":"status","message":"retrieving"}' in response.text
    assert 'data: {"type":"delta","text":"Hello"}' in response.text
    assert 'data: {"type":"final","reply":' in response.text


def test_handle_chat_builds_hit_logs_from_retrieved_chunks(monkeypatch):
    class DummyRetriever:
        def query(self, text: str, top_k: int = 6):
            return [
                {
                    "document": "Seven-day return is supported",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb-1-0",
                        "title": "Return policy",
                        "document_id": 1,
                        "chunk_id": 101,
                    },
                }
            ]

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def chat(self, prompt: str):
            return "Seven-day return is supported."

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "build_customer_service_prompt", lambda **kwargs: "prompt")
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="refund", conversationId="c-1"))

    assert response.answer == "Seven-day return is supported."
    assert [item.model_dump() for item in response.hitLogs] == [{"documentId": 1, "chunkId": 101}]
    assert response.citations[0].title == "Return policy"


def test_chat_endpoint_handles_lightrag_like_retriever_metadata_safely(monkeypatch):
    class DummyLightRAGRetriever:
        def query(self, text: str, top_k: int = 6):
            return [
                {
                    "document": "Corrupted metadata should not break chat",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "lr:policy:bad-doc",
                        "title": "Bad document id",
                        "document_id": "abc",
                        "chunk_id": "101",
                    },
                },
                {
                    "document": "Corrupted chunk id should be skipped",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "lr:policy:bad-chunk",
                        "title": "Bad chunk id",
                        "document_id": "12",
                        "chunk_id": "x",
                    },
                },
                {
                    "document": "Seven-day return is supported",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "lr:policy:return",
                        "title": "Return policy",
                        "document_id": "11",
                        "chunk_id": "301",
                    },
                },
                {
                    "document": "Warranty details vary by product",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "lr:policy:warranty",
                        "title": "Warranty policy",
                        "document_id": None,
                        "chunk_id": None,
                    },
                },
            ]

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def chat(self, prompt: str):
            return "Seven-day return is supported."

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "is_product_query", lambda _: False)
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyLightRAGRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "build_customer_service_prompt", lambda **kwargs: "prompt")
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = client.post("/chat", json={"message": "refund policy", "conversationId": "c-lr"})

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "Seven-day return is supported."
    assert [item["sourceId"] for item in body["citations"]] == [
        "lr:policy:bad-doc",
        "lr:policy:bad-chunk",
        "lr:policy:return",
    ]
    assert body["hitLogs"] == [{"documentId": 11, "chunkId": 301}]


def test_build_citations_skips_invalid_entries_before_top_3_cutoff():
    chunks = [
        {"metadata": {"source_type": "kb", "source_id": None, "title": "invalid-1"}},
        {"metadata": {"source_type": None, "source_id": "invalid-2", "title": "invalid-2"}},
        {"metadata": {"source_type": "kb", "source_id": "kb-1", "title": "valid-1"}},
        {"metadata": {"source_type": "kb", "source_id": "kb-2", "title": "valid-2"}},
        {"metadata": {"source_type": "kb", "source_id": "kb-3", "title": "valid-3"}},
        {"metadata": {"source_type": "kb", "source_id": "kb-4", "title": "valid-4"}},
    ]

    citations = chat_api.build_citations(chunks)

    assert [(item.sourceType, item.sourceId, item.title) for item in citations] == [
        ("kb", "kb-1", "valid-1"),
        ("kb", "kb-2", "valid-2"),
        ("kb", "kb-3", "valid-3"),
    ]


def test_build_citations_deduplicates_sources_before_limit():
    chunks = [
        {"metadata": {"source_type": "kb", "source_id": "kb-1", "title": "first"}},
        {"metadata": {"source_type": "kb", "source_id": "kb-1", "title": "duplicate"}},
        {"metadata": {"source_type": "kb", "source_id": "kb-2", "title": "second"}},
        {"metadata": {"source_type": "kb", "source_id": "kb-3", "title": "third"}},
        {"metadata": {"source_type": "kb", "source_id": "kb-4", "title": "fourth"}},
    ]

    citations = chat_api.build_citations(chunks)

    assert [(item.sourceType, item.sourceId, item.title) for item in citations] == [
        ("kb", "kb-1", "first"),
        ("kb", "kb-2", "second"),
        ("kb", "kb-3", "third"),
    ]


def test_classify_after_sales_state_query_uses_canonical_route():
    assert chat_api.classify_customer_service_route("我的售后进度怎么样") == "after_sales"


def test_handle_chat_degrades_safely_for_lightrag_answer_level_only_result(monkeypatch):
    captured = {}

    class DummyLightRAGRetriever:
        def query(self, text: str, top_k: int = 6):
            captured["knowledge_query"] = text
            return [
                {
                    "document": "LightRAG summarized answer: unopened items support seven-day returns.",
                    "metadata": {
                        "source_type": "lightrag",
                        "source_id": "answer",
                        "title": "LightRAG answer",
                    },
                }
            ]

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def chat(self, prompt: str):
            captured["prompt"] = prompt
            return "Unopened items support seven-day no-reason return."

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "is_product_query", lambda _: False)
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyLightRAGRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "build_customer_service_prompt", lambda **kwargs: kwargs["retrieved_context"])
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="return policy", conversationId="c-lr-answer"))

    assert captured["knowledge_query"] == "return policy"
    assert "LightRAG summarized answer" in captured["prompt"]
    assert [item.model_dump() for item in response.hitLogs] == []
    assert [(item.sourceType, item.sourceId, item.title) for item in response.citations] == [
        ("lightrag", "answer", "LightRAG answer")
    ]
    assert response.retrievalTrace is not None
    assert response.retrievalTrace.attributionStatus == "answer_level"
    assert response.retrievalTrace.returnedChunkCount == 1
    assert response.retrievalTrace.selectedChunkCount == 1
    assert response.retrievalTrace.hitLogCount == 0
    assert response.retrievalTrace.citationCount == 1
    assert response.retrievalTrace.selectedSourceIds == ["answer"]
    assert response.retrievalTrace.notes == [
        "LightRAG returned answer/source-level evidence without numeric document/chunk metadata; chunk hit logging is degraded."
    ]


def test_handle_chat_records_chunk_level_retrieval_trace_for_knowledge_hits(monkeypatch):
    class DummyRetriever:
        def query(self, text: str, top_k: int = 6):
            return [
                {
                    "document": "订单金额未达到优惠券最低门槛时，优惠券不能使用。",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb:6:3:12",
                        "title": "KB Coupon Rules",
                        "document_id": 6,
                        "chunk_id": 912,
                        "category": "coupon",
                        "version": 3,
                    },
                },
                {
                    "document": "物流信息长时间不更新时，可以先核对地址。",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb:8:3:3",
                        "title": "KB Logistics",
                        "document_id": 8,
                        "chunk_id": 933,
                        "category": "logistics",
                        "version": 3,
                    },
                },
            ]

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def chat(self, prompt: str):
            return "订单金额未达到优惠券最低门槛时，优惠券不能使用。"

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "is_product_query", lambda _: False)
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "build_customer_service_prompt", lambda **kwargs: kwargs["retrieved_context"])
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="优惠券没到门槛为什么不能用？"))

    assert response.hitLogs[0].documentId == 6
    assert response.retrievalTrace is not None
    assert response.retrievalTrace.route == "coupon"
    assert response.retrievalTrace.sourceType == "knowledge"
    assert response.retrievalTrace.retriever == "lightrag"
    assert response.retrievalTrace.requestedTopK == 20
    assert response.retrievalTrace.returnedChunkCount == 2
    assert response.retrievalTrace.selectedChunkCount == 1
    assert response.retrievalTrace.attributionStatus == "chunk_level"
    assert response.retrievalTrace.selectedCategories == ["coupon"]
    assert response.retrievalTrace.selectedSourceIds == ["kb:6:3:12"]
    assert response.retrievalTrace.hitLogCount == 1
    assert response.retrievalTrace.citationCount == 1
    assert response.retrievalTrace.fallbackReason is None


def test_handle_chat_returns_friendly_message_when_knowledge_retrieval_is_unavailable(monkeypatch):
    class FailingRetriever:
        def query(self, text: str, top_k: int = 6):
            raise RuntimeError("LightRAG request failed for /query: [WinError 10061] actively refused")

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "is_product_query", lambda _: False)
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: FailingRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())

    response = chat_api.handle_chat(ChatRequest(message="退货规则是什么？"))

    assert "知识库服务暂时不可用" in response.answer
    assert "10061" not in response.answer
    assert response.route == "after_sales"
    assert response.sourceType == "knowledge"
    assert response.citations == []
    assert response.hitLogs == []
    assert response.fallbackReason == "Knowledge retrieval is temporarily unavailable."


def test_handle_chat_filters_stale_kb_versions_for_same_document(monkeypatch):
    captured = {}

    class DummyRetriever:
        def query(self, text: str, top_k: int = 6):
            return [
                {
                    "document": "Old version says upload evidence within 48 hours.",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb:5:1:4",
                        "title": "After sales old",
                        "document_id": 5,
                        "chunk_id": 501,
                        "version": 1,
                    },
                },
                {
                    "document": "Current version says paid, shipped, and completed orders can apply.",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb:5:2:4",
                        "title": "After sales current",
                        "document_id": 5,
                        "chunk_id": 701,
                        "version": 2,
                    },
                },
            ]

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def chat(self, prompt: str):
            captured["prompt"] = prompt
            return "Paid, shipped, and completed orders can apply."

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "is_product_query", lambda _: False)
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "build_customer_service_prompt", lambda **kwargs: kwargs["retrieved_context"])
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="after sales policy", conversationId="c-version"))

    assert "48 hours" not in captured["prompt"]
    assert "Current version" in captured["prompt"]
    assert [item.model_dump() for item in response.hitLogs] == [{"documentId": 5, "chunkId": 701}]
    assert response.citations[0].sourceId == "kb:5:2:4"


def test_rerank_chunks_for_message_prefers_matching_policy_category():
    chunks = [
        {
            "document": "待支付订单和已取消订单不能申请售后。",
            "metadata": {
                "source_type": "kb",
                "source_id": "kb:5:3:9",
                "title": "KB After Sales Policy",
                "document_id": 5,
                "chunk_id": 842,
                "category": "after_sales",
                "version": 3,
            },
        },
        {
            "document": "订单金额没有达到最低消费金额时不能使用优惠券。",
            "metadata": {
                "source_type": "kb",
                "source_id": "kb:6:3:12",
                "title": "KB Coupon Rules",
                "document_id": 6,
                "chunk_id": 912,
                "category": "coupon",
                "version": 3,
            },
        },
    ]

    ranked = chat_api.rerank_chunks_for_message("没达到金额的优惠券可以先用吗？", chunks, limit=6)

    assert [item["metadata"]["source_id"] for item in ranked] == ["kb:6:3:12"]


def test_rerank_chunks_for_message_drops_cross_category_results_when_no_matching_policy_category():
    chunks = [
        {
            "document": "待支付订单和已取消订单不能申请售后。",
            "metadata": {
                "source_type": "kb",
                "source_id": "kb:5:3:9",
                "title": "KB After Sales Policy",
                "document_id": 5,
                "chunk_id": 842,
                "category": "after_sales",
                "version": 3,
            },
        }
    ]

    ranked = chat_api.rerank_chunks_for_message("没达到金额的优惠券可以先用吗？", chunks, limit=6)

    assert ranked == []


def test_rerank_chunks_for_message_keeps_generic_policy_when_no_exact_category():
    chunks = [
        {
            "document": "质量问题退货的退回运费由平台承担。",
            "metadata": {
                "source_type": "kb",
                "source_id": "kb:2:1:0",
                "title": "Return policy",
                "document_id": 2,
                "chunk_id": 2,
                "category": "policy",
                "version": 1,
            },
        }
    ]

    ranked = chat_api.rerank_chunks_for_message("售后质量问题退回运费谁承担？", chunks, limit=6)

    assert [item["metadata"]["source_id"] for item in ranked] == ["kb:2:1:0"]


def test_handle_chat_stream_skips_non_numeric_hit_log_ids(monkeypatch):
    class DummyLightRAGRetriever:
        def query(self, text: str, top_k: int = 6):
            return [
                {
                    "document": "Corrupted metadata should not break chat stream",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "lr:policy:bad",
                        "title": "Bad ids",
                        "document_id": "abc",
                        "chunk_id": "x",
                    },
                },
                {
                    "document": "Seven-day return is supported",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "lr:policy:return",
                        "title": "Return policy",
                        "document_id": "11",
                        "chunk_id": "301",
                    },
                },
            ]

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def stream_chat(self, prompt: str):
            yield "Seven-day return is supported."

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "is_product_query", lambda _: False)
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyLightRAGRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "build_customer_service_prompt", lambda **kwargs: "prompt")
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    events = list(chat_api.handle_chat_stream(ChatRequest(message="refund policy", conversationId="c-lr-stream")))
    final_event = next(event for event in events if event["type"] == "final")
    reply = final_event["reply"]

    assert [item["sourceId"] for item in reply["citations"]] == ["lr:policy:bad", "lr:policy:return"]
    assert reply["hitLogs"] == [{"documentId": 11, "chunkId": 301}]


def test_handle_chat_stream_returns_friendly_final_event_when_knowledge_retrieval_is_unavailable(monkeypatch):
    class FailingRetriever:
        def query(self, text: str, top_k: int = 6):
            raise RuntimeError("LightRAG request failed for /query: [WinError 10061] actively refused")

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "is_product_query", lambda _: False)
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: FailingRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())

    events = list(chat_api.handle_chat_stream(ChatRequest(message="退货规则是什么？", conversationId="c-kb-down")))
    final_event = next(event for event in events if event["type"] == "final")
    reply = final_event["reply"]

    assert reply["route"] == "after_sales"
    assert reply["sourceType"] == "knowledge"
    assert "知识库服务暂时不可用" in reply["answer"]
    assert "10061" not in reply["answer"]
    assert reply["fallbackReason"] == "Knowledge retrieval is temporarily unavailable."


def test_handle_chat_uses_realtime_product_tool_before_knowledge_retrieval(monkeypatch):
    captured = {}

    class DummyProductTool:
        def search(self, message: str):
            captured["product_query"] = message
            return [
                {
                    "id": 12,
                    "name": "AirPods Pro",
                    "price": 899,
                    "originalPrice": 999,
                    "stock": 18,
                    "description": "主动降噪耳机",
                    "skus": [{"attrs": {"颜色": "白色"}, "price": 899, "stock": 18}],
                }
            ]

    class FailingRetriever:
        def query(self, text: str, top_k: int = 6):
            raise AssertionError("Knowledge retriever should not be queried when product tool returns products")

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def chat(self, prompt: str):
            captured["prompt"] = prompt
            return "AirPods Pro 当前价格是 899 元，库存 18 件。"

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "get_product_tool_client", lambda: DummyProductTool())
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: FailingRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="AirPods Pro 多少钱，还有库存吗？"))

    assert captured["product_query"] == "AirPods Pro 多少钱，还有库存吗？"
    assert "AirPods Pro" in captured["prompt"]
    assert "899" in captured["prompt"]
    assert "18" in captured["prompt"]
    assert response.answer.endswith("实际价格和库存以下单页为准。")
    assert response.citations[0].sourceType == "product"
    assert response.citations[0].sourceId == "12"
    assert response.hitLogs == []


def test_product_disclaimer_is_not_duplicated_when_answer_already_mentions_order_page():
    answer = chat_api.with_product_disclaimer("实际价格和库存请以您下单页面的实时显示为准。")

    assert answer == "实际价格和库存请以您下单页面的实时显示为准。"


def test_handle_chat_falls_back_to_knowledge_retrieval_when_product_tool_has_no_match(monkeypatch):
    captured = {}

    class EmptyProductTool:
        def search(self, message: str):
            captured["product_query"] = message
            return []

    class DummyRetriever:
        def query(self, text: str, top_k: int = 6):
            captured["knowledge_query"] = text
            return [
                {
                    "document": "Seven-day return is supported",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb-1-0",
                        "title": "Return policy",
                        "document_id": 1,
                        "chunk_id": 101,
                    },
                }
            ]

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def chat(self, prompt: str):
            return "Seven-day return is supported."

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "get_product_tool_client", lambda: EmptyProductTool())
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "build_customer_service_prompt", lambda **kwargs: "prompt")
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="不存在的耳机多少钱？"))

    assert captured["product_query"] == "不存在的耳机多少钱？"
    assert captured["knowledge_query"] == "不存在的耳机多少钱？"
    assert response.answer == "Seven-day return is supported."
    assert [item.model_dump() for item in response.hitLogs] == [{"documentId": 1, "chunkId": 101}]


def test_handle_chat_returns_deterministic_no_match_for_plain_apple_price_query(monkeypatch):
    class FailingProductTool:
        def search(self, message: str):
            raise AssertionError("Plain apple queries are too broad and should ask for clarification first")

    class FailingRetriever:
        def query(self, text: str, top_k: int = 6):
            raise AssertionError("Price queries with no realtime product match should not fall back to LLM")

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "get_product_tool_client", lambda: FailingProductTool())
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: FailingRetriever())

    response = chat_api.handle_chat(ChatRequest(message="苹果多少钱？"))

    assert "「苹果」这个词太宽" in response.answer
    assert "品牌" not in response.answer
    assert "Apple" not in response.answer
    assert "水果苹果" in response.answer
    assert response.route == "product"
    assert response.sourceType == "product"
    assert response.confidence == 0.62
    assert response.citations == []


def test_handle_chat_uses_product_tool_for_apple_15_price_query(monkeypatch):
    captured = {}

    class DummyProductTool:
        def search(self, message: str):
            captured["product_query"] = message
            return [
                {
                    "id": 15,
                    "name": "iPhone 15 128G",
                    "price": 5199,
                    "stock": 11,
                }
            ]

    class DummyLlmClient:
        def chat(self, prompt: str):
            captured["prompt"] = prompt
            return "iPhone 15 128G 当前售价 5199 元，库存 11 件。"

    class FailingRetriever:
        def query(self, text: str, top_k: int = 6):
            raise AssertionError("Product route with realtime product hit should not query knowledge retriever")

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "get_product_tool_client", lambda: DummyProductTool())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: FailingRetriever())
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="苹果15多少钱？"))

    assert captured["product_query"] == "苹果15多少钱？"
    assert "iPhone 15 128G" in captured["prompt"]
    assert response.route == "product"
    assert response.sourceType == "product"
    assert response.citations[0].sourceId == "15"


def test_handle_chat_prioritizes_after_sales_chunks_for_after_sales_question(monkeypatch):
    captured = {}

    class DummyRetriever:
        def query(self, text: str, top_k: int = 6):
            captured["top_k"] = top_k
            return [
                {
                    "document": "1. 明确使用场景：家用、办公、学生、游戏、出差便携。",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb:9:1:2",
                        "title": "Shopping guide",
                        "document_id": 9,
                        "chunk_id": 687,
                        "category": "shopping_guide",
                    },
                },
                {
                    "document": "质量问题通过审核后，平台承担退回运费；具体方式以售后单审核结论为准。",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb:5:1:4",
                        "title": "After sales",
                        "document_id": 5,
                        "chunk_id": 653,
                        "category": "after_sales",
                    },
                },
            ]

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def chat(self, prompt: str):
            captured["prompt"] = prompt
            return "质量问题通过审核后，平台承担退回运费。"

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "is_product_query", lambda _: False)
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "build_customer_service_prompt", lambda **kwargs: kwargs["retrieved_context"])
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="我收到商品就是坏的，退货运费是谁承担？"))

    assert captured["top_k"] >= 12
    assert "平台承担退回运费" in captured["prompt"]
    assert "明确使用场景" not in captured["prompt"]
    assert response.answer == "质量问题通过审核后，平台承担退回运费。"
    assert response.hitLogs[0].documentId == 5


def test_handle_chat_uses_after_sales_shipping_chunk_for_quality_return_shipping_question(monkeypatch):
    captured = {}

    class DummyRetriever:
        def query(self, text: str, top_k: int = 6):
            return [
                {
                    "document": "优惠券不满足门槛时不可用。",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb:6:3:12",
                        "title": "Coupon Rules",
                        "document_id": 6,
                        "chunk_id": 912,
                        "category": "coupon",
                    },
                },
                {
                    "document": "如果审核确认为商品质量问题，退货运费通常由商家承担；非质量问题需要按售后规则和审核结果处理。",
                    "metadata": {
                        "source_type": "kb",
                        "source_id": "kb:5:3:22",
                        "title": "KB After Sales Policy",
                        "document_id": 5,
                        "chunk_id": 855,
                        "category": "after_sales",
                    },
                },
            ]

    class DummyNeo4jRetriever:
        def lookup_product_policy(self, text: str):
            return []

    class DummyLlmClient:
        def chat(self, prompt: str):
            captured["prompt"] = prompt
            return "质量问题通过审核后，退货运费通常由商家承担。"

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "is_product_query", lambda _: False)
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: DummyRetriever())
    monkeypatch.setattr(chat_api, "get_neo4j_retriever", lambda: DummyNeo4jRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "build_customer_service_prompt", lambda **kwargs: kwargs["retrieved_context"])
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="售后质量问题退回运费谁承担？"))

    assert "商品质量问题，退货运费通常由商家承担" in captured["prompt"]
    assert "优惠券不满足门槛" not in captured["prompt"]
    assert response.route == "after_sales"
    assert response.hitLogs[0].documentId == 5
    assert response.hitLogs[0].chunkId == 855


def test_handle_chat_clarifies_memory_when_product_query_matches_multiple_versions(monkeypatch):
    class AmbiguousProductTool:
        def search(self, message: str):
            return [
                {"id": 1, "name": "iPhone 15 Pro 128G", "price": 7999, "stock": 97},
                {"id": 2, "name": "iPhone 15 Pro 256G", "price": 8999, "stock": 42},
            ]

    class FailingLlmClient:
        def chat(self, prompt: str):
            raise AssertionError("Ambiguous product price answers should be deterministic")

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "get_product_tool_client", lambda: AmbiguousProductTool())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: FailingLlmClient())

    response = chat_api.handle_chat(ChatRequest(message="苹果15Pro多少钱？"))

    assert "iPhone 15 Pro 128G" in response.answer
    assert "7999" in response.answer
    assert "不确定你要的是不是这个内存" in response.answer
    assert "iPhone 15 Pro 256G" in response.answer
    assert "请告诉我具体内存或规格" in response.answer
    assert response.confidence == 0.72
    assert [item.sourceId for item in response.citations] == ["1", "2"]
    assert response.hitLogs == []


def test_rerank_chunks_for_coupon_threshold_question_prefers_coupon_category():
    chunks = [
        {
            "document": "物流异常时可在订单页点击催促派送。",
            "metadata": {
                "source_type": "kb",
                "source_id": "kb:8:3:3",
                "title": "KB Logistics",
                "document_id": 8,
                "chunk_id": 933,
                "category": "logistics",
            },
        },
        {
            "document": "订单金额未达到优惠券最低门槛时，优惠券不能使用。",
            "metadata": {
                "source_type": "kb",
                "source_id": "kb:6:3:12",
                "title": "KB Coupon Rules",
                "document_id": 6,
                "chunk_id": 912,
                "category": "coupon",
            },
        },
    ]

    ranked = chat_api.rerank_chunks_for_message("优惠券没到门槛为什么不能用？", chunks, limit=6)

    assert [item["metadata"]["source_id"] for item in ranked] == ["kb:6:3:12"]


def test_rerank_chunks_for_logistics_stuck_question_prefers_logistics_category():
    chunks = [
        {
            "document": "订单金额未达到优惠券最低门槛时，优惠券不能使用。",
            "metadata": {
                "source_type": "kb",
                "source_id": "kb:6:3:12",
                "title": "KB Coupon Rules",
                "document_id": 6,
                "chunk_id": 912,
                "category": "coupon",
            },
        },
        {
            "document": "物流信息长时间不更新时，可以先核对地址并联系在线客服催促派送。",
            "metadata": {
                "source_type": "kb",
                "source_id": "kb:8:3:3",
                "title": "KB Logistics",
                "document_id": 8,
                "chunk_id": 933,
                "category": "logistics",
            },
        },
    ]

    ranked = chat_api.rerank_chunks_for_message("物流一直不动怎么办？", chunks, limit=6)

    assert [item["metadata"]["source_id"] for item in ranked] == ["kb:8:3:3"]


def test_handle_chat_uses_wallet_tool_with_auth_before_knowledge(monkeypatch):
    captured = {}

    class DummyBusinessTool:
        def get_wallet(self, auth_token: str):
            captured["auth_token"] = auth_token
            return {
                "wallet": {"balance": "20000.00", "status": "active"},
                "transactions": [{"type": "REGISTER_BONUS", "amount": "20000.00", "remark": "注册送余额"}],
            }

    class FailingRetriever:
        def query(self, text: str, top_k: int = 6):
            raise AssertionError("Knowledge base should not be queried when wallet facts are available")

    class DummyLlmClient:
        def chat(self, prompt: str):
            captured["prompt"] = prompt
            return "您的钱包余额是 20000.00 元。"

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "get_business_tool_client", lambda: DummyBusinessTool())
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: FailingRetriever())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="我的余额是多少？", authToken="token-123"))

    assert captured["auth_token"] == "token-123"
    assert "钱包余额: 20000.00" in captured["prompt"]
    assert response.answer == "您的钱包余额是 20000.00 元。"
    assert response.confidence == 0.9
    assert response.route == "wallet"
    assert response.sourceType == "business"
    assert response.hitLogs == []


def test_handle_chat_requires_login_for_user_specific_wallet_question(monkeypatch):
    class FailingBusinessTool:
        def get_wallet(self, auth_token: str):
            raise AssertionError("Business tool should not be called without auth token")

    class FailingRetriever:
        def query(self, text: str, top_k: int = 6):
            raise AssertionError("Knowledge base should not answer user-specific wallet state")

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "get_business_tool_client", lambda: FailingBusinessTool())
    monkeypatch.setattr(chat_api, "get_knowledge_retriever", lambda: FailingRetriever())

    response = chat_api.handle_chat(ChatRequest(message="我的余额是多少？"))

    assert "登录" in response.answer
    assert response.confidence == 0.7
    assert response.route == "wallet"
    assert response.sourceType == "business"
    assert response.fallbackReason == "Login is required for account-specific business data."


def test_handle_chat_uses_order_tool_with_auth(monkeypatch):
    captured = {}

    class DummyBusinessTool:
        def list_orders(self, auth_token: str):
            captured["auth_token"] = auth_token
            return [
                {
                    "id": 101,
                    "orderNo": "ORD101",
                    "status": "PAID",
                    "payAmount": "899.00",
                    "createdAt": "2026-04-27 10:00:00",
                }
            ]

    class DummyLlmClient:
        def chat(self, prompt: str):
            captured["prompt"] = prompt
            return "我查到您最近的订单 ORD101，目前状态是 PAID。"

    monkeypatch.setattr(
        chat_api,
        "get_settings",
        lambda: SimpleNamespace(ai_cs_max_message_length=800, ai_llm_api_key="test-key"),
    )
    monkeypatch.setattr(chat_api, "get_business_tool_client", lambda: DummyBusinessTool())
    monkeypatch.setattr(chat_api, "get_llm_client", lambda: DummyLlmClient())
    monkeypatch.setattr(chat_api, "strip_think_tags", lambda text: text)

    response = chat_api.handle_chat(ChatRequest(message="我的订单到哪了？", authToken="token-123"))

    assert captured["auth_token"] == "token-123"
    assert "ORD101" in captured["prompt"]
    assert response.route == "order"
    assert response.sourceType == "business"
    assert response.answer == "我查到您最近的订单 ORD101，目前状态是 PAID。"
