# Project Handoff

This repository is public:

- Repo: `https://github.com/dengzhekun/projectku-web`
- Clone:

```bash
git clone https://github.com/dengzhekun/projectku-web.git
cd projectku-web
```

## What This Project Is

ProjectKu Web is a full-stack e-commerce project with:

- Vue 3 storefront
- Spring Boot backend API
- MySQL data store
- FastAPI AI customer-service service
- Knowledge-base admin and LightRAG integration path

## Fastest Local Start

If you only need the storefront and backend first:

1. Prepare dependencies:
   - Java 17
   - Maven
   - Node.js 20
   - npm
   - MySQL 8
2. Initialize the local database:

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p123456 --default-character-set=utf8mb4 web < back/sql/init_db.sql
```

3. Start the backend:

```bash
cd back
mvn spring-boot:run
```

4. Start the frontend in a second terminal:

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1
```

5. Open:
   - Frontend: `http://127.0.0.1:5173/`
   - Swagger: `http://localhost:8080/api/swagger-ui-custom.html`

For the full AI-enabled setup, start with [README.md](README.md) and then [deploy/README.md](deploy/README.md).

## Production Entry Points

Current public site:

- Storefront: `https://evanshine.me/`
- KB admin: `https://evanshine.me/admin/kb`

GitHub release page:

- Releases: `https://github.com/dengzhekun/projectku-web/releases`

## Repo Structure

- `frontend/`: Vue storefront
- `back/`: Spring Boot backend
- `ai-service/`: AI customer-service and knowledge-base service
- `deploy/`: deployment templates and scripts
- `docs/`: architecture, KB, deployment, and design notes
- `k6/`: smoke load scripts

## Important Config Files

Local and production setup depends on these templates:

- `deploy/prod.env.example`
- `deploy/ai-service.env.example`
- `deploy/lightrag.env.example`

Do not commit real secrets. Copy these templates to working env files and fill the values per environment.

## What To Read First

Recommended order for a new maintainer:

1. [README.md](README.md)
2. [deploy/README.md](deploy/README.md)
3. [docs/deployment.md](docs/deployment.md)
4. [docs/deployment-faq.md](docs/deployment-faq.md)

If taking over AI and KB flows specifically, then continue with:

5. `docs/knowledge-base/`
6. `ai-service/README.md` if present, plus the service code under `ai-service/app/`

## Common Maintenance Commands

Frontend build:

```bash
cd frontend
npm install
npm run build
```

Frontend smoke tests:

```bash
cd frontend
npm run test:e2e:install
npm run test:e2e
```

k6 checkout smoke:

```bash
k6 run k6/checkout-smoke.js
```

Backend local run:

```bash
cd back
mvn spring-boot:run
```

## Operational Notes

- The public GitHub repo does not by itself update the production server.
- Server deploys should be treated as an explicit release step.
- Frontend static assets are versioned build outputs, so always rebuild before publishing.
- AI service, embeddings, and LightRAG may use environment-specific endpoints and keys that are not stored in this repo.

## Handoff Checklist

When taking over this project, verify these items first:

1. You can clone the repo and run the frontend locally.
2. You can start the backend and access the products API.
3. You know which environment variables are still missing for AI and production.
4. You know whether you are only maintaining local development, or also production deployment.
5. You know where the current live domain and server entry points are.

## Public Repo Link To Share

This is the link you can send directly to a collaborator:

`https://github.com/dengzhekun/projectku-web`
