# AI Service Runbook

This service uses Xunfei Coding Plan for the LLM and a local BGE embedding model for retrieval.

## Local First Run

1. Confirm `deploy/ai-service.env` contains `AI_LLM_API_KEY`, `AI_LLM_BASE_URL`, and `AI_LLM_MODEL=astron-code-latest`.
2. Install dependencies with the China mirror:
   ```powershell
   cd ai-service
   python -m pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com
   ```
3. Seed the local Chroma knowledge base:
   ```powershell
   python app/ingest/sync_job.py
   ```
   This step only seeds the built-in demo documents. The new admin workflow can also create and index documents from the browser at `http://127.0.0.1:5173/admin/kb`.
4. Start the AI service:
   ```powershell
   python -m uvicorn app.main:app --host 127.0.0.1 --port 9000
   ```
5. Verify:
   - `http://127.0.0.1:9000/health`
   - `POST http://127.0.0.1:9000/internal/index`
   - backend `POST /api/v1/customer-service/chat`

## Knowledge Base Admin Flow

After `back`, `frontend`, `ai-service`, MySQL, and Neo4j are up:

1. Open `http://127.0.0.1:5173/admin/kb`
2. Create a manual document or upload `txt` / `md` / `docx`
3. Trigger chunk preview
4. Trigger indexing
5. Ask a question through customer service
6. Re-open the document and inspect hit logs

Backend persistence:

- document metadata, chunks, index records, and hit logs: MySQL
- uploaded source files: `back/storage/kb/<documentId>/`
- vector data: `ai-service/data/chroma`

## One Command Local Startup

Run this from the project root on Windows:

```powershell
.\start_all.ps1 -Mode dev -InstallAiDeps -SeedAiKb
```

After the first run, omit `-InstallAiDeps` unless dependencies changed and omit `-SeedAiKb` unless knowledge documents changed.

## Embedding Download

The default embedding model is `BAAI/bge-m3`. The config sets `HF_ENDPOINT=https://hf-mirror.com` and caches model files under `ai-service/data/huggingface` for better mainland China download reliability.

If the automatic download is still slow, pre-download the model manually and point the service to the local folder:

```powershell
set HF_ENDPOINT=https://hf-mirror.com
huggingface-cli download BAAI/bge-m3 --local-dir data/models/bge-m3
set AI_EMBEDDING_MODEL=./data/models/bge-m3
```

`BGE-M3` does not need a separate daemon. It is loaded inside the FastAPI process when the first embedding request arrives. After that, its memory stays resident until the `ai-service` process restarts.

## Production

Production compose reads `deploy/ai-service.env` directly. The `ai-service-data` Docker volume persists Chroma data and embedding cache across container rebuilds.

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Minimum memory is about 8 GB for a small single-server deployment without local LLM inference. Recommended memory is 12-16 GB because BGE-M3 CPU embedding and Neo4j can spike during warm-up and indexing.

For LightRAG embedding gateway mode, set:

- `AI_EMBEDDING_GATEWAY_API_KEY=...` in `deploy/ai-service.env`
- keep this value identical to `EMBEDDING_BINDING_API_KEY` in `deploy/lightrag.env`

## Retrieval Mode Switch (Chroma / LightRAG)

Set these variables in `deploy/ai-service.env` (or start from `deploy/ai-service.env.example`):

```bash
KNOWLEDGE_RETRIEVER=chroma
LIGHTRAG_BASE_URL=http://127.0.0.1:9621
LIGHTRAG_API_KEY=
LIGHTRAG_TIMEOUT_SECONDS=60
LIGHTRAG_QUERY_MODE=hybrid
```

`LIGHTRAG_BASE_URL` values:

- Local process on the same host: `http://127.0.0.1:9621`
- Docker Compose internal network: `http://lightrag:9621`

LightRAG runtime env file:

- `docker-compose.prod.yml` expects `deploy/lightrag.env`
- generate it from template:
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup_lightrag_env.ps1
  ```
  add `-Force` only when intentionally replacing an existing local file
- keep embedding config aligned to ai-service gateway:
  - `EMBEDDING_BINDING=openai`
  - `EMBEDDING_BINDING_HOST=http://ai-service:9000/v1`
  - `EMBEDDING_USE_BASE64=false`

Retriever modes:

- `chroma`: current stable behavior (default/safe)
- `lightrag`: LightRAG-only mode, requires LightRAG Server healthy, no fallback path
- `lightrag_with_chroma_fallback`: recommended trial mode while validating LightRAG availability

Rollback:

1. Set `KNOWLEDGE_RETRIEVER=chroma`
2. Restart `ai-service`
