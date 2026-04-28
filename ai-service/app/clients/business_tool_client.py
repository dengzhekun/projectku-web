from __future__ import annotations

import re
from decimal import Decimal, InvalidOperation
from typing import Any

import httpx

from app.config import Settings


def normalize_auth_token(auth_token: str | None) -> str | None:
    if not auth_token or not auth_token.strip():
        return None
    stripped = auth_token.strip()
    if stripped.lower().startswith("bearer "):
        return stripped
    return f"Bearer {stripped}"


def extract_coupon_code(message: str) -> str | None:
    match = re.search(r"(?<![A-Za-z0-9])([A-Z]{2,}\d{2,})(?![A-Za-z0-9])", message)
    return match.group(1) if match else None


def extract_amount(message: str) -> str | None:
    match = re.search(r"(?:满|订单|消费|金额|买了|下单)\s*(\d+(?:\.\d+)?)", message)
    return match.group(1) if match else None


ORDER_STATUS_LABELS = {
    "PENDING_PAYMENT": "待支付",
    "UNPAID": "待支付",
    "PAID": "已支付，等待发货",
    "PENDING_SHIPMENT": "已支付，等待发货",
    "SHIPPED": "已发货，运输中",
    "DELIVERED": "已送达",
    "COMPLETED": "已完成",
    "CANCELLED": "已取消",
    "REFUNDING": "退款处理中",
    "REFUNDED": "已退款",
}

AFTERSALE_STATUS_LABELS = {
    "PENDING": "待审核",
    "APPROVED": "已通过，处理中",
    "REJECTED": "已拒绝",
    "CANCELLED": "已取消",
    "REFUNDING": "退款处理中",
    "REFUNDED": "已退款",
    "COMPLETED": "已完成",
}


def status_with_label(status: Any, labels: dict[str, str]) -> str:
    raw = "" if status is None else str(status)
    label = labels.get(raw.upper())
    return f"{label}（{raw}）" if label and raw else label or raw or "-"


def decimal_value(value: Any) -> Decimal | None:
    if value is None:
        return None
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None


class BusinessToolClient:
    def __init__(self, settings: Settings):
        self.base_url = settings.backend_api_base_url.rstrip("/")
        self.timeout = max(settings.product_tool_timeout_seconds, 1)

    def get_wallet(self, auth_token: str) -> dict[str, Any]:
        return self._get("/v1/me/wallet", auth_token)

    def list_orders(self, auth_token: str) -> list[dict[str, Any]]:
        data = self._get("/v1/orders", auth_token, params={"page": 1, "size": 5})
        return data if isinstance(data, list) else []

    def get_order(self, auth_token: str, order_id: int) -> dict[str, Any] | None:
        data = self._get(f"/v1/orders/{order_id}", auth_token)
        return data if isinstance(data, dict) else None

    def list_coupons(self, auth_token: str) -> list[dict[str, Any]]:
        data = self._get("/v1/coupons/available", auth_token)
        return data if isinstance(data, list) else []

    def check_coupon(self, auth_token: str, code: str, amount: str | None = None) -> dict[str, Any]:
        payload = {"amount": amount or "0"}
        data = self._post(f"/v1/coupons/{code}/check", auth_token, json=payload)
        return data if isinstance(data, dict) else {}

    def list_aftersales(self, auth_token: str) -> list[dict[str, Any]]:
        data = self._get("/v1/aftersales", auth_token, params={"page": 1, "size": 5})
        return data if isinstance(data, list) else []

    def _headers(self, auth_token: str) -> dict[str, str]:
        normalized = normalize_auth_token(auth_token)
        if not normalized:
            raise ValueError("auth token is required")
        return {"Authorization": normalized}

    def _get(self, path: str, auth_token: str, params: dict[str, Any] | None = None) -> Any:
        with httpx.Client(timeout=self.timeout) as client:
            response = client.get(f"{self.base_url}{path}", headers=self._headers(auth_token), params=params)
            response.raise_for_status()
        return self._extract_data(response.json())

    def _post(self, path: str, auth_token: str, json: dict[str, Any] | None = None) -> Any:
        with httpx.Client(timeout=self.timeout) as client:
            response = client.post(f"{self.base_url}{path}", headers=self._headers(auth_token), json=json or {})
            response.raise_for_status()
        return self._extract_data(response.json())

    def _extract_data(self, payload: Any) -> Any:
        if isinstance(payload, dict) and "data" in payload:
            return payload.get("data")
        return payload


def format_wallet_business_facts(data: dict[str, Any]) -> str:
    wallet = data.get("wallet") if isinstance(data, dict) else {}
    transactions = data.get("transactions") if isinstance(data, dict) else []
    lines = ["以下为后端钱包接口实时返回的数据；不得编造未返回的余额或流水。"]
    if isinstance(wallet, dict):
        lines.append(f"钱包余额: {wallet.get('balance')}")
        if wallet.get("status") is not None:
            lines.append(f"钱包状态: {wallet.get('status')}")
    if isinstance(transactions, list) and transactions:
        lines.append("最近流水:")
        for item in transactions[:5]:
            if not isinstance(item, dict):
                continue
            lines.append(
                f"- 类型: {item.get('type')}; 金额: {item.get('amount')}; 备注: {item.get('remark') or item.get('description')}"
            )
    return "\n".join(lines)


def format_orders_business_facts(orders: list[dict[str, Any]]) -> str:
    lines = ["以下为后端订单接口实时返回的数据；不得编造未返回的订单状态、金额或物流。"]
    if not orders:
        lines.append("当前未查询到订单。")
        return "\n".join(lines)
    for index, order in enumerate(orders[:5], start=1):
        lines.append(
            f"{index}. 订单ID: {order.get('id')}; 订单号: {order.get('orderNo')}; "
            f"状态: {status_with_label(order.get('status'), ORDER_STATUS_LABELS)}; "
            f"实付: {order.get('payAmount') or order.get('totalAmount')}; "
            f"创建时间: {order.get('createdAt') or order.get('createTime')}"
        )
        items = order.get("items")
        if isinstance(items, list) and items:
            item_names = [str(item.get("productName") or item.get("name")) for item in items if isinstance(item, dict)]
            lines.append(f"   商品: {'、'.join(item_names[:3])}")
    return "\n".join(lines)


def format_coupons_business_facts(data: Any, code: str | None = None) -> str:
    lines = ["以下为后端优惠券接口实时返回的数据；不得编造未返回的门槛、折扣或可用状态。"]
    if code:
        lines.append(f"查询券码: {code}")
    if isinstance(data, dict):
        valid = data.get("valid")
        if valid is not None:
            lines.append(f"可用状态: {'可用' if bool(valid) else '不可用'}")
        threshold = decimal_value(data.get("minAmount") or data.get("thresholdAmount"))
        current = decimal_value(data.get("currentAmount") or data.get("amount"))
        if threshold is not None and current is not None and current < threshold:
            lines.append(f"还差: {(threshold - current).quantize(Decimal('0.01'))}")
        for key, value in data.items():
            lines.append(f"{key}: {value}")
        return "\n".join(lines)
    if isinstance(data, list):
        if not data:
            lines.append("当前未查询到可用优惠券。")
            return "\n".join(lines)
        for index, coupon in enumerate(data[:8], start=1):
            if not isinstance(coupon, dict):
                continue
            lines.append(
                f"{index}. 券码: {coupon.get('code')}; 名称: {coupon.get('name')}; "
                f"门槛: {coupon.get('thresholdAmount') or coupon.get('minAmount')}; "
                f"优惠: {coupon.get('discountAmount') or coupon.get('amount')}; 状态: {coupon.get('status')}"
            )
    return "\n".join(lines)


def format_aftersales_business_facts(items: list[dict[str, Any]]) -> str:
    lines = ["以下为后端售后接口实时返回的数据；不得编造未返回的审核状态或退款进度。"]
    if not items:
        lines.append("当前未查询到售后申请。")
        return "\n".join(lines)
    for index, item in enumerate(items[:5], start=1):
        lines.append(
            f"{index}. 售后ID: {item.get('id')}; 订单ID: {item.get('orderId')}; "
            f"类型: {item.get('type')}; 状态: {status_with_label(item.get('status'), AFTERSALE_STATUS_LABELS)}; "
            f"原因: {item.get('reason')}"
        )
    return "\n".join(lines)
