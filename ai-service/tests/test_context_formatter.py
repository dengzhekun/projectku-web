from app.retrieval.context_formatter import merge_context


def test_merge_context_includes_source_metadata():
    chunks = [
        {"document": "七天无理由适用于未拆封商品", "metadata": {"source_type": "policy", "source_id": "return_7d"}},
        {"document": "订单详情页可以发起售后", "metadata": {"source_type": "faq", "source_id": "after_sale_entry"}},
    ]

    merged = merge_context(chunks)

    assert "七天无理由适用于未拆封商品" in merged
    assert "policy:return_7d" in merged


def test_merge_context_tolerates_missing_metadata():
    chunks = [
        None,
        "not-a-chunk",
        {"document": "LightRAG 只返回了答案级内容"},
        {"document": "字段不完整也不能中断客服回答", "metadata": {"source_type": "lightrag"}},
    ]

    merged = merge_context(chunks)

    assert "LightRAG 只返回了答案级内容" in merged
    assert "字段不完整也不能中断客服回答" in merged
    assert "lightrag:unknown" in merged
