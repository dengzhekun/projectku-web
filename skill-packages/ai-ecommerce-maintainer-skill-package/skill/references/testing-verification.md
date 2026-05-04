# Testing and Verification

Use this reference to select verification without over-testing every small change.

## Test Selection

Frontend UI/text/style:

- build;
- text encoding regression if available;
- Playwright smoke for touched flows.

Backend business logic:

- unit tests for changed service;
- API smoke for changed controller;
- transaction/auth tests for payment, inventory, orders, wallet, coupons.

AI service:

- pytest for route/tool/retriever code;
- customer-service regression prompts;
- RAG runtime health if retrieval changed.

Deployment:

- container status;
- backend health;
- representative API smoke;
- AI chat smoke;
- fake-token smoke.

Performance:

- start target project first;
- run k6/JMeter from a separate test folder when possible;
- generate HTML/Allure-style reports only after there is actual result data;
- avoid counting a zero-loop run as performance evidence.

## Useful Command Patterns

PowerShell local checks:

```powershell
git status --short
cd frontend; npm run build
cd ..\back; mvn test
cd ..\ai-service; python -m pytest
```

AI regression script pattern:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-ai-cs-regression.ps1
```

LightRAG runtime pattern:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-lightrag-runtime.ps1 -CheckEmbeddingGateway
```

Playwright:

```powershell
cd frontend
npm run test:e2e:install
npm run test:e2e
```

k6:

```powershell
k6 run k6/checkout-smoke.js
```

## Reporting Standard

When done, report:

- what changed;
- which files changed;
- which verification commands ran;
- exact pass/fail summary;
- anything not verified and why.

Never hide failed tests. If failures are unrelated, say why with evidence.
