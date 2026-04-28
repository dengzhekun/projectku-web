import importlib.util
from pathlib import Path
import sys


def load_eval_module():
    script_path = Path(__file__).resolve().parents[2] / "scripts" / "eval_ai_customer_service.py"
    spec = importlib.util.spec_from_file_location("eval_ai_customer_service", script_path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def test_evaluate_case_checks_required_text_and_source_type():
    module = load_eval_module()
    case = {
        "name": "iphone price",
        "message": "苹果15多少钱？",
        "must_include": ["5999", "下单页"],
        "must_not_include": ["iPhone 15 Pro"],
        "citation_source_type": "product",
    }
    response = {
        "answer": "iPhone 15 128G 是 5999 元，实际价格和库存以下单页为准。",
        "citations": [{"sourceType": "product", "sourceId": "2", "title": "iPhone 15 128G"}],
    }

    result = module.evaluate_case(case, response)

    assert result.passed is True
    assert result.failures == []


def test_evaluate_case_reports_missing_text_for_bad_answer():
    module = load_eval_module()
    case = {
        "name": "cart stock",
        "message": "加入购物车会锁库存吗？",
        "must_include": ["提交订单", "扣库存"],
        "must_not_include": ["HUAWEI Mate X5"],
    }
    response = {"answer": "有的，我先查到 HUAWEI Mate X5。", "citations": []}

    result = module.evaluate_case(case, response)

    assert result.passed is False
    assert "missing: 提交订单" in result.failures
    assert "missing: 扣库存" in result.failures
    assert "forbidden: HUAWEI Mate X5" in result.failures


def test_evaluate_case_accepts_comma_formatted_numbers():
    module = load_eval_module()
    case = {"name": "wallet", "message": "余额", "must_include": ["20000"]}
    response = {"answer": "新用户默认赠送 20,000.00 元余额。", "citations": []}

    result = module.evaluate_case(case, response)

    assert result.passed is True
