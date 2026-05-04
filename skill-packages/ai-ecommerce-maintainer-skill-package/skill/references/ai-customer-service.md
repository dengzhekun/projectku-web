# AI Customer Service

Use this reference for agent routing, prompts, realtime tools, RAG fallback, and customer-service regressions.

## Routing Model

Use a deterministic route-first design before letting the LLM improvise:

1. Personal business data:
   - orders, logistics for a user's order, wallet, coupons owned by the user, addresses, payment status, after-sales applications.
   - Require verified auth token.
   - Call backend business APIs.
2. Realtime product data:
   - price, stock, SKU, product list, model/version, product availability.
   - Call product API.
   - Always add "actual price and stock are subject to the order page" or equivalent.
3. Stable business rules:
   - after-sales policy, refund rules, coupon usage rules, logistics policy, payment instructions.
   - Query RAG/LightRAG.
4. Ambiguous or incomplete requests:
   - ask for missing model, SKU, memory, order number, coupon code, or scenario.

## Product Ambiguity

Do not treat every brand-like word as a product.

Examples:

- "苹果多少钱" is ambiguous. Clarify whether the user means fruit or a phone/product.
- "苹果15多少钱", "iPhone 15", "苹果15ProMax" strongly imply phone models.
- If multiple SKU versions match and memory/color is missing, list candidates or ask which version.
- If confidence is not high enough, ask rather than guessing.

## Tool vs Knowledge Base

Use tools for volatile facts:

- prices,
- stock,
- SKU variants,
- product availability,
- order status,
- wallet balance,
- coupon ownership/eligibility,
- payment state.

Use RAG for durable facts:

- return policy,
- shipping/logistics policy,
- coupon rule explanations,
- warranty/after-sales rules,
- shopping guides,
- FAQ.

## Auth Rules

- The backend should verify incoming Bearer tokens before passing them to the AI service.
- The AI service should only call personal APIs with a trusted token.
- Fake tokens should fail with 401, not downgrade into anonymous tool access.
- Anonymous users can receive general policy answers, but not personal order/wallet/coupon data.

## Answer Standards

- Be concise and specific.
- State uncertainty clearly.
- Do not invent prices, inventory, timelines, refund promises, or coupon validity.
- For multiple matches, show the likely matches and ask for missing specs.
- Include operational disclaimers where needed: price/stock subject to order page, refund result subject to review, logistics subject to carrier updates.

## Regression Cases To Maintain

Keep a small high-signal set:

- ambiguous product: "苹果多少钱";
- concrete product: "苹果15多少钱";
- multiple SKU: "苹果15 Pro 多少钱";
- after-sales rule: "质量问题退回运费谁承担";
- coupon rule: "优惠券没到门槛为什么不能用";
- logistics rule: "物流一直不动怎么办";
- anonymous personal query: "我的订单到哪了";
- fake token personal query should return 401.
