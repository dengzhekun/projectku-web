# ProjectKu Production Deployment

## Recommended Entry

For the public repository, the recommended deployment path is the script and template set under `deploy/`.

Minimal flow:

```bash
cp deploy/prod.env.example deploy/prod.env
cp deploy/ai-service.env.example deploy/ai-service.env
./deploy/prepare_lightrag_env.sh
vi deploy/prod.env
vi deploy/ai-service.env
vi deploy/lightrag.env
./deploy/bootstrap-prod.sh
```

If you only need the quick operator view, start with `deploy/README.md`.
For recurring operator questions, also keep `docs/deployment-faq.md` nearby.

## Runtime Architecture

- Frontend: Vue build served by Nginx
- Backend: Spring Boot 3 with `prod` profile
- AI service: FastAPI for AI chat, product lookup, and KB retrieval
- MySQL 8: business database
- Neo4j 5: graph storage
- PostgreSQL + pgvector: LightRAG vector and document-status storage
- LightRAG: graph-enhanced retrieval service

Ports:

- `80`: frontend entry
- `7474`: Neo4j Browser
- `7687`: Neo4j Bolt
- `19621` on loopback: LightRAG server

## Environment Files

### `deploy/prod.env`

Used for top-level variable interpolation in `docker-compose.prod.yml`:

```env
MYSQL_ROOT_PASSWORD=change_me
MYSQL_DATABASE=web
NEO4J_PASSWORD=change_me
```

### `deploy/ai-service.env`

Main AI-service config. Copy from `deploy/ai-service.env.example`.

Check these values carefully:

- `AI_LLM_BASE_URL`
- `AI_LLM_API_KEY`
- `AI_LLM_MODEL`
- `AI_EMBEDDING_PROVIDER`
- `AI_EMBEDDING_MODEL`
- `AI_EMBEDDING_REMOTE_URL`
- `AI_EMBEDDING_REMOTE_API_KEY`
- `AI_EMBEDDING_GATEWAY_API_KEY`
- `KNOWLEDGE_RETRIEVER`
- `LIGHTRAG_BASE_URL`
- `LIGHTRAG_API_KEY`
- `NEO4J_PASSWORD`

### `deploy/lightrag.env`

Generate it first with:

```bash
./deploy/prepare_lightrag_env.sh
```

That script syncs these values from `ai-service.env`:

- `AI_LLM_BASE_URL -> LLM_BINDING_HOST`
- `AI_LLM_API_KEY -> LLM_BINDING_API_KEY`
- `AI_LLM_MODEL -> LLM_MODEL`
- `AI_EMBEDDING_GATEWAY_API_KEY -> EMBEDDING_BINDING_API_KEY`
- `AI_EMBEDDING_MODEL -> EMBEDDING_MODEL`
- `NEO4J_PASSWORD -> NEO4J_PASSWORD`

You still need to set:

- `LIGHTRAG_API_KEY`
- `POSTGRES_PASSWORD`

## Startup

```bash
./deploy/bootstrap-prod.sh
```

Equivalent compose command:

```bash
docker compose --env-file deploy/prod.env -f docker-compose.prod.yml up -d --build
```

## Endpoints

- Front page: `http://SERVER_IP/`
- KB admin: `http://SERVER_IP/admin/kb`
- Product API: `http://SERVER_IP/api/v1/products`

If you explicitly expose more ports:

- AI health: `http://SERVER_IP:9000/health`
- Neo4j Browser: `http://SERVER_IP:7474`

## Operations

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

## Retriever Mode Guidance

Interpret `KNOWLEDGE_RETRIEVER` in `deploy/ai-service.env` like this:

- `chroma`: safest current path
- `lightrag`: pure LightRAG, all dependencies must be healthy
- `lightrag_with_chroma_fallback`: safer trial mode for staged rollout

Fast rollback:

1. Set `KNOWLEDGE_RETRIEVER=chroma`
2. Restart `ai-service`

## Memory Guidance

This project can use a remote LLM, so server memory is mainly consumed by Java, MySQL, Neo4j, LightRAG, and embeddings.

- Minimum workable: `8 GB`
- Better baseline: `12 GB`
- Safer target: `16 GB`

Typical ranges:

- MySQL: `0.6-1.2 GB`
- Spring Boot: `0.4-0.9 GB`
- AI service: `0.3-0.8 GB`
- Neo4j: `1.5-3 GB`
- Local BGE-M3 embeddings: `2-6 GB`

For low-memory servers, prefer:

- remote embeddings
- moving Neo4j off the main app host
- keeping `chroma` as the primary retrieval path first
