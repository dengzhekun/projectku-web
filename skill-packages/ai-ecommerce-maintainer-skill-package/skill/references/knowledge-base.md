# Knowledge Base and RAG

Use this reference for LightRAG/RAG maintenance, indexing, content governance, and traceability.

## Content Governance

Good retrieval starts with good documents. Structure KB documents with:

- title,
- category,
- tags,
- business scope,
- version,
- effective date,
- source owner,
- concise sections,
- examples and edge cases.

Recommended ecommerce categories:

- product-shopping-guide,
- after-sales-policy,
- refund-payment-rules,
- coupon-rules,
- logistics-rules,
- account-wallet-rules,
- platform-faq.

## Indexing Workflow

For admin-managed documents:

1. Save source document and metadata in the backend database.
2. Chunk/preview content.
3. Send chunks or full document envelope to AI service.
4. AI service indexes into active RAG runtime.
5. Store index record and status.
6. Reindex should delete or supersede the previous logical document version.
7. Delete should remove backend metadata/files and retrieval runtime data in a safe order.

## LightRAG Source Tracing

LightRAG may return answer-level citations rather than exact chunk ids. That is acceptable for answering, but weaker for admin hit logs.

Preferred trace fields:

- question,
- route,
- normalized query,
- retrieval mode,
- candidate document ids/titles,
- citation text or source id,
- chunk id when available,
- confidence/fallback reason,
- final answer,
- latency and error details.

If chunk-level citation is unavailable:

- keep the answer if grounded;
- mark attribution as degraded;
- do not pretend a specific chunk was hit.

## Miss Handling

When a user question cannot be answered:

1. record the miss;
2. classify whether it needs a product tool, business API, or KB content;
3. generate a draft KB entry for rule/policy misses;
4. avoid creating KB entries for volatile product price/stock facts.

## Health Checks

For a RAG-enabled system, verify:

- AI service health;
- embedding gateway/backend health;
- RAG server health;
- index endpoint succeeds;
- query endpoint returns grounded content;
- backend admin sync status reflects failures;
- chat regression still passes.

## Common Failure Modes

- documents are saved in backend but not indexed;
- indexing uses old collection/runtime;
- embedding backend is down while RAG health looks normal;
- duplicate reindex leaves stale content;
- prompt routes policy questions to login-only business tools;
- hit logs are empty because only answer-level citation is available.
