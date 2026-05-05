# AI Service Runbook

This service uses Xunfei Coding Plan for the LLM and LightRAG as the production retrieval runtime.

## Local First Run

1. Confirm `deploy/ai-service.env` contains `AI_LLM_API_KEY`, `AI_LLM_BASE_URL`, and `AI_LLM_MODEL=astron-code-latest`.
2. Install dependencies with the China mirror:
   ```powershell
   cd ai-service
   python -m pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com
   ```
3. Seed demo knowledge-base content:
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
- retrieval runtime storage: LightRAG-managed PostgreSQL/Neo4j (configured in `deploy/lightrag.env`)

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

Production compose reads `deploy/ai-service.env` directly. The `ai-service-data` Docker volume persists embedding cache across container rebuilds.

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Minimum memory is about 8 GB for a small single-server deployment without local LLM inference. Recommended memory is 12-16 GB because BGE-M3 CPU embedding and Neo4j can spike during warm-up and indexing.

For LightRAG embedding gateway mode, set:

- `AI_EMBEDDING_GATEWAY_API_KEY=...` in `deploy/ai-service.env`
- keep this value identical to `EMBEDDING_BINDING_API_KEY` in `deploy/lightrag.env`

## Retrieval Runtime (LightRAG only)

Set these variables in `deploy/ai-service.env` (or start from `deploy/ai-service.env.example`):

```bash
KNOWLEDGE_RETRIEVER=lightrag
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

Runtime verification:

1. Run `scripts/verify-lightrag-runtime.ps1`.
2. Verify `ai-service` embedding gateway (`/v1/embeddings`) is healthy.
3. If `AI_EMBEDDING_REMOTE_URL` uses local backend (`http://127.0.0.1:9001/embed`), verify port `9001` is healthy before query tests.

Important: LightRAG service endpoints may appear healthy even when embedding backend is unavailable. In that case indexing/query requests fail until embedding backend is restored.

## Onsite AI Customer-Service Regression

Run the onsite regression script after any of these changes:

- LightRAG rebuild, runtime cutover, or KB reindex
- customer-service routing or prompt changes
- seed knowledge-base content changes for after-sales, coupon, or logistics rules
- before a Windows portable package or GitHub release candidate is handed off

Prerequisites:

1. Backend is running and reachable at `http://127.0.0.1:8080/api` or an override URL.
2. Backend can successfully proxy `POST /api/v1/customer-service/chat`.
3. If you expect knowledge answers to pass, the active runtime already contains after-sales, coupon, and logistics knowledge.
4. Do not paste API keys into the command line. The regression script does not print secrets.

Default command from the repo root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-ai-cs-regression.ps1
```

Override backend URL or timeout when needed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-ai-cs-regression.ps1 `
  -BackendBaseUrl "http://127.0.0.1:8080/api" `
  -TimeoutSeconds 30
```

Behavior:

- The script sends built-in Chinese regression cases to backend `/v1/customer-service/chat`, including:
  - `苹果多少钱`（宽词澄清）
  - `苹果15多少钱`、`苹果15Pro多少钱`（商品型号查询）
  - `售后质量问题退回运费谁承担`（售后运费）
  - `优惠券没到门槛为什么不能用`（优惠券门槛）
  - `物流一直不动怎么办`（物流异常）
  - 未登录个人查询应提示登录
  - 伪造 token 访问个人订单应返回 `401`
- It prints `PASS` or `FAIL` for each case, then prints a final summary.
- Exit code is `0` only when all cases pass; any failed case returns exit code `1`.
- Before running cases, it checks the configured LightRAG docs endpoint unless `-SkipLightRagCheck` is passed.
- If every case fails without an HTTP status code, treat the backend chat endpoint as not reachable first.
- If product routing still works but one or more knowledge cases fail, treat that as a likely KB coverage or indexing problem first, not automatically an app code regression.
- If only after-sales / coupon / logistics cases fail and the script says LightRAG is not reachable, start or repair LightRAG before changing prompts or routing code.

Pass criteria:

- Product intent:
  - `苹果多少钱` must not be treated as a fully concrete iPhone model; answer should ask for clarification or specific model.
  - `苹果15` / `iPhone 15` variants should route to product query and return product-related answer.
- Knowledge intent:
  - after-sales / coupon / logistics cases should return knowledge-based guidance without forcing login.
- Auth security:
  - forged token request for personal order/wallet/coupon scope must fail with `401` (or explicit unauthorized/login hint).

Case details are documented in `docs/knowledge-base/ai-cs-regression-cases.md`.

## Stream Chat Observability

Both normal chat and stream chat should now persist the final AI reply metadata:

- `customer_service_log` records route, source type, confidence, fallback reason, and first citation source when available.
- `kb_hit_log` records chunk-level hits when `final.reply.hitLogs` contains numeric document and chunk ids.
- `kb_miss_log` records low-confidence or fallback replies.

The stream endpoint still forwards SSE events to the browser. The backend also reads the final SSE event internally so the admin panel can keep hit logs and missed-question drafts aligned with real customer-service usage.
