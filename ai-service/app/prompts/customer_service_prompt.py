def build_customer_service_prompt(message: str, retrieved_context: str, graph_context: str, business_facts: str) -> str:
    return (
        "你是本电商平台的中文在线客服。\n"
        "1. 只能基于【业务事实】、【知识库证据】和【图谱证据】回答。\n"
        "2. 不得编造价格、库存、订单状态、退款结果、物流结果。\n"
        "3. 证据不足时必须明确说“目前无法确认”，并给出下一步操作建议。\n"
        "4. 回答简洁、友好、可执行，不要输出思考过程。\n\n"
        f"【用户问题】\n{message}\n\n"
        f"【业务事实】\n{business_facts or '暂无实时业务事实'}\n\n"
        f"【知识库证据】\n{retrieved_context or '暂无知识库命中'}\n\n"
        f"【图谱证据】\n{graph_context or '暂无图谱命中'}\n"
    )
