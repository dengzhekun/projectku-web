# ProjectKu Web

## Handoff

If someone else is taking over or helping maintain this project, start with [HANDOFF.md](HANDOFF.md).

ProjectKu Web is a full-stack e-commerce project with product catalog, cart, orders, coupons, after-sales flows, reviews, wallet balance, and an AI customer-service knowledge base.

![ProjectKu homepage](docs/repo-assets/homepage.png)

![Release](https://img.shields.io/github/v/release/dengzhekun/projectku-web)
![License](https://img.shields.io/github/license/dengzhekun/projectku-web)

## Highlights

- Storefront frontend built with Vue 3 and Vite
- Backend API built with Spring Boot 3, MyBatis, and MySQL
- AI customer service built with FastAPI, RAG retrieval, and realtime product lookup
- Knowledge-base admin with document ingest, indexing, hit logs, and miss logs
- LightRAG integration path with Neo4j and PostgreSQL/pgvector

## Tech Stack

- Frontend: Vue 3, TypeScript, Pinia, Vite
- Backend: Java 17, Spring Boot 3, MyBatis
- AI service: Python, FastAPI, Chroma, LightRAG, Neo4j
- Infra: Docker Compose, Nginx, MySQL 8, PostgreSQL/pgvector

## Repository Layout

```text
frontend/      Vue storefront
back/          Spring Boot backend
ai-service/    AI customer-service and knowledge-base service
deploy/        Production deployment templates and scripts
docs/          Design, deployment, API, and KB docs
scripts/       Local helper and validation scripts
```

## Architecture

### Current system

![ProjectKu system architecture](docs/knowledge-base/diagrams/01-current-system-architecture.png)

### AI customer-service target flow

![ProjectKu AI service target flow](docs/knowledge-base/diagrams/03-target-ai-service-flow.png)

## Quick Start

### 1) Minimal local run (storefront + backend)

Use this path if you want the shortest working local setup without bringing up the AI service.

Prerequisites:

- Java 17 + Maven (`mvn`)
- Node.js 20 + npm (`npm`)
- MySQL 8

Default backend local database settings are `localhost:3306`, database `web`, username `root`, password `123456`.

Import the schema and seed data:

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p123456 --default-character-set=utf8mb4 web < back/sql/init_db.sql
```

Start backend:

```bash
cd back
mvn spring-boot:run
```

Start frontend:

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1
```

### 2) AI-enabled local run

Use this path if you want the full local stack, including the AI service and seeded knowledge-base content.

Prerequisites:

- Java 17 + Maven (`mvn`)
- Node.js 20 + npm (`npm`)
- Python 3.11+ (`python`)
- Docker (recommended, for local MySQL auto-start)

Fastest path:

Windows PowerShell:

```powershell
.\start_all.ps1 -Mode dev -InstallAiDeps -SeedAiKb
```

Linux / macOS:

```bash
./start_all.sh dev --install-ai-deps --seed-ai-kb
```

To make AI chat actually answer through your remote LLM, copy `deploy/ai-service.env.example` to `deploy/ai-service.env` and fill at least:

- `AI_LLM_API_KEY`
- `AI_LLM_BASE_URL` / `AI_LLM_MODEL` if you are not using the default provider

By default the AI service uses local `BAAI/bge-m3` embeddings, so the first startup may download model files. If you already have a remote embedding service, set `AI_EMBEDDING_PROVIDER=remote_http` and fill the remote embedding variables instead.

Default local URLs after startup:

- Frontend: `http://127.0.0.1:5173/`
- Backend: `http://localhost:8080/api`
- AI health: `http://127.0.0.1:9000/health`

Optional frontend text-encoding regression check:

```bash
node scripts/verify_frontend_text_encoding.js
```

### 3) Production deployment

Fastest production path (Linux server):

```bash
cp deploy/prod.env.example deploy/prod.env
cp deploy/ai-service.env.example deploy/ai-service.env
./deploy/prepare_lightrag_env.sh
./deploy/bootstrap-prod.sh
```

Alternative from repo root (same production compose stack):

```bash
./start_all.sh prod
```

```powershell
.\start_all.ps1 -Mode prod
```

Deployment docs:

- `deploy/README.md`
- `docs/deployment.md`
- `docs/deployment-faq.md`

## Repository Automation

- GitHub Actions CI runs frontend install/build, frontend text-encoding regression checks, backend unit tests, and a lightweight `ai-service` unit-test subset.
- Public issue intake is structured with bug-report and feature-request templates.

## Local Smoke Testing

Playwright smoke tests live under `frontend/tests/` and target a running local stack.

Install the browser once:

```bash
cd frontend
npm run test:e2e:install
```

Run smoke tests after frontend and backend are already up:

```bash
cd frontend
npm run test:e2e
```

Default assumptions:

- frontend: `http://127.0.0.1:5173`
- backend API: `http://127.0.0.1:8080/api`
- seeded login: `user@example.com` / `123456`

Override when needed:

```bash
PLAYWRIGHT_BASE_URL=http://127.0.0.1:5173 PLAYWRIGHT_API_BASE_URL=http://127.0.0.1:8080/api npm run test:e2e
```

Lightweight `k6` smoke scripts live under `k6/` and are only for local or staging checks.

```bash
k6 run k6/checkout-smoke.js
```

Recommended production stack:

- Nginx for frontend entry
- Spring Boot backend with `prod` profile
- FastAPI AI service
- MySQL 8
- Neo4j 5
- PostgreSQL with pgvector
- LightRAG in staged rollout mode

## Main Views

- Home: `frontend/src/views/HomeView.vue`
- Product detail: `frontend/src/views/ProductDetailView.vue`
- Cart: `frontend/src/views/CartView.vue`
- Knowledge base admin: `frontend/src/views/KnowledgeBaseAdminView.vue`

## Main APIs

- Products: `/api/v1/products`
- Orders: `/api/v1/orders`
- Payments: `/api/v1/payments`
- AI chat: `/api/v1/customer-service/chat`

OpenAPI:

- Swagger UI: `http://localhost:8080/api/swagger-ui-custom.html`
- OpenAPI JSON: `http://localhost:8080/api/api-docs`

## Related Docs

- `docs/api-contract.md`
- `docs/ai-service-runbook.md`
- `docs/ai-customer-service-knowledge-base-design.md`
- `docs/knowledge-base/2026-04-26-rag-solution-comparison.md`
- `docs/knowledge-base/diagrams/`
- `docs/deployment-faq.md`
- `CONTRIBUTING.md`

## Public Release Notes

- `v0.1.0`: first public release, deployment templates and release packaging
- `v0.1.1`: README visualization, public repository polish, and GitHub-facing deployment entry cleanup
- `v0.1.2`: public repo documentation expansion and deployment FAQ cleanup
- `v0.1.3`: homepage screenshot and frontend encoding regression guard
- `v0.1.4`: GitHub CI and public issue templates
- `v0.1.5`: quick-start clarification and lightweight AI-service CI
- `v0.1.6`: Playwright smoke tests, k6 checkout smoke, and checkout coupon default fix

## Notes

- `deploy/ai-service.env`, `deploy/lightrag.env`, and `deploy/prod.env` are local/server secrets and are intentionally not committed.
- The current release keeps Chroma as the stable retrieval path while allowing staged migration to LightRAG.
- See `NOTICE` for trademark, demo asset, and redistribution caveats.
- `docs/repo-assets/homepage.png` is generated from the local running frontend and can be refreshed after UI updates.
