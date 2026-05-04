# Architecture Discovery

Use this reference when entering an unfamiliar AI ecommerce repository.

## Fast Discovery

Run cheap read-only checks first:

```powershell
Get-Location
git status --short
git log -3 --oneline --decorate
Get-ChildItem -Force
rg --files -g "!node_modules" -g "!target" -g "!.git" | Select-Object -First 200
```

Prefer `rg` / `rg --files`. Avoid broad recursive commands over generated directories.

## Identify Stack

Look for:

- frontend: `package.json`, `vite.config.*`, `src/router`, `src/views`, `src/components`;
- backend: `pom.xml`, `build.gradle`, `src/main/java`, `src/main/resources/mapper`, controllers/services/mappers;
- AI service: `FastAPI`, `uvicorn`, `requirements.txt`, `app/api`, `app/clients`, `app/retrieval`;
- database: `sql/`, migration folders, MyBatis XML, Prisma/TypeORM migrations;
- deployment: `docker-compose*.yml`, `deploy/`, `nginx.conf`, `.env.example`;
- tests: `src/test`, `tests/`, Playwright, k6, JMeter folders.

## Make a Navigation Map

Before modifying behavior, map these user-visible domains:

- home/catalog/search/category/product detail;
- product SKU, price, stock;
- cart and checkout;
- order creation, payment, cancellation, timeout cleanup;
- wallet/balance and transaction logs;
- coupons and threshold checks;
- reviews and after-sales;
- AI customer-service widget;
- admin/knowledge-base pages;
- auth, admin auth, route guards.

## Boundaries

Record which service owns each responsibility:

- frontend renders and calls APIs;
- backend enforces business correctness and auth;
- AI service routes language requests and calls tools/retrieval;
- database persists source of truth;
- RAG runtime stores searchable policy/document knowledge;
- deployment layer wires ports, env, volumes, and reverse proxy.

If a file is large or mixed-purpose, avoid broad refactors unless the current task needs a small boundary improvement.
