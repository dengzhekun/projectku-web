def build_seed_records() -> list[dict]:
    return [
        {
            "id": "faq:after_sale_entry:v1",
            "document": "进入订单详情页，点击申请售后，根据页面提示提交即可。",
            "metadata": {
                "source_type": "faq",
                "source_id": "after_sale_entry",
                "title": "如何申请售后",
                "visibility": "public",
                "version": 1,
            },
        },
        {
            "id": "policy:after_sale_scope:v1",
            "document": "当前项目只明确支持已支付、已发货、已完成订单发起售后申请；待支付和已取消订单不能申请售后。具体处理结果以售后单审核和订单页面为准。",
            "metadata": {
                "source_type": "policy",
                "source_id": "after_sale_scope",
                "title": "售后申请范围",
                "visibility": "public",
                "version": 1,
            },
        },
        {
            "id": "faq:customer_service_scope:v1",
            "document": "订单、支付、退款、物流等结果以系统页面显示为准，智能客服只提供解释和操作建议。",
            "metadata": {
                "source_type": "faq",
                "source_id": "customer_service_scope",
                "title": "客服能力范围",
                "visibility": "public",
                "version": 1,
            },
        },
    ]
