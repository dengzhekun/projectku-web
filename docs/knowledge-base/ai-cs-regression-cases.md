# AI 客服回归用例

现场回归脚本 `scripts/run-ai-cs-regression.ps1` 默认会向后端 `POST /api/v1/customer-service/chat` 发送以下问题，用于快速检查路由、知识库召回和回答文案是否回退。

## 内置用例

1. `苹果多少钱`
   - 目标：验证商品类问题仍然走商品路由，且对“苹果”这种过宽词给出稳定澄清。
   - 通过条件：
     - `answer` 包含 `苹果`
     - `answer` 还需包含 `水果苹果` 或 `词太宽`
     - `route=product`
     - `sourceType=product`
   - 备注：`fallbackReason` 允许为空或非空，不作为失败条件。

2. `售后质量问题退回运费谁承担`
   - 目标：验证售后知识库命中仍然优先于无关类目。
   - 通过条件：
     - `sourceType=knowledge`
     - `fallbackReason` 为空
     - `answer` 包含 `商家承担` 或 `运费通常由商家承担`
   - 建议关注：如果这里失败，通常说明售后规则未被索引、召回被其他类目干扰，或 prompt/路由发生回归。

3. `优惠券没到门槛为什么不能用`
   - 目标：验证优惠券问题仍然落到券规则。
   - 通过条件：
     - `route=coupon`，或
     - `answer` 包含 `门槛`

4. `物流一直不动怎么办`
   - 目标：验证物流问题仍然优先召回物流规则。
   - 通过条件：
     - `route=logistics`，或
     - `answer` 包含 `物流`

## 使用建议

- 适合做现场 smoke / regression，不替代完整自动化测试。
- 如果只有知识库相关 case 失败，而商品 case 正常，优先检查：
  - 文档是否已同步到当前运行时
  - LightRAG 或其他检索运行时是否可用
  - 最新索引是否覆盖售后、优惠券、物流三类规则
