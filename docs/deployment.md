# ProjectKu Production Deployment

## Architecture

- Frontend: Vue build output served by Nginx.
- Backend: Spring Boot 3 with the `prod` profile.
- AI service: FastAPI, Xunfei Coding Plan LLM, local BGE-M3 embeddings, Chroma vector store.
- Graph database: Neo4j 5 community for product, category, and policy relationships.
- Database: MySQL 8 with persistent volume.
- Entry point: Nginx listens on port `80` and proxies `/api/` to the backend.

## Docker Compose Deployment

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

After deployment:

- Frontend: `http://SERVER_IP/`
- Knowledge base admin: `http://SERVER_IP/admin/kb`
- Backend API: `http://SERVER_IP/api/v1/products`
- AI health check: `http://SERVER_IP:9000/health` if exposed internally or by firewall rule.
- Neo4j browser: `http://SERVER_IP:7474` if exposed internally or by firewall rule.

## Required Environment

Set strong passwords before production deployment:

```bash
MYSQL_ROOT_PASSWORD=change_me
NEO4J_PASSWORD=change_me
```

The AI service reads `deploy/ai-service.env`. Confirm these values before deployment:

- `AI_LLM_BASE_URL=https://maas-coding-api.cn-huabei-1.xf-yun.com/v2`
- `AI_LLM_MODEL=astron-code-latest`
- `AI_LLM_API_KEY=...`
- `AI_EMBEDDING_MODEL=BAAI/bge-m3`
- `AI_EMBEDDING_GATEWAY_API_KEY=...` (used by `POST /v1/embeddings` OpenAI-compatible gateway)
- `AI_EMBEDDING_HF_ENDPOINT=https://hf-mirror.com`
- `CHROMA_ANONYMIZED_TELEMETRY=false`
- `KNOWLEDGE_RETRIEVER=chroma` (default/safe)
- `LIGHTRAG_BASE_URL=http://127.0.0.1:9621` for local process, or `http://lightrag:9621` on Docker Compose internal network
- `LIGHTRAG_API_KEY=` (empty if not required, otherwise use your server token)
- `LIGHTRAG_TIMEOUT_SECONDS=60`
- `LIGHTRAG_QUERY_MODE=hybrid`

LightRAG container reads `deploy/lightrag.env` (not committed):

1. Create it with:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup_lightrag_env.ps1
   ```
   Use `-Force` only when you need to overwrite an existing `deploy/lightrag.env`.
2. Keep:
   - `EMBEDDING_BINDING=openai`
   - `EMBEDDING_BINDING_HOST=http://ai-service:9000/v1`
   - `EMBEDDING_MODEL=BAAI/bge-m3`
   - `EMBEDDING_DIM=1024`
   - `EMBEDDING_SEND_DIM=false`
   - `EMBEDDING_USE_BASE64=false`
3. Set `EMBEDDING_BINDING_API_KEY` to the same value as `AI_EMBEDDING_GATEWAY_API_KEY`.

Retriever mode meanings:

- `chroma`: current stable behavior
- `lightrag`: requires healthy LightRAG Server; no fallback
- `lightrag_with_chroma_fallback`: recommended trial mode

Rollback:

1. Set `KNOWLEDGE_RETRIEVER=chroma`
2. Restart `ai-service`

## Manual Deployment

Frontend:

```bash
cd frontend
npm ci
npm run build
```

Backend:

```bash
cd back
mvn -DskipTests package
java -jar target/*.jar --spring.profiles.active=prod
```

AI service:

```bash
cd ai-service
python -m pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com
python app/ingest/sync_job.py
python -m uvicorn app.main:app --host 0.0.0.0 --port 9000
```

Knowledge base admin usage after services are up:

1. Open `/admin/kb`
2. Upload or create a document
3. Run chunk preview
4. Run indexing
5. Ask a customer-service question
6. Check hit logs in the same admin page

MySQL:

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS web DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -uroot -p --default-character-set=utf8mb4 web < back/sql/init_db.sql
```

Neo4j:

```bash
docker run -d --name neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/YOUR_PASSWORD neo4j:5-community
```

## Memory Estimate

This project does not run the Qwen 35B model locally. The LLM is remote through Xunfei Coding Plan, so server memory mainly comes from Java, MySQL, Neo4j, Chroma, and local embedding.

- Minimum single-server memory: 8 GB.
- Recommended single-server memory: 12-16 GB.
- Recommended if you expect frequent re-indexing, larger doc batches, or concurrent traffic: 16 GB.
- MySQL: about 0.6-1.2 GB for this project size.
- Spring backend: about 0.4-0.9 GB.
- Nginx frontend: about 0.05-0.1 GB.
- AI FastAPI service: about 0.3-0.8 GB before embedding warm-up.
- BGE-M3 local embedding on CPU: about 2-6 GB during warm-up/indexing depending cache and batch size.
- Neo4j: about 1.5-3 GB for a small graph with safe headroom.

Practical sizing:

- `8 GB`: can run, but indexing and Neo4j warm-up headroom is tight
- `12 GB`: usable baseline for one machine
- `16 GB`: safer target if you want local embedding plus Neo4j on the same host

For low-memory servers, keep Xunfei remote LLM unchanged and consider one of these first:

- move Neo4j to another host
- switch embedding to a smaller local BGE variant
- switch embedding to a cloud embedding API

## Local Development

Windows one-command startup:

```powershell
.\start_all.ps1 -Mode dev -InstallAiDeps -SeedAiKb
```

Linux/macOS one-command startup:

```bash
./start_all.sh dev --install-ai-deps --seed-ai-kb
```

After the first run, skip the dependency and seed flags unless requirements or knowledge files changed.
