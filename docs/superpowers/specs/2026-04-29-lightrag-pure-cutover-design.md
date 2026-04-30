# LightRAG Pure Cutover Design

## Goal

Make LightRAG the only knowledge retrieval runtime for AI customer service and KB indexing. Remove Chroma from the active query/index/delete path so production retrieval, attribution, and reindex behavior rely on one backend only.

## Why This Change

The current project is in a mixed state:

- `KNOWLEDGE_RETRIEVER=lightrag_with_chroma_fallback` is enabled in local deployment config.
- LightRAG already handles retrieval and KB indexing in many paths.
- Chroma is still used as a fallback and as a source-tracing crutch for some attribution cases.

That mixed mode helped de-risk adoption, but it now creates operational ambiguity:

- two retrieval backends can diverge silently,
- delete and rebuild semantics are inconsistent,
- some hit-log attribution still depends on Chroma being present,
- rollback behavior is implicit rather than intentional.

The target of this cutover is not "LightRAG trial mode". The target is a single authoritative retrieval backend for runtime behavior.

## External Constraint

This cutover is only worth doing if document deletion is actually possible on the LightRAG side. Current upstream evidence indicates that document deletion exists in modern LightRAG server/runtime and has had recent fixes:

- LightRAG repository: <https://github.com/HKUDS/LightRAG>
- Delete-by-doc-id issue: <https://github.com/HKUDS/LightRAG/issues/661>
- v1.2.3 release note mentioning `delete_by_doc_id` fix: <https://newreleases.io/project/github/HKUDS/LightRAG/release/v1.2.3>

Inference from those sources: a pure-LightRAG cutover is feasible, but this repository must explicitly wire the delete contract and verify it against the deployed server version.

## Scope

This cutover covers:

1. AI-service retrieval runtime
2. KB indexing bridge
3. KB document deletion bridge
4. Attribution and hit-log behavior
5. Seed/rebuild idempotency
6. Deployment configuration defaults
7. Removal of Chroma from active runtime configuration and logic

This cutover does not cover:

- redesigning LightRAG internals,
- changing business-tool routing for product/order/wallet tools,
- broader knowledge graph feature expansion.

## Current Problems To Eliminate

### 1. Runtime split-brain

`FallbackKnowledgeRetriever` can hide LightRAG failures by silently falling back to Chroma. That keeps the chat alive, but it also means:

- retrieval source of truth is unclear,
- write/delete failures can go unnoticed,
- repeated reindexing can produce inconsistent LightRAG and Chroma state.

### 2. Delete inconsistency

`LightRagRetriever.delete_document()` is currently a no-op. Backend document deletion also does not notify ai-service to remove corresponding vector/graph data.

### 3. Attribution dependency on Chroma

Some LightRAG answer-level responses currently rely on Chroma re-query to recover chunk-level hit logs. That is incompatible with a true Chroma-free runtime.

### 4. Rebuild duplication risk

Seed and rebuild paths are append-oriented from the LightRAG perspective. If the same logical document is reinserted without cleanup, old content may remain.

## Target Architecture

### Retrieval mode

The only supported runtime retriever mode becomes:

- `KNOWLEDGE_RETRIEVER=lightrag`

Removed from supported runtime behavior:

- `chroma`
- `lightrag_with_chroma_fallback`

Code cleanup may happen in the same round or immediately after cutover, but the operational requirement is that production no longer depends on Chroma being installed, populated, or queried.

### Index/delete contract

The authoritative lifecycle becomes:

1. backend chunks a KB document,
2. backend calls ai-service `/internal/index`,
3. ai-service deletes the existing LightRAG representation for that `documentId`,
4. ai-service inserts the new version into LightRAG,
5. chat queries LightRAG only.

For document deletion:

1. backend receives delete request,
2. backend calls ai-service delete endpoint for `documentId`,
3. ai-service deletes the LightRAG representation,
4. backend deletes DB rows/files.

This order avoids DB success followed by orphaned vector/graph content.

### Attribution model

For KB-admin-managed content, chunk metadata remains embedded in the ingested LightRAG text envelope:

- `document_id`
- `chunk_id`
- `chunk_index`
- `version`
- `title`
- `category`
- `source_id`
- `source_type`

Chat should derive citations and hit logs from LightRAG-returned sources directly.

If LightRAG returns answer-level content without chunk metadata:

- keep the answer,
- expose answer-level citation if available,
- record no chunk hit log,
- record a clear fallback/degraded attribution reason.

The key design decision is that pure-LightRAG mode does **not** re-query Chroma for source tracing.

## Data and Deletion Strategy

The delete path must use the official/available LightRAG document deletion capability, keyed by stable KB document identity.

Required repository behavior:

1. use deterministic document-scoped identifiers/tags in LightRAG inserts,
2. call LightRAG delete before reinsert,
3. verify delete result,
4. surface partial failure clearly instead of silently swallowing it.

If the deployed LightRAG version only supports specific delete semantics, this repository should adapt to that concrete API rather than simulating delete locally.

## Error Handling

### Indexing

- if LightRAG delete fails, indexing fails
- if LightRAG upsert fails, indexing fails
- backend records failed index record
- no hidden fallback write to Chroma

### Deletion

- if ai-service delete fails, backend document delete fails
- user should not be told the document is gone while retrieval data still exists

### Attribution

- malformed metadata must not crash chat
- malformed metadata may produce degraded citation/hit-log output
- degraded attribution must be explicit in logs

## Testing Strategy

The cutover is only complete if these cases are locked by tests:

1. `build_knowledge_retriever()` returns only LightRAG mode for supported runtime config
2. `/internal/index` calls delete then upsert on LightRAG path
3. backend `deleteDocument()` calls ai-service delete endpoint before DB/file cleanup
4. repeated reindex of the same KB document does not rely on Chroma and does not preserve stale versions
5. chat with valid LightRAG source metadata produces citations and chunk-level hit logs
6. chat with answer-level-only LightRAG output degrades safely without Chroma

## Deployment Result

After this cutover:

- `deploy/ai-service.env.example` defaults to `KNOWLEDGE_RETRIEVER=lightrag`
- production compose/runtime no longer assumes Chroma is needed
- operational rollback, if needed, is "disable LightRAG cutover by reverting this branch/deploy", not "query Chroma in secret"

## Success Criteria

This work is complete when all of the following are true:

1. AI customer service uses LightRAG as its only KB retriever
2. KB reindex and delete are consistent on the LightRAG side
3. No production chat path depends on Chroma for attribution or retrieval
4. Known degraded-attribution cases are explicit and tested
5. Deployment defaults reflect LightRAG-first production usage
