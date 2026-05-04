# ProjectKu Case Study

This is a reusable case study, not a secret store. Do not add real API keys, passwords, or private tokens.

## Project Identity

ProjectKu is a full-stack AI ecommerce project with:

- Vue 3 + Vite storefront;
- Spring Boot 3 backend;
- MySQL database;
- FastAPI AI customer-service service;
- LightRAG knowledge-base runtime;
- admin knowledge-base UI;
- wallet balance, coupons, orders, reviews, after-sales, inventory, and product SKU flows.

Public repo:

```text
https://github.com/dengzhekun/projectku-web
```

Known full local repo path during the original work:

```text
D:\web-main
```

A similarly named C drive folder was not the full repo. Always verify root before acting.

## Important ProjectKu Lessons

- Product price/stock/SKU must come from realtime backend APIs, not RAG.
- Policies such as after-sales, coupon rules, logistics, refund rules belong in KB/RAG.
- "苹果多少钱" should be treated as ambiguous; "苹果15" or "iPhone 15" can route to phones.
- When SKU memory/color/spec is missing, ask or list candidates instead of guessing.
- AI客服 should not require login for general policy questions.
- AI客服 must require verified auth for personal order/wallet/coupon data.
- Fake Bearer tokens should return 401.
- Cart does not reduce stock; order/payment flow must guard against overselling.
- LightRAG answer-level citations may produce valid answers while admin chunk hit logs are empty.
- Local/GitHub updated does not mean the cloud server is updated.

## ProjectKu File Map

Common paths:

- frontend views: `frontend/src/views/`;
- frontend customer-service client: `frontend/src/lib/customerService.ts`;
- backend controllers: `back/src/main/java/com/web/controller/`;
- backend services: `back/src/main/java/com/web/service/impl/`;
- backend token verification: `back/src/main/java/com/web/security/AuthTokenService.java`;
- AI chat route: `ai-service/app/api/chat.py`;
- product tool: `ai-service/app/clients/product_tool_client.py`;
- business tool: `ai-service/app/clients/business_tool_client.py`;
- LightRAG retriever: `ai-service/app/retrieval/lightrag_retriever.py`;
- customer-service prompt: `ai-service/app/prompts/customer_service_prompt.py`;
- KB seed docs: `docs/knowledge-base/seed/`;
- deployment docs: `deploy/README.md`, `docs/deployment.md`;
- handoff docs: `HANDOFF.md`, `AI_HANDOFF.md` when present.

## ProjectKu Validation Examples

Typical local commands:

```powershell
cd D:\web-main\back
mvn test

cd D:\web-main\ai-service
python -m pytest

cd D:\web-main\frontend
npm run build
```

AI service regression:

```powershell
cd D:\web-main
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-ai-cs-regression.ps1
```

LightRAG check:

```powershell
cd D:\web-main
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-lightrag-runtime.ps1 -CheckEmbeddingGateway
```

## ProjectKu Server Caution

During the original work, the cloud server also ran Metapi next to the ecommerce stack. The maintainer must not stop, rebuild, or reconfigure unrelated services when deploying the shop.

If a server handoff says "do not update yet", obey that. Generate docs or plans only.
