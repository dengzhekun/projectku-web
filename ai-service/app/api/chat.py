from functools import lru_cache
import json
import re

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from app.clients.business_tool_client import (
    BusinessToolClient,
    extract_amount,
    extract_coupon_code,
    format_aftersales_business_facts,
    format_coupons_business_facts,
    format_orders_business_facts,
    format_wallet_business_facts,
)
from app.clients.embedding_client import EmbeddingClient
from app.clients.llm_client import LlmClient
from app.clients.product_tool_client import (
    ProductToolClient,
    extract_product_keyword,
    format_product_business_facts,
    is_product_query,
)
from app.config import get_settings
from app.ingest.seed_documents import build_seed_records
from app.prompts.customer_service_prompt import build_customer_service_prompt
from app.retrieval.chroma_retriever import ChromaRetriever, merge_context
from app.retrieval.knowledge_retriever import KnowledgeRetriever, build_knowledge_retriever
from app.retrieval.neo4j_retriever import Neo4jRetriever, format_graph_context
from app.safety.guardrails import strip_think_tags
from app.schemas import ChatRequest, ChatResponse, Citation, HitLog

router = APIRouter()


@lru_cache(maxsize=1)
def get_embedding_client() -> EmbeddingClient:
    return EmbeddingClient(get_settings())


@lru_cache(maxsize=1)
def get_chroma_retriever() -> ChromaRetriever:
    retriever = ChromaRetriever(get_settings(), get_embedding_client())
    if retriever.collection.count() == 0:
        retriever.upsert(build_seed_records())
    return retriever


@lru_cache(maxsize=1)
def get_knowledge_retriever() -> KnowledgeRetriever:
    retriever = build_knowledge_retriever(get_settings(), get_embedding_client())
    collection = getattr(retriever, "collection", None)
    if collection is not None and hasattr(collection, "count") and collection.count() == 0:
        retriever.upsert(build_seed_records())
    return retriever


@lru_cache(maxsize=1)
def get_neo4j_retriever() -> Neo4jRetriever:
    return Neo4jRetriever(get_settings())


@lru_cache(maxsize=1)
def get_llm_client() -> LlmClient:
    return LlmClient(get_settings())


@lru_cache(maxsize=1)
def get_product_tool_client() -> ProductToolClient:
    return ProductToolClient(get_settings())


@lru_cache(maxsize=1)
def get_business_tool_client() -> BusinessToolClient:
    return BusinessToolClient(get_settings())


def build_product_citations(products: list[dict]) -> list[Citation]:
    citations: list[Citation] = []
    for product in products[:3]:
        product_id = product.get("id")
        if product_id is None:
            continue
        citations.append(
            Citation(
                sourceType="product",
                sourceId=str(product_id),
                title=str(product.get("name") or product_id),
            )
        )
    return citations


def _safe_int(value) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def build_hit_logs(chunks: list[dict]) -> list[HitLog]:
    hit_logs: list[HitLog] = []
    for chunk in chunks:
        metadata = chunk.get("metadata") or {}
        document_id = _safe_int(metadata.get("document_id"))
        chunk_id = _safe_int(metadata.get("chunk_id"))
        if document_id is None or chunk_id is None:
            continue
        hit_logs.append(HitLog(documentId=document_id, chunkId=chunk_id))
    return hit_logs


def build_citations(chunks: list[dict]) -> list[Citation]:
    citations: list[Citation] = []
    for chunk in chunks[:3]:
        metadata = chunk.get("metadata") or {}
        source_type = metadata.get("source_type")
        source_id = metadata.get("source_id")
        if source_type is None or source_id is None:
            continue
        citations.append(
            Citation(
                sourceType=str(source_type),
                sourceId=str(source_id),
                title=metadata.get("title") or str(source_id),
            )
        )
    return citations


def filter_latest_kb_versions(chunks: list[dict]) -> list[dict]:
    latest_by_document: dict[int, int] = {}
    for chunk in chunks:
        metadata = chunk.get("metadata") or {}
        document_id = _safe_int(metadata.get("document_id"))
        version = _safe_int(metadata.get("version"))
        if document_id is None or version is None:
            continue
        latest_by_document[document_id] = max(latest_by_document.get(document_id, version), version)

    if not latest_by_document:
        return chunks

    filtered: list[dict] = []
    for chunk in chunks:
        metadata = chunk.get("metadata") or {}
        document_id = _safe_int(metadata.get("document_id"))
        version = _safe_int(metadata.get("version"))
        if document_id is None or version is None:
            filtered.append(chunk)
            continue
        if version == latest_by_document.get(document_id):
            filtered.append(chunk)
    return filtered


def is_lightrag_answer_level_result(chunks: list[dict]) -> bool:
    return any(
        (chunk.get("metadata") or {}).get("source_type") == "lightrag"
        and (chunk.get("metadata") or {}).get("source_id") == "answer"
        for chunk in chunks
    )


def build_attribution_chunks(message: str, chunks: list[dict]) -> list[dict]:
    if not chunks or build_hit_logs(chunks) or not is_lightrag_answer_level_result(chunks):
        return chunks

    try:
        traced_chunks = get_chroma_retriever().query(message, top_k=3)
    except Exception:
        return chunks

    return traced_chunks if build_hit_logs(traced_chunks) else chunks


def _contains_any(text: str, terms: tuple[str, ...]) -> bool:
    return any(term in text for term in terms)


def infer_policy_category(message: str) -> str | None:
    lowered = message.lower()
    if _contains_any(message, ("坏", "破损", "少件", "售后", "退货", "换货", "质量问题", "运费谁", "退回运费")):
        return "after_sales"
    if _contains_any(message, ("优惠券", "券", "满减", "折扣", "门槛", "叠加")) or re.search(r"(?<![A-Za-z0-9])[A-Z]{2,}\d{2,}(?![A-Za-z0-9])", message):
        return "coupon"
    if _contains_any(message, ("物流", "快递", "配送", "签收", "派送", "没收到")):
        return "logistics"
    if _contains_any(message, ("退款", "到账", "原路退回", "支付", "余额")):
        return "payment_refund"
    if _contains_any(lowered, ("iphone", "macbook")) or _contains_any(message, ("选购", "推荐", "参数", "配置", "购物车", "加购", "锁库存")):
        return "shopping_guide"
    return None


def has_account_owner_signal(message: str) -> bool:
    return _contains_any(message, ("我的", "我有", "我还", "我现在", "账号", "账户", "钱包", "余额还有"))


def is_wallet_state_query(message: str) -> bool:
    if "余额" not in message and "钱包" not in message:
        return False
    if _contains_any(message, ("注册送", "注册赠送", "注册送", "余额不足")) and not has_account_owner_signal(message):
        return False
    return has_account_owner_signal(message) or bool(re.search(r"(余额|钱包).*(多少|几|剩|还有)", message))


def is_order_state_query(message: str) -> bool:
    lowered = message.lower()
    if "order" in lowered and _contains_any(message, ("我的", "状态", "到哪", "发货", "物流")):
        return True
    return _contains_any(message, ("我的订单", "订单状态", "订单到哪", "订单发货", "发货了吗", "订单号"))


def is_aftersales_state_query(message: str) -> bool:
    return _contains_any(message, ("我的售后", "售后状态", "售后进度", "退款进度"))


def classify_customer_service_route(message: str) -> str:
    if is_wallet_state_query(message):
        return "wallet"
    if is_order_state_query(message):
        return "order"
    if is_aftersales_state_query(message):
        return "aftersales"
    if is_product_query(message):
        return "product"
    return infer_policy_category(message) or "knowledge"


def score_chunk_for_message(message: str, chunk: dict) -> int:
    metadata = chunk.get("metadata") or {}
    document = str(chunk.get("document") or "")
    category = metadata.get("category")
    desired_category = infer_policy_category(message)
    score = 0
    if desired_category and category == desired_category:
        score += 100
    for term in ("质量问题", "退回运费", "平台承担", "破损", "少件", "功能异常"):
        if term in message and term in document:
            score += 20
        elif term in document and desired_category == "after_sales":
            score += 10
    if document.lstrip().startswith("#"):
        score -= 5
    if len(document.strip()) < 30:
        score -= 8
    return score


def rerank_chunks_for_message(message: str, chunks: list[dict], limit: int = 6) -> list[dict]:
    chunks = filter_latest_kb_versions(chunks)
    if infer_policy_category(message) is None:
        return chunks[:limit]
    indexed = list(enumerate(chunks))
    indexed.sort(key=lambda item: (-score_chunk_for_message(message, item[1]), item[0]))
    return [chunk for _, chunk in indexed[:limit]]


def with_product_disclaimer(answer: str) -> str:
    disclaimer = "实际价格和库存以下单页为准。"
    stripped = answer.strip()
    if disclaimer in stripped:
        return stripped
    if "实际价格" in stripped and "库存" in stripped and ("下单页" in stripped or "下单页面" in stripped):
        return stripped
    return f"{stripped}\n{disclaimer}" if stripped else disclaimer


def has_specific_sku_hint(message: str) -> bool:
    return bool(re.search(r"\d+\s*(?:g|gb|tb)", message, flags=re.IGNORECASE))


def is_product_list_query(message: str) -> bool:
    return any(term in message for term in ("有哪些", "有什么", "推荐"))


def is_product_price_or_stock_query(message: str) -> bool:
    return any(term in message for term in ("多少钱", "价格", "库存", "有货"))


def should_clarify_product_variant(message: str, products: list[dict]) -> bool:
    return (
        len(products) > 1
        and is_product_price_or_stock_query(message)
        and not is_product_list_query(message)
        and not has_specific_sku_hint(message)
    )


def format_product_price_stock(product: dict) -> str:
    parts: list[str] = []
    if product.get("price") is not None:
        parts.append(f"当前售价 {product.get('price')} 元")
    if product.get("stock") is not None:
        parts.append(f"库存 {product.get('stock')}")
    return "，".join(parts) if parts else "已查到商品信息"


def build_variant_clarification_answer(products: list[dict]) -> str:
    first = products[0]
    first_name = first.get("name") or "这个商品"
    lines = [
        f"有的，我先查到 {first_name}。不确定你要的是不是这个内存/规格：{format_product_price_stock(first)}。",
    ]
    if len(products) > 1:
        lines.append("还查到这些相近版本：")
        for product in products[1:4]:
            name = product.get("name") or "未命名商品"
            lines.append(f"- {name}：{format_product_price_stock(product)}")
    lines.append("如果你要查准确价格，请告诉我具体内存或规格，比如 128G / 256G。")
    return with_product_disclaimer("\n".join(lines))


def should_answer_plain_apple_no_match(message: str) -> bool:
    return extract_product_keyword(message) == "苹果" and is_product_price_or_stock_query(message)


def plain_apple_no_match_response(message: str) -> ChatResponse:
    return ChatResponse(
        answer=with_product_disclaimer(
            "「苹果」这个词太宽，我会先按水果类宽词处理，不会直接按手机或配件来查。"
            "如果你问的是水果苹果，请补充具体商品名或规格；如果你想问手机，请输入具体型号，比如 苹果15 / iPhone 15。"
        ),
        confidence=0.62,
        route="product",
        sourceType="product",
        citations=[],
        actions=[],
        hitLogs=[],
        fallbackReason="No realtime product match for plain apple price query.",
    )


def login_required_response(route: str) -> ChatResponse:
    return ChatResponse(
        answer="这个问题需要查询你的账号实时数据。请先登录后再问我，我就可以帮你查订单、余额、优惠券或售后进度。",
        confidence=0.7,
        route=route,
        sourceType="business",
        citations=[],
        actions=[],
        hitLogs=[],
        fallbackReason="Login is required for account-specific business data.",
    )


def business_tool_unavailable_response(route: str) -> ChatResponse:
    return ChatResponse(
        answer="我暂时无法连接实时业务接口，不能确认你的账号数据。你可以稍后再试，或到对应页面查看最新状态。",
        confidence=0.45,
        route=route,
        sourceType="business",
        citations=[],
        actions=[],
        hitLogs=[],
        fallbackReason=f"Business tool query failed for route: {route}.",
    )


def build_business_prompt_response(message: str, facts: str, route: str, citations: list[Citation]) -> ChatResponse:
    settings = get_settings()
    if not settings.ai_llm_api_key or settings.ai_llm_api_key.startswith("replace_"):
        return ChatResponse(
            answer=f"已查询到实时业务数据：\n{facts}",
            confidence=0.78,
            route=route,
            sourceType="business",
            citations=citations,
            actions=[],
            hitLogs=[],
            fallbackReason="Business data was found, but the LLM is not configured.",
        )

    prompt = build_customer_service_prompt(
        message=message,
        retrieved_context="",
        graph_context="",
        business_facts=facts,
    )
    answer = strip_think_tags(get_llm_client().chat(prompt))
    return ChatResponse(
        answer=answer or "我查到了实时业务数据，但暂时无法生成回答。请到对应页面查看详情。",
        confidence=0.9,
        route=route,
        sourceType="business",
        citations=citations,
        actions=[],
        hitLogs=[],
        fallbackReason=None,
    )


def build_business_stream_response(message: str, facts: str, route: str, citations: list[Citation]):
    settings = get_settings()
    if not settings.ai_llm_api_key or settings.ai_llm_api_key.startswith("replace_"):
        answer = f"已查询到实时业务数据：\n{facts}"
        yield {"type": "delta", "text": answer}
        yield {
            "type": "final",
            "reply": ChatResponse(
                answer=answer,
                confidence=0.78,
                route=route,
                sourceType="business",
                citations=citations,
                actions=[],
                hitLogs=[],
                fallbackReason="Business data was found, but the LLM is not configured.",
            ).model_dump(),
        }
        return

    prompt = build_customer_service_prompt(
        message=message,
        retrieved_context="",
        graph_context="",
        business_facts=facts,
    )
    answer_parts: list[str] = []
    yield {"type": "status", "message": "generating"}
    for text in get_llm_client().stream_chat(prompt):
        answer_parts.append(text)
        yield {"type": "delta", "text": text}
    answer = strip_think_tags("".join(answer_parts)) or "我查到了实时业务数据，但暂时无法生成回答。请到对应页面查看详情。"
    yield {
        "type": "final",
        "reply": ChatResponse(
            answer=answer,
            confidence=0.9,
            route=route,
            sourceType="business",
            citations=citations,
            actions=[],
            hitLogs=[],
            fallbackReason=None,
        ).model_dump(),
    }


def build_business_tool_context(request: ChatRequest, route: str) -> tuple[str, list[Citation]] | None:
    auth_token = request.authToken
    message = request.message.strip()
    if route not in ("wallet", "order", "coupon", "aftersales"):
        return None
    if route in ("wallet", "order", "aftersales") and not auth_token:
        raise PermissionError("login required")
    if route == "coupon" and not auth_token:
        if has_account_owner_signal(message) or extract_coupon_code(message):
            raise PermissionError("login required")
        return None

    client = get_business_tool_client()
    if route == "wallet":
        wallet = client.get_wallet(auth_token)
        return (
            format_wallet_business_facts(wallet),
            [Citation(sourceType="business", sourceId="wallet", title="钱包实时数据")],
        )

    if route == "order":
        if request.orderId is not None:
            order = client.get_order(auth_token, request.orderId)
            orders = [order] if order else []
        else:
            orders = client.list_orders(auth_token)
        return (
            format_orders_business_facts(orders),
            [Citation(sourceType="business", sourceId="orders", title="订单实时数据")],
        )

    if route == "coupon":
        code = extract_coupon_code(message)
        if code:
            result = client.check_coupon(auth_token, code, extract_amount(message))
            facts = format_coupons_business_facts(result, code)
            source_id = f"coupon:{code}"
        else:
            coupons = client.list_coupons(auth_token)
            facts = format_coupons_business_facts(coupons)
            source_id = "coupons"
        return (
            facts,
            [Citation(sourceType="business", sourceId=source_id, title="优惠券实时数据")],
        )

    aftersales = client.list_aftersales(auth_token)
    return (
        format_aftersales_business_facts(aftersales),
        [Citation(sourceType="business", sourceId="aftersales", title="售后实时数据")],
    )


def handle_chat(request: ChatRequest) -> ChatResponse:
    settings = get_settings()
    message = request.message.strip()
    if len(message) > settings.ai_cs_max_message_length:
        raise HTTPException(status_code=400, detail="message is too long")

    product_results: list[dict] = []
    route = classify_customer_service_route(message)
    try:
        business_context = build_business_tool_context(request, route)
    except PermissionError:
        return login_required_response(route)
    except Exception:
        return business_tool_unavailable_response(route)
    if business_context:
        facts, citations = business_context
        return build_business_prompt_response(message, facts, route, citations)

    if route == "product" and should_answer_plain_apple_no_match(message):
        return plain_apple_no_match_response(message)

    if route == "product":
        try:
            product_results = get_product_tool_client().search(message)
        except Exception:
            product_results = []

    if product_results:
        citations = build_product_citations(product_results)
        if should_clarify_product_variant(message, product_results):
            return ChatResponse(
                answer=build_variant_clarification_answer(product_results),
                confidence=0.72,
                route=route,
                sourceType="product",
                citations=citations,
                actions=[],
                hitLogs=[],
                fallbackReason=None,
            )

        prompt = build_customer_service_prompt(
            message=message,
            retrieved_context="",
            graph_context="",
            business_facts=format_product_business_facts(product_results),
        )
        if not settings.ai_llm_api_key or settings.ai_llm_api_key.startswith("replace_"):
            return ChatResponse(
                answer=with_product_disclaimer("已查询到商品实时数据，但 AI customer service is not configured. Set AI_LLM_API_KEY first."),
                confidence=0.75,
                route=route,
                sourceType="product",
                citations=citations,
                actions=[],
                hitLogs=[],
                fallbackReason="Product data was found, but the LLM is not configured.",
            )

        answer = strip_think_tags(get_llm_client().chat(prompt))
        return ChatResponse(
            answer=with_product_disclaimer(answer),
            confidence=0.9,
            route=route,
            sourceType="product",
            citations=citations,
            actions=[],
            hitLogs=[],
            fallbackReason=None,
        )

    chunks = rerank_chunks_for_message(message, get_knowledge_retriever().query(message, top_k=20), limit=6)
    graph_rows = get_neo4j_retriever().lookup_product_policy(message)

    retrieved_context = merge_context(chunks)
    graph_context = format_graph_context(graph_rows)
    prompt = build_customer_service_prompt(
        message=message,
        retrieved_context=retrieved_context,
        graph_context=graph_context,
        business_facts="No live business facts available",
    )

    fallback_reason = None if chunks else "No matching knowledge was found. The answer uses generic rules only."
    attribution_chunks = build_attribution_chunks(message, chunks)
    citations = build_citations(attribution_chunks)
    hit_logs = build_hit_logs(attribution_chunks)

    if not settings.ai_llm_api_key or settings.ai_llm_api_key.startswith("replace_"):
        return ChatResponse(
            answer="AI customer service is not configured. Set AI_LLM_API_KEY first.",
            confidence=0.2,
            route=route,
            sourceType="knowledge",
            citations=citations,
            actions=[],
            hitLogs=hit_logs,
            fallbackReason="This response is a local configuration reminder, not an LLM answer.",
        )

    answer = strip_think_tags(get_llm_client().chat(prompt))
    return ChatResponse(
        answer=answer or "Unable to confirm the answer right now. Please try again later.",
        confidence=0.86 if chunks else 0.56,
        route=route,
        sourceType="knowledge",
        citations=citations,
        actions=[],
        hitLogs=hit_logs,
        fallbackReason=fallback_reason,
    )


def sse_event(event: dict) -> str:
    return f"data: {json.dumps(event, ensure_ascii=False, separators=(',', ':'))}\n\n"


def handle_chat_stream(request: ChatRequest):
    settings = get_settings()
    message = request.message.strip()
    if len(message) > settings.ai_cs_max_message_length:
        raise HTTPException(status_code=400, detail="message is too long")

    yield {"type": "status", "message": "retrieving"}

    product_results: list[dict] = []
    route = classify_customer_service_route(message)
    try:
        business_context = build_business_tool_context(request, route)
    except PermissionError:
        response = login_required_response(route)
        yield {"type": "delta", "text": response.answer}
        yield {"type": "final", "reply": response.model_dump()}
        return
    except Exception:
        response = business_tool_unavailable_response(route)
        yield {"type": "delta", "text": response.answer}
        yield {"type": "final", "reply": response.model_dump()}
        return
    if business_context:
        facts, citations = business_context
        yield from build_business_stream_response(message, facts, route, citations)
        return

    if route == "product" and should_answer_plain_apple_no_match(message):
        response = plain_apple_no_match_response(message)
        yield {"type": "delta", "text": response.answer}
        yield {"type": "final", "reply": response.model_dump()}
        return

    if route == "product":
        try:
            product_results = get_product_tool_client().search(message)
        except Exception:
            product_results = []

    if product_results:
        citations = build_product_citations(product_results)
        if should_clarify_product_variant(message, product_results):
            answer = build_variant_clarification_answer(product_results)
            yield {"type": "delta", "text": answer}
            yield {
                "type": "final",
                "reply": ChatResponse(
                    answer=answer,
                    confidence=0.72,
                    route=route,
                    sourceType="product",
                    citations=citations,
                    actions=[],
                    hitLogs=[],
                    fallbackReason=None,
                ).model_dump(),
            }
            return

        prompt = build_customer_service_prompt(
            message=message,
            retrieved_context="",
            graph_context="",
            business_facts=format_product_business_facts(product_results),
        )
        if not settings.ai_llm_api_key or settings.ai_llm_api_key.startswith("replace_"):
            answer = with_product_disclaimer("已查询到商品实时数据，但 AI customer service is not configured. Set AI_LLM_API_KEY first.")
            yield {"type": "delta", "text": answer}
            yield {
                "type": "final",
                "reply": ChatResponse(
                    answer=answer,
                    confidence=0.75,
                    route=route,
                    sourceType="product",
                    citations=citations,
                    actions=[],
                    hitLogs=[],
                    fallbackReason="Product data was found, but the LLM is not configured.",
                ).model_dump(),
            }
            return

        answer_parts: list[str] = []
        yield {"type": "status", "message": "generating"}
        for text in get_llm_client().stream_chat(prompt):
            answer_parts.append(text)
            yield {"type": "delta", "text": text}
        answer = with_product_disclaimer(strip_think_tags("".join(answer_parts)))
        yield {
            "type": "final",
            "reply": ChatResponse(
                answer=answer,
                confidence=0.9,
                route=route,
                sourceType="product",
                citations=citations,
                actions=[],
                hitLogs=[],
                fallbackReason=None,
            ).model_dump(),
        }
        return

    chunks = rerank_chunks_for_message(message, get_knowledge_retriever().query(message, top_k=20), limit=6)
    graph_rows = get_neo4j_retriever().lookup_product_policy(message)

    retrieved_context = merge_context(chunks)
    graph_context = format_graph_context(graph_rows)
    prompt = build_customer_service_prompt(
        message=message,
        retrieved_context=retrieved_context,
        graph_context=graph_context,
        business_facts="No live business facts available",
    )

    fallback_reason = None if chunks else "No matching knowledge was found. The answer uses generic rules only."
    attribution_chunks = build_attribution_chunks(message, chunks)
    citations = build_citations(attribution_chunks)
    hit_logs = build_hit_logs(attribution_chunks)

    if not settings.ai_llm_api_key or settings.ai_llm_api_key.startswith("replace_"):
        answer = "AI customer service is not configured. Set AI_LLM_API_KEY first."
        yield {"type": "delta", "text": answer}
        yield {
            "type": "final",
            "reply": ChatResponse(
                answer=answer,
                confidence=0.2,
                route=route,
                sourceType="knowledge",
                citations=citations,
                actions=[],
                hitLogs=hit_logs,
                fallbackReason="This response is a local configuration reminder, not an LLM answer.",
            ).model_dump(),
        }
        return

    answer_parts: list[str] = []
    yield {"type": "status", "message": "generating"}
    for text in get_llm_client().stream_chat(prompt):
        answer_parts.append(text)
        yield {"type": "delta", "text": text}
    answer = strip_think_tags("".join(answer_parts)) or "Unable to confirm the answer right now. Please try again later."
    yield {
        "type": "final",
        "reply": ChatResponse(
            answer=answer,
            confidence=0.86 if chunks else 0.56,
            route=route,
            sourceType="knowledge",
            citations=citations,
            actions=[],
            hitLogs=hit_logs,
            fallbackReason=fallback_reason,
        ).model_dump(),
    }


@router.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    if not request.message or not request.message.strip():
        raise HTTPException(status_code=400, detail="message is required")
    return handle_chat(request)


@router.post("/chat/stream")
def chat_stream(request: ChatRequest) -> StreamingResponse:
    if not request.message or not request.message.strip():
        raise HTTPException(status_code=400, detail="message is required")

    def stream():
        try:
            for event in handle_chat_stream(request):
                yield sse_event(event)
        except Exception as exc:
            yield sse_event({"type": "error", "message": str(exc) or "stream failed"})

    return StreamingResponse(stream(), media_type="text/event-stream")
