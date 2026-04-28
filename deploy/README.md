# Deployment Guide

This directory is the standard production deployment entrypoint for the public repository, aimed at Linux servers.

## Files

- `prod.env.example`: top-level Docker Compose secrets template
- `ai-service.env.example`: AI service environment template
- `lightrag.env.example`: LightRAG environment template
- `prepare_lightrag_env.sh`: generates or updates `lightrag.env` from known values in `ai-service.env`
- `bootstrap-prod.sh`: one-command production bootstrap
- `nginx.conf`: reverse proxy configuration

## Fastest Path

```bash
cp deploy/prod.env.example deploy/prod.env
cp deploy/ai-service.env.example deploy/ai-service.env
./deploy/prepare_lightrag_env.sh
vi deploy/prod.env
vi deploy/ai-service.env
vi deploy/lightrag.env
./deploy/bootstrap-prod.sh
```

## Values You Must Fill

### `deploy/prod.env`

- `MYSQL_ROOT_PASSWORD`
- `NEO4J_PASSWORD`

### `deploy/ai-service.env`

- `AI_LLM_API_KEY`
- `AI_EMBEDDING_REMOTE_URL` / `AI_EMBEDDING_REMOTE_API_KEY` if embeddings are remote
- `AI_EMBEDDING_GATEWAY_API_KEY` if the internal embedding gateway is enabled

### `deploy/lightrag.env`

- `LIGHTRAG_API_KEY`
- `POSTGRES_PASSWORD`
- `NEO4J_PASSWORD`
- `LLM_BINDING_API_KEY`

## Default Endpoints After Startup

- Frontend: `http://SERVER_IP/`
- KB admin: `http://SERVER_IP/admin/kb`
- Backend API: `http://SERVER_IP/api/v1/products`
- AI health: `http://SERVER_IP:9000/health` if you expose it

## Common Commands

Start:

```bash
./deploy/bootstrap-prod.sh
```

Status:

```bash
docker compose --env-file deploy/prod.env -f docker-compose.prod.yml ps
```

Logs:

```bash
docker compose --env-file deploy/prod.env -f docker-compose.prod.yml logs -f
```

Stop:

```bash
docker compose --env-file deploy/prod.env -f docker-compose.prod.yml down
```

More operator notes:

- `docs/deployment.md`
- `docs/deployment-faq.md`
