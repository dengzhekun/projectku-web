from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Any

import httpx

from app.config import Settings


PRODUCT_QUERY_TERMS = (
    "商品",
    "产品",
    "价格",
    "多少钱",
    "库存",
    "有货",
    "规格",
    "型号",
    "sku",
    "SKU",
    "手机",
    "电脑",
    "笔记本",
    "耳机",
    "家电",
    "空调",
    "冰箱",
    "洗衣机",
    "电视",
    "平板",
    "苹果15",
    "苹果手机",
    "iPhone",
    "iphone",
    "小米",
    "红米",
    "Redmi",
    "redmi",
)

BROAD_CATALOG_KEYWORDS = (
    "手机",
    "电脑",
    "笔记本",
    "耳机",
    "家电",
    "空调",
    "冰箱",
    "洗衣机",
    "电视",
    "平板",
)

NON_PRODUCT_SERVICE_TERMS = (
    "售后",
    "退货",
    "换货",
    "退款",
    "退回运费",
    "运费谁",
    "质量问题",
    "破损",
    "坏的",
    "少件",
    "物流",
    "快递",
    "签收",
    "优惠券",
    "余额",
    "钱包",
    "注册赠送",
    "注册送",
    "余额不足",
    "购物车",
    "加购",
    "锁库存",
)

QUERY_NOISE_TERMS = (
    "请问",
    "帮我",
    "看看",
    "查询",
    "一下",
    "现在",
    "当前",
    "多少钱",
    "价格",
    "库存",
    "有货吗",
    "还有",
    "有没有",
    "有哪些",
    "有什么",
    "推荐",
    "便宜一点",
    "便宜点",
    "多少",
    "商品",
    "产品",
    "规格",
    "型号",
    "的",
    "吗",
    "呢",
    "？",
    "?",
)


@dataclass(frozen=True)
class ProductQuery:
    raw: str
    keyword: str | None = None
    brand: str | None = None
    series: str | None = None
    variant: str | None = None
    storage: str | None = None


def normalize_variant(value: str | None) -> str | None:
    if not value:
        return None
    normalized = value.lower().replace(" ", "")
    if normalized in ("pro", "max", "plus", "ultra", "se"):
        return normalized
    if normalized == "promax":
        return "pro max"
    return normalized


def normalize_storage(value: str | None) -> str | None:
    if not value:
        return None
    normalized = value.upper().replace("GB", "G").replace("TB", "T")
    return normalized


def normalize_product_alias(keyword: str) -> str:
    normalized = keyword.strip()
    compact = re.sub(r"\s+", "", normalized)
    lower_compact = compact.lower()

    apple_model = re.match(r"^(?:苹果|iphone)(\d{1,2})(pro|max|plus)?$", lower_compact, re.IGNORECASE)
    if apple_model:
        model = apple_model.group(1)
        suffix = apple_model.group(2)
        parts = ["iPhone", model]
        if suffix:
            parts.append(suffix.capitalize())
        return " ".join(parts)

    apple_phone = re.match(r"^苹果手机$", compact)
    if apple_phone:
        return "iPhone"

    xiaomi_model = re.match(r"^小米\s*(\d{1,2})(pro|max|ultra)?$", normalized, re.IGNORECASE)
    if xiaomi_model:
        parts = ["Xiaomi", xiaomi_model.group(1)]
        if xiaomi_model.group(2):
            parts.append(xiaomi_model.group(2).capitalize())
        return " ".join(parts)

    redmi_model = re.match(r"^(?:红米|redmi)\s*([a-z]\d{1,2}|\d{1,3})(pro|max|ultra)?$", normalized, re.IGNORECASE)
    if redmi_model:
        parts = ["Redmi", redmi_model.group(1).upper()]
        if redmi_model.group(2):
            parts.append(redmi_model.group(2).capitalize())
        return " ".join(parts)

    return normalized


def parse_product_query(text: str) -> ProductQuery:
    keyword = extract_product_keyword(text) if text else None
    target = keyword or text.strip()
    compact = re.sub(r"\s+", "", target)
    lower = compact.lower()

    storage_match = re.search(r"(\d+\s*(?:g|gb|t|tb))", target, flags=re.IGNORECASE)
    storage = normalize_storage(storage_match.group(1)) if storage_match else None

    apple_model = re.search(r"(?:iphone|苹果)(\d{1,2})(promax|pro|max|plus|se)?", lower, flags=re.IGNORECASE)
    if apple_model:
        return ProductQuery(
            raw=text,
            keyword=keyword,
            brand="iphone",
            series=apple_model.group(1),
            variant=normalize_variant(apple_model.group(2)),
            storage=storage,
        )
    if "iphone" in lower or "苹果手机" in compact:
        return ProductQuery(raw=text, keyword=keyword, brand="iphone", storage=storage)

    xiaomi_model = re.search(r"(?:xiaomi|小米)(\d{1,2})(pro|max|ultra)?", lower, flags=re.IGNORECASE)
    if xiaomi_model:
        return ProductQuery(
            raw=text,
            keyword=keyword,
            brand="xiaomi",
            series=xiaomi_model.group(1),
            variant=normalize_variant(xiaomi_model.group(2)),
            storage=storage,
        )

    redmi_model = re.search(r"(?:redmi|红米)([a-z]\d{1,2}|\d{1,3})(pro|max|ultra)?", lower, flags=re.IGNORECASE)
    if redmi_model:
        return ProductQuery(
            raw=text,
            keyword=keyword,
            brand="redmi",
            series=redmi_model.group(1).upper(),
            variant=normalize_variant(redmi_model.group(2)),
            storage=storage,
        )

    huawei_model = re.search(r"(?:huawei|华为)\s*([a-z]+)?\s*(\d{1,2}|x\d)", target, flags=re.IGNORECASE)
    if huawei_model:
        series = " ".join(part for part in huawei_model.groups() if part)
        return ProductQuery(raw=text, keyword=keyword, brand="huawei", series=series.upper(), storage=storage)

    return ProductQuery(raw=text, keyword=keyword, storage=storage)


def is_product_query(message: str) -> bool:
    if any(term in message for term in NON_PRODUCT_SERVICE_TERMS):
        return False
    if not any(term in message for term in PRODUCT_QUERY_TERMS):
        return False

    keyword = extract_product_keyword(message)
    if keyword:
        return True

    return any(term in message for term in ("商品", "产品", "手机", "电脑", "笔记本", "耳机", "家电"))


def extract_product_keyword(message: str) -> str | None:
    keyword = message.strip()
    keyword = re.sub(r"[，。！？、,.!?;；:：()（）\[\]【】\"']", " ", keyword)
    for term in QUERY_NOISE_TERMS:
        keyword = keyword.replace(term, " ")
    keyword = re.sub(r"\s+", " ", keyword).strip()
    if not keyword:
        return None
    return normalize_product_alias(keyword)


def build_product_search_terms(message: str) -> list[str | None]:
    keyword = extract_product_keyword(message)
    terms: list[str | None] = []
    if keyword:
        terms.append(keyword)
        compact = re.sub(r"\s+", "", keyword)
        if compact != keyword:
            terms.append(compact)
    if should_search_broad_catalog(message, keyword):
        terms.append(None)
    seen = set()
    unique_terms: list[str | None] = []
    for term in terms:
        key = term or ""
        if key not in seen:
            seen.add(key)
            unique_terms.append(term)
    return unique_terms


def should_search_broad_catalog(message: str, keyword: str | None) -> bool:
    if not keyword:
        return True
    if keyword in BROAD_CATALOG_KEYWORDS:
        return True
    return any(term in message for term in ("有哪些", "有什么", "推荐"))


def product_name_matches(product: dict[str, Any], keyword: str | None) -> bool:
    if not keyword:
        return True
    name = str(product.get("name") or "").lower()
    normalized_keyword = keyword.lower()
    compact_name = re.sub(r"\s+", "", name)
    compact_keyword = re.sub(r"\s+", "", normalized_keyword)
    return normalized_keyword in name or compact_keyword in compact_name


def parse_product_name(product: dict[str, Any]) -> ProductQuery:
    return parse_product_query(str(product.get("name") or ""))


def is_compatible_product(query: ProductQuery, candidate: ProductQuery) -> bool:
    if query.brand and candidate.brand and query.brand != candidate.brand:
        return False
    if query.series and candidate.series and query.series.lower() != candidate.series.lower():
        return False
    if query.variant:
        return candidate.variant == query.variant
    if query.brand == "iphone" and query.series and candidate.variant in ("pro", "pro max", "plus", "max"):
        return False
    if query.storage and candidate.storage and query.storage != candidate.storage:
        return False
    return True


def score_product_candidate(query: ProductQuery, product: dict[str, Any]) -> int:
    candidate = parse_product_name(product)
    if not is_compatible_product(query, candidate):
        return -1

    score = 0
    if query.brand and candidate.brand == query.brand:
        score += 30
    if query.series and candidate.series and query.series.lower() == candidate.series.lower():
        score += 35
    if query.variant and candidate.variant == query.variant:
        score += 25
    elif not query.variant and not candidate.variant:
        score += 12
    if query.storage and candidate.storage == query.storage:
        score += 20
    if query.keyword and product_name_matches(product, query.keyword):
        score += 10
    if product.get("stock") is not None and product.get("stock") > 0:
        score += 2
    return score


def rank_product_candidates(query: ProductQuery, products: list[dict[str, Any]]) -> list[dict[str, Any]]:
    scored: list[tuple[int, dict[str, Any]]] = []
    for product in products:
        score = score_product_candidate(query, product)
        if score >= 0:
            scored.append((score, product))
    scored.sort(key=lambda item: (-item[0], str(item[1].get("name") or "")))
    return [product for _, product in scored]


class ProductToolClient:
    def __init__(self, settings: Settings):
        self.base_url = settings.backend_api_base_url.rstrip("/")
        self.timeout = max(settings.product_tool_timeout_seconds, 1)
        self.max_results = max(settings.product_tool_max_results, 1)

    def search(self, message: str) -> list[dict[str, Any]]:
        query = parse_product_query(message)
        for keyword in build_product_search_terms(message):
            products = rank_product_candidates(query, self._list_products(keyword))
            if products:
                return [self._detail_or_summary(product) for product in products[: self.max_results]]
        return []

    def _list_products(self, keyword: str | None) -> list[dict[str, Any]]:
        params: dict[str, Any] = {"page": 1, "size": self.max_results}
        if keyword:
            params["keyword"] = keyword
        with httpx.Client(timeout=self.timeout) as client:
            response = client.get(f"{self.base_url}/v1/products", params=params)
            response.raise_for_status()
        payload = response.json()
        data = payload.get("data", [])
        return data if isinstance(data, list) else []

    def _detail_or_summary(self, product: dict[str, Any]) -> dict[str, Any]:
        product_id = product.get("id")
        if product_id is None:
            return product
        try:
            with httpx.Client(timeout=self.timeout) as client:
                response = client.get(f"{self.base_url}/v1/products/{product_id}")
                response.raise_for_status()
            detail = response.json().get("data")
            if isinstance(detail, dict):
                return detail
        except httpx.HTTPError:
            return product
        return product


def format_product_business_facts(products: list[dict[str, Any]]) -> str:
    lines = [
        "以下为后端商品接口实时返回的数据；不得编造未返回的价格、库存或规格。",
    ]
    for index, product in enumerate(products, start=1):
        lines.append(f"{index}. {product.get('name') or '未命名商品'}")
        lines.append(f"   商品ID: {product.get('id')}")
        if product.get("price") is not None:
            lines.append(f"   当前售价: {product.get('price')}")
        if product.get("originalPrice") is not None:
            lines.append(f"   原价: {product.get('originalPrice')}")
        if product.get("stock") is not None:
            lines.append(f"   库存: {product.get('stock')}")
        if product.get("tags"):
            lines.append(f"   标签: {product.get('tags')}")
        if product.get("activityLabel"):
            lines.append(f"   活动: {product.get('activityLabel')}")
        if product.get("description"):
            lines.append(f"   描述: {product.get('description')}")
        skus = product.get("skus")
        if isinstance(skus, list) and skus:
            lines.append("   SKU:")
            for sku in skus:
                attrs = sku.get("attrs") if isinstance(sku, dict) else None
                lines.append(
                    "   - "
                    f"规格: {attrs or '默认'}; "
                    f"价格: {sku.get('price') if isinstance(sku, dict) else None}; "
                    f"库存: {sku.get('stock') if isinstance(sku, dict) else None}"
                )
    lines.append("回答商品价格或库存时，必须提醒：实际价格和库存以下单页为准。")
    return "\n".join(lines)
