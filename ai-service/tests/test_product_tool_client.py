from types import SimpleNamespace

from app.clients.product_tool_client import (
    ProductToolClient,
    build_product_search_terms,
    extract_product_keyword,
    is_product_query,
    normalize_product_alias,
    parse_product_query,
    rank_product_candidates,
)


def test_extract_product_keyword_removes_price_and_stock_question_noise():
    assert extract_product_keyword("AirPods Pro 多少钱，还有库存吗？") == "AirPods Pro"


def test_is_product_query_ignores_after_sales_question_that_mentions_product():
    assert not is_product_query("我收到商品就是坏的，退货运费是谁承担？")


def test_is_product_query_accepts_specific_product_price_question():
    assert is_product_query("苹果15Pro多少钱？")


def test_is_product_query_ignores_wallet_balance_question():
    assert not is_product_query("注册送多少钱余额？余额不足怎么办？")


def test_is_product_query_ignores_cart_stock_lock_question():
    assert not is_product_query("加入购物车会锁库存吗？")


def test_is_product_query_ignores_bare_stock_question():
    assert not is_product_query("库存")


def test_extract_product_keyword_keeps_category_query_term():
    assert extract_product_keyword("有哪些手机？") == "手机"


def test_normalize_product_alias_maps_apple_model_to_iphone():
    assert normalize_product_alias("苹果15Pro") == "iPhone 15 Pro"
    assert normalize_product_alias("苹果15") == "iPhone 15"
    assert normalize_product_alias("iphone15pro") == "iPhone 15 Pro"
    assert normalize_product_alias("苹果15ProMax") == "iPhone 15 Pro Max"
    assert normalize_product_alias("iphone15promax") == "iPhone 15 Pro Max"
    assert normalize_product_alias("iPhone SE") == "iPhone SE"


def test_normalize_product_alias_does_not_treat_plain_apple_as_iphone():
    assert normalize_product_alias("苹果") == "苹果"


def test_build_product_search_terms_does_not_broaden_plain_apple_price_query():
    assert build_product_search_terms("苹果多少钱？") == ["苹果"]


def test_build_product_search_terms_keeps_broad_catalog_fallback_for_category_queries():
    assert build_product_search_terms("有哪些手机？") == ["手机", None]


def test_extract_product_keyword_normalizes_common_phone_aliases():
    assert extract_product_keyword("苹果15Pro多少钱？") == "iPhone 15 Pro"
    assert extract_product_keyword("小米14多少钱？") == "Xiaomi 14"
    assert extract_product_keyword("红米K70有货吗？") == "Redmi K70"


def test_extract_product_keyword_ignores_greeting_noise_before_phone_alias():
    assert extract_product_keyword("你好，苹果15Pro多少钱？") == "iPhone 15 Pro"
    assert extract_product_keyword("您好 苹果15多少钱？") == "iPhone 15"
    assert extract_product_keyword("麻烦问下苹果15Pro多少钱？") == "iPhone 15 Pro"


def test_parse_product_query_distinguishes_base_and_pro_iphone_models():
    base_query = parse_product_query("苹果15多少钱？")
    assert base_query.brand == "iphone"
    assert base_query.series == "15"
    assert base_query.variant is None

    pro_query = parse_product_query("苹果15Pro多少钱？")
    assert pro_query.brand == "iphone"
    assert pro_query.series == "15"
    assert pro_query.variant == "pro"


def test_rank_product_candidates_does_not_treat_base_iphone_as_pro():
    query = parse_product_query("苹果15多少钱？")
    products = [
        {"id": 1, "name": "iPhone 15 Pro 128G", "price": 7999, "stock": 97},
        {"id": 2, "name": "iPhone 15 128G", "price": 5999, "stock": 120},
    ]

    ranked = rank_product_candidates(query, products)

    assert [product["id"] for product in ranked] == [2]


def test_rank_product_candidates_matches_requested_pro_variant():
    query = parse_product_query("苹果15Pro多少钱？")
    products = [
        {"id": 1, "name": "iPhone 15 Pro 128G", "price": 7999, "stock": 97},
        {"id": 2, "name": "iPhone 15 128G", "price": 5999, "stock": 120},
    ]

    ranked = rank_product_candidates(query, products)

    assert [product["id"] for product in ranked] == [1]


def test_rank_product_candidates_supports_other_brand_models():
    redmi_query = parse_product_query("红米K70有货吗？")
    xiaomi_query = parse_product_query("小米14多少钱？")
    products = [
        {"id": 3, "name": "Xiaomi 14 Pro 12+256G", "price": 4999, "stock": 200},
        {"id": 4, "name": "Redmi K70 12+256G", "price": 2299, "stock": 300},
    ]

    assert [product["id"] for product in rank_product_candidates(redmi_query, products)] == [4]
    assert [product["id"] for product in rank_product_candidates(xiaomi_query, products)] == [3]


def test_product_tool_does_not_return_unrelated_catalog_items_for_plain_apple_query():
    class PlainAppleProductTool(ProductToolClient):
        def __init__(self):
            super().__init__(
                SimpleNamespace(
                    backend_api_base_url="http://backend/api",
                    product_tool_timeout_seconds=3,
                    product_tool_max_results=5,
                )
            )
            self.seen_keywords = []

        def _list_products(self, keyword):
            self.seen_keywords.append(keyword)
            if keyword is None:
                return [{"id": 2, "name": "iPhone 15 128G", "price": 5999, "stock": 120}]
            return []

    tool = PlainAppleProductTool()

    assert tool.search("苹果多少钱？") == []
    assert tool.seen_keywords == ["苹果"]
