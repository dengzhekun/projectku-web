from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import re
import sys
from pathlib import Path
from urllib import request


DEFAULT_CASES = [
    {
        "name": "iphone 15 price uses product tool",
        "message": "苹果15多少钱？",
        "must_include": ["5999", "下单页"],
        "must_not_include": ["iPhone 15 Pro"],
        "citation_source_type": "product",
    },
    {
        "name": "wallet balance policy",
        "message": "注册送多少钱余额？余额不足怎么办？",
        "must_include": ["20000", "余额不足"],
        "must_not_include": ["HUAWEI Mate X5"],
        "citation_source_type": "kb",
    },
    {
        "name": "cart does not lock stock",
        "message": "加入购物车会锁库存吗？",
        "must_include": ["提交订单", "库存"],
        "must_not_include": ["HUAWEI Mate X5"],
        "citation_source_type": "kb",
    },
    {
        "name": "coupon code route",
        "message": "NEW300为什么不能用？",
        "must_include": ["5000", "300"],
        "must_not_include": ["HUAWEI Mate X5"],
        "citation_source_type": "kb",
    },
    {
        "name": "after sales order state",
        "message": "什么订单可以申请售后？",
        "must_include": ["已支付", "已发货", "已完成"],
        "must_not_include": ["48"],
        "citation_source_type": "kb",
    },
]


@dataclass
class EvalResult:
    name: str
    passed: bool
    failures: list[str]
    answer: str


def evaluate_case(case: dict, response: dict) -> EvalResult:
    answer = str(response.get("answer") or "")
    failures: list[str] = []

    for expected in case.get("must_include", []):
        if not contains_expected(answer, str(expected)):
            failures.append(f"missing: {expected}")

    for forbidden in case.get("must_not_include", []):
        if forbidden in answer:
            failures.append(f"forbidden: {forbidden}")

    expected_source_type = case.get("citation_source_type")
    if expected_source_type:
        citations = response.get("citations") or []
        source_types = {citation.get("sourceType") for citation in citations if isinstance(citation, dict)}
        if expected_source_type not in source_types:
            failures.append(f"missing citation source type: {expected_source_type}")

    return EvalResult(
        name=str(case.get("name") or case.get("message") or "unnamed"),
        passed=not failures,
        failures=failures,
        answer=answer,
    )


def contains_expected(answer: str, expected: str) -> bool:
    if expected in answer:
        return True
    if re.fullmatch(r"\d+(?:\.\d+)?", expected):
        return expected in answer.replace(",", "")
    return False


def post_chat(base_url: str, message: str, timeout: int) -> dict:
    payload = json.dumps({"message": message}, ensure_ascii=False).encode("utf-8")
    req = request.Request(
        base_url.rstrip("/") + "/chat",
        data=payload,
        headers={"Content-Type": "application/json; charset=utf-8", "Accept": "application/json"},
        method="POST",
    )
    with request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def load_cases(path: str | None) -> list[dict]:
    if not path:
        return DEFAULT_CASES
    case_path = Path(path)
    return json.loads(case_path.read_text(encoding="utf-8"))


def run_eval(base_url: str, cases: list[dict], timeout: int) -> list[EvalResult]:
    results: list[EvalResult] = []
    for case in cases:
        response = post_chat(base_url, str(case["message"]), timeout)
        results.append(evaluate_case(case, response))
    return results


def main() -> int:
    parser = argparse.ArgumentParser(description="Run AI customer-service smoke evaluations.")
    parser.add_argument("--base-url", default="http://127.0.0.1:9000")
    parser.add_argument("--cases")
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args()

    results = run_eval(args.base_url, load_cases(args.cases), args.timeout)
    passed = sum(1 for result in results if result.passed)
    print(f"AI customer-service eval: {passed}/{len(results)} passed")
    for result in results:
        status = "PASS" if result.passed else "FAIL"
        print(f"- {status} {result.name}")
        for failure in result.failures:
            print(f"  {failure}")

    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
