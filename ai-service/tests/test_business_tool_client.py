from types import SimpleNamespace

from app.clients.business_tool_client import (
    BusinessToolClient,
    format_coupons_business_facts,
    format_orders_business_facts,
    format_wallet_business_facts,
    normalize_auth_token,
)


def test_normalize_auth_token_accepts_raw_or_bearer_token():
    assert normalize_auth_token("abc.def") == "Bearer abc.def"
    assert normalize_auth_token("Bearer abc.def") == "Bearer abc.def"
    assert normalize_auth_token("   ") is None


def test_business_tool_client_sends_authorization_header(monkeypatch):
    captured = {}

    class DummyResponse:
        def raise_for_status(self):
            return None

        def json(self):
            return {"code": 200, "data": {"wallet": {"balance": "20000.00"}, "transactions": []}}

    class DummyHttpClient:
        def __init__(self, timeout):
            captured["timeout"] = timeout

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

        def get(self, url, headers=None, params=None):
            captured["url"] = url
            captured["headers"] = headers
            captured["params"] = params
            return DummyResponse()

    monkeypatch.setattr("app.clients.business_tool_client.httpx.Client", DummyHttpClient)

    client = BusinessToolClient(
        SimpleNamespace(backend_api_base_url="http://backend/api", product_tool_timeout_seconds=7)
    )
    result = client.get_wallet("token-123")

    assert captured["url"] == "http://backend/api/v1/me/wallet"
    assert captured["headers"] == {"Authorization": "Bearer token-123"}
    assert result["wallet"]["balance"] == "20000.00"


def test_format_wallet_business_facts_includes_balance_and_recent_transactions():
    facts = format_wallet_business_facts(
        {
            "wallet": {"balance": "19999.00", "status": "active"},
            "transactions": [
                {"type": "REGISTER_BONUS", "amount": "20000.00", "remark": "注册送余额"},
                {"type": "PAY", "amount": "-1.00", "remark": "订单支付"},
            ],
        }
    )

    assert "钱包余额: 19999.00" in facts
    assert "REGISTER_BONUS" in facts
    assert "订单支付" in facts


def test_format_orders_business_facts_translates_status_for_customer_service():
    facts = format_orders_business_facts(
        [
            {
                "id": 101,
                "orderNo": "ORD101",
                "status": "PAID",
                "payAmount": "899.00",
                "createdAt": "2026-04-27 10:00:00",
            }
        ]
    )

    assert "状态: 已支付，等待发货" in facts
    assert "PAID" in facts


def test_format_coupon_check_facts_marks_threshold_shortfall():
    facts = format_coupons_business_facts(
        {
            "valid": False,
            "reason": "未达到满减门槛",
            "minAmount": "300.00",
            "currentAmount": "199.00",
        },
        code="NEW300",
    )

    assert "查询券码: NEW300" in facts
    assert "还差: 101.00" in facts
    assert "不可用" in facts
