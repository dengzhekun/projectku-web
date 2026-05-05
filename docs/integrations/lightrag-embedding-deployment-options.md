# LightRAG Embedding Deployment Options

Date: 2026-05-05

This document records the current LightRAG embedding deployment choices for this project. It is meant for future handoff and migration when the embedding server is upgraded or replaced with a cloud embedding API.

Do not put real API keys in this document. Runtime secrets belong in server env files only.

## Current Production State

As of 2026-05-05, the active production LightRAG chain uses `BAAI/bge-small-zh-v1.5` embeddings with `512` dimensions. The index was rebuilt after this switch.

AI service runtime:

```env
AI_EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
AI_EMBEDDING_DIMENSIONS=512
AI_EMBEDDING_REMOTE_URL=http://127.0.0.1:9001/embed
AI_EMBEDDING_REMOTE_TIMEOUT_SECONDS=60
```

LightRAG runtime:

```env
EMBEDDING_BINDING=openai
EMBEDDING_BINDING_HOST=http://172.21.0.1:19000/v1
EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
EMBEDDING_DIM=512
```

Important implication:

- The indexed LightRAG data is built with `BAAI/bge-small-zh-v1.5`.
- Query-time embeddings must stay on `BAAI/bge-small-zh-v1.5`.
- Do not switch only the embedding service to a different model without rebuilding the LightRAG index.

## Previous BGE-M3 Production State

This was the previous production target before the 2026-05-05 small-model cutover. Keep it as a higher-quality path for a larger embedding server or cloud embedding API, not as the current running state.

AI service runtime:

```env
AI_EMBEDDING_PROVIDER=remote_http
AI_EMBEDDING_MODEL=BAAI/bge-m3
AI_EMBEDDING_DIMENSIONS=1024
AI_EMBEDDING_REMOTE_URL=http://127.0.0.1:9001/embed
```

LightRAG runtime:

```env
EMBEDDING_BINDING=openai
EMBEDDING_BINDING_HOST=http://172.21.0.1:19000/v1
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIM=1024
```

Important implication:

- The indexed LightRAG data is built with `BAAI/bge-m3`.
- Query-time embeddings must stay on `BAAI/bge-m3`.
- Do not switch only the embedding service to a different model without rebuilding the LightRAG index.

## Why The Aliyun 1.6G Server Is Not Current Mainline

The Aliyun embedding server can run an embedding API, but its current hardware is too small for BGE-M3 as a real-time customer-service path.

Observed BGE-M3 behavior on the small Aliyun server:

- Service can start.
- Health check works.
- A single uncached embedding request can take about 20 seconds after warm-up.
- A small batch can take much longer.
- The server previously had an OOM kill event.
- A SQLite cache was added, so repeated identical text can return quickly, but new customer questions still need real inference.

This makes it acceptable as:

- a backup endpoint,
- a slow background indexing endpoint,
- a temporary test endpoint,
- a cache-assisted fallback.

It is not a good default for:

- real-time AI customer-service queries,
- heavy LightRAG reindexing,
- multiple users asking new questions concurrently.

## Preserved BGE-M3 Mainline Plan

Keep this plan when the embedding machine has enough memory or when using a cloud embedding API with BGE-M3-compatible output.

Recommended resources:

- Minimum practical self-hosted memory: 4 GB
- Better self-hosted memory: 8 GB
- Recommended for smoother indexing: 8 GB or more

Model and dimensions:

```env
AI_EMBEDDING_MODEL=BAAI/bge-m3
AI_EMBEDDING_DIMENSIONS=1024
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIM=1024
```

Benefits:

- Better Chinese semantic retrieval than small models.
- Good fit for ecommerce policy, logistics, refund, coupon, and shopping-guide documents.
- The project was previously aligned with this path. If returning to BGE-M3, restore/rebuild the BGE-M3 LightRAG index and rerun regression tests.

Costs:

- More memory.
- Slower on small CPU-only machines.
- Reindexing can be slow because LightRAG also performs entity/relation extraction.

## Small Aliyun Model Plan

The original standalone embedding installer defaulted to:

```env
MODEL_NAME=BAAI/bge-small-zh-v1.5
```

This is better suited to a 1.6G server. It is compatible only with a LightRAG index rebuilt at `512` dimensions, not with any preserved BGE-M3 `1024`-dimension index.

Typical differences:

- `BAAI/bge-small-zh-v1.5` is lighter.
- Its embedding dimension is different from BGE-M3.
- Retrieval quality may be lower, but latency and memory use are much better on small servers.

Use this plan only if the priority is to move embedding off the main server and accept a smaller model.

Required migration steps:

1. Back up LightRAG data and backend KB metadata.
2. Change the embedding service model to the small model.
3. Change AI service embedding model and dimension.
4. Change LightRAG `EMBEDDING_MODEL` and `EMBEDDING_DIM`.
5. Clear or isolate the old LightRAG vector storage.
6. Reindex all knowledge-base documents.
7. Run customer-service regression questions.
8. Keep the old BGE-M3 backup until the new retrieval quality is accepted.

Never mix BGE-M3-indexed data with small-model query embeddings.

## Cloud Embedding API Plan

Use this when the project needs stability without maintaining a local embedding model server.

Expected properties:

- OpenAI-compatible embeddings endpoint, or a small adapter in `ai-service`.
- Stable model name.
- Known embedding dimension.
- Good Chinese retrieval quality.
- Reasonable latency from the production server region.

Migration is similar to the small-model plan:

1. Add the provider endpoint and key to runtime env.
2. Confirm `/v1/embeddings` or adapter output dimension.
3. Update `AI_EMBEDDING_MODEL` and `AI_EMBEDDING_DIMENSIONS`.
4. Update LightRAG `EMBEDDING_MODEL` and `EMBEDDING_DIM`.
5. Rebuild LightRAG index.
6. Run regression questions and inspect hit logs.

Cloud embedding is usually the cleanest option if the project must stay on a 4G ecommerce server.

## Safe Switch Checklist

Before switching:

```bash
systemctl is-active evanshine-ai-service.service
systemctl is-active embedding-local.service
systemctl is-active embedding-tunnel.service
curl -sS http://127.0.0.1:9001/health
curl -sS http://127.0.0.1:19001/health
```

Back up:

```bash
mkdir -p /opt/evanshine-shop/backups/embedding-switch-$(date +%Y%m%d%H%M%S)
cp -a /opt/evanshine-shop/ai-service.runtime.env /opt/evanshine-shop/backups/embedding-switch-$(date +%Y%m%d%H%M%S)/
tar -C /opt/evanshine-shop -czf /opt/evanshine-shop/backups/lightrag-data-before-embedding-switch.tgz lightrag-data
```

After switching:

```bash
systemctl restart evanshine-ai-service.service
curl -sS http://172.21.0.1:19000/health
```

Then run fixed customer-service smoke questions:

- `苹果多少钱`
- `苹果15多少钱`
- `物流一直不动怎么办？`
- `优惠券没到门槛为什么不能用？`
- `售后质量问题退回运费谁承担？`

Expected behavior:

- `苹果多少钱` asks for clarification.
- `苹果15多少钱` uses real product data.
- Logistics, coupon, and after-sales questions hit LightRAG KB documents.
- Backend hit logs should record chunk-level hits when available.

## Rollback Rule

If knowledge questions time out, return empty answers, or record `Knowledge retrieval is temporarily unavailable`, rollback the AI runtime embedding URL first.

Known stable production fallback:

```env
AI_EMBEDDING_REMOTE_URL=http://127.0.0.1:9001/embed
AI_EMBEDDING_MODEL=BAAI/bge-m3
AI_EMBEDDING_DIMENSIONS=1024
```

Restart:

```bash
systemctl restart evanshine-ai-service.service
```

Then rerun the smoke questions.

## Decision Summary

Use BGE-M3 when retrieval quality matters and the server has enough memory.

Use the small Aliyun model only when low memory is the main constraint and a full LightRAG reindex is acceptable.

Use a cloud embedding API when operational stability matters more than self-hosting.

Current recommendation:

- Keep current production on `BAAI/bge-small-zh-v1.5` / `512` dimensions.
- Keep the Aliyun service as a documented backup/test path.
- Keep the BGE-M3 plan for a future larger embedding server or cloud embedding API migration.
- Whenever the model or dimension changes, migrate with a full LightRAG reindex and regression test.

## 2026-05-05 Small-Model Cutover Record

The project was switched from BGE-M3 to `BAAI/bge-small-zh-v1.5` on 2026-05-05.

Final production state after verification:

```env
AI_EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
AI_EMBEDDING_DIMENSIONS=512
AI_EMBEDDING_REMOTE_URL=http://127.0.0.1:9001/embed
AI_EMBEDDING_REMOTE_TIMEOUT_SECONDS=60

EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
EMBEDDING_DIM=512
EMBEDDING_BINDING_HOST=http://172.21.0.1:19000/v1
```

Important correction to the earlier small-Aliyun plan:

- Aliyun 1.6G can run `bge-small-zh-v1.5` for single requests.
- LightRAG indexing sends many concurrent embedding requests for chunks, entities, and relationships.
- Under that indexing load, the 1.6G Aliyun server became unstable and requests timed out.
- Therefore the final mainline uses the 核云 local embedding service with `bge-small-zh-v1.5`.
- Aliyun remains a backup/test endpoint, not the active production endpoint.

Observed local small-model state:

- Local embedding service: `embedding-local.service`
- URL: `http://127.0.0.1:9001/embed`
- Model: `BAAI/bge-small-zh-v1.5`
- Dimension: `512`
- LightRAG documents status after rebuild: `processed=134`
- Backend KB sync health: `totalDocuments=5`, `indexedDocuments=5`, no failed/stale/missing documents

Backups created during this migration:

```text
/opt/evanshine-shop/backups/switch-to-bge-small-20260505083042
/opt/evanshine-shop/backups/bge-small-cutover-20260505083342
/opt/evanshine-shop/backups/local-bge-small-embedding-20260505085606
/opt/evanshine-shop/backups/bge-small-reindex-retry-20260505085822
/opt/evanshine-shop/backups/ai-clarification-route-fix-20260505092415
```

Aliyun backup path:

```text
/root/embedding-before-bge-small-20260505083042
```

Verification run after migration:

- `苹果多少钱`: returns clarification, route/sourceType `clarification`
- `苹果15多少钱？`: product route, uses realtime product data, returns iPhone 15 128G price `5999`
- `物流一直不动怎么办？`: knowledge route, hits `KB Logistics Rules`
- `优惠券没到门槛为什么不能用？`: knowledge route, hits `KB Coupon Rules`
- `售后质量问题退回运费谁承担？`: knowledge route, hits `KB After Sales Policy`

Local AI service tests after the clarification route change:

```text
118 passed
```

Rollback notes:

- To return to BGE-M3, restore the BGE-M3 env values and LightRAG backup, or rebuild LightRAG with BGE-M3.
- Do not point a 512-dimensional small-model query service at a 1024-dimensional BGE-M3 index.
- Do not point a BGE-M3 query service at the 512-dimensional small-model index.
