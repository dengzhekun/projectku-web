# LightRAG 高级优化与排查说明

更新时间：2026-05-02

## 目标

当前项目已经采用 LightRAG-only 作为 AI 客服知识库运行路径。本说明记录高级优化后的可观测性、回归验证和知识库治理规则，避免后续误判“客服答对但后台 chunk 命中为空”等情况。

## retrievalTrace 字段

AI 服务的 `/chat` 和 `/chat/stream` 最终回复现在会在知识库路径返回 `retrievalTrace`。

示例字段：

```json
{
  "retriever": "lightrag",
  "requestedTopK": 20,
  "returnedChunkCount": 2,
  "selectedChunkCount": 1,
  "attributionStatus": "chunk_level",
  "selectedCategories": ["coupon"],
  "selectedSourceIds": ["kb:6:3:12"],
  "citationCount": 1,
  "hitLogCount": 1,
  "notes": []
}
```

## attributionStatus 含义

- `chunk_level`：LightRAG 返回了可解析的 `document_id` 和 `chunk_id`，后台可以记录具体 chunk 命中日志。
- `answer_level`：LightRAG 只返回 answer-level 证据，客服回答可以是有依据的，但后台不一定能记录具体 chunk。
- `source_level`：有来源或 citation，但缺少可落库的 chunk id。
- `none`：没有可靠知识来源。

## 如何判断问题来源

如果客服回答不理想，先看：

1. `route` 是否正确。
2. `sourceType` 是否正确。
3. `retrievalTrace.returnedChunkCount` 是否为 0。
4. `retrievalTrace.selectedChunkCount` 是否为 0。
5. `retrievalTrace.selectedCategories` 是否符合问题类型。
6. `retrievalTrace.attributionStatus` 是否为 `answer_level` 或 `source_level`。
7. `fallbackReason` 是否非空。

判断方式：

- `returnedChunkCount=0`：多半是索引、embedding、LightRAG 查询或知识内容覆盖问题。
- `returnedChunkCount>0` 但 `selectedChunkCount=0`：多半是类别重排过滤掉了不相关内容，可能要补对应分类文档。
- `selectedChunkCount>0` 但 `hitLogCount=0`：多半是来源追踪降级，不一定是回答没命中。
- `selectedCategories` 不对：检查文档 category 或路由关键词。

## 回归用例扩展

现场回归脚本现在覆盖：

- 苹果宽词澄清
- 苹果15具体商品查询
- 苹果15Pro多规格追问
- 售后质量问题退回运费
- 售后可申请订单状态
- 优惠券门槛
- 物流不更新
- 未登录余额查询
- 未登录订单查询

运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-ai-cs-regression.ps1
```

## 知识库治理规则

继续补知识库时，优先补稳定规则，不要把实时数据塞进知识库。

适合知识库：

- 售后规则
- 物流规则
- 优惠券规则
- 支付退款规则
- 购物流程说明
- 平台 FAQ

不适合知识库：

- 商品实时价格
- 库存
- SKU 可售状态
- 用户订单状态
- 用户余额
- 用户个人优惠券列表

这些实时数据应该走后端工具 API。

## 后续可继续增强

如果要继续做更细来源追踪，可以考虑：

1. 在 LightRAG ingest 文本 envelope 中继续保留 `document_id`、`chunk_id`、`category`、`version`。
2. 优先使用能返回 chunk source 的 LightRAG `/query/data` 路径。
3. 在后台客服日志表增加 trace JSON 字段，用于保存 `retrievalTrace`。
4. 在管理后台客服查询日志里展示 `attributionStatus` 和 `selectedCategories`。

