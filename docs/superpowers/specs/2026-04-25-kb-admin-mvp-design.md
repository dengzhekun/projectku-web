# Knowledge Base Admin MVP Design

**Date:** 2026-04-25

**Scope:** Add a knowledge base admin MVP to the existing e-commerce platform so administrators can create, upload, manage, chunk, index, and inspect customer-service knowledge documents.

## 1. Goal

Build a first usable knowledge base management workflow inside the existing `frontend` and `back` projects, while keeping `ai-service` focused on vector indexing and retrieval.

This MVP must allow:

- manual document creation
- file upload for `txt`, `md`, and `docx`
- category, status, and version management
- chunk preview before indexing
- manual vector indexing into Chroma
- viewing retrieval hit references

This MVP explicitly does not include:

- PDF support
- async job queue
- automatic retries
- automatic incremental sync
- retrieval evaluation dashboards
- feedback loop optimization
- full RBAC implementation

## 2. Product Decision Summary

### 2.1 Admin UI Placement

The admin UI is added into the existing `frontend` project instead of creating a separate admin frontend.

Reason:

- fastest path to delivery
- reuses existing Vue app, router, and API layer
- avoids duplicated login/session infrastructure

### 2.2 Document Source Scope

The MVP supports:

- manual text entry
- `txt`
- `md`
- `docx`

The MVP does not support `pdf`.

Reason:

- `txt/md/docx` are much more predictable for extraction and chunking
- PDF introduces OCR/layout noise and would significantly expand parsing complexity

### 2.3 Storage Strategy

Use a hybrid storage design:

- MySQL stores metadata, status, version, chunk records, index records, and hit logs
- disk stores original uploaded files

Reason:

- file retention is easy and cheap on disk
- operational metadata is easier to manage and query in MySQL
- future async jobs or re-indexing can reuse both layers cleanly

### 2.4 Architecture Split

- `frontend`: admin management UI
- `back`: document management, upload, parsing trigger, chunk preview APIs, hit log query APIs
- `ai-service`: embedding, Chroma indexing, retrieval, and hit reporting

Reason:

- keeps Java backend responsible for business/admin operations
- keeps Python AI service responsible for vector operations only
- makes later replacement of local embedding with cloud embedding easier

## 3. High-Level Architecture

### 3.1 Components

#### Frontend

Add a knowledge base admin page inside the current Vue app.

Main UI areas:

- document list
- create/upload form
- document detail
- chunk preview
- index records
- hit reference view

#### Backend (`back`)

Add a new knowledge base module:

- controller layer for admin APIs
- service layer for orchestration
- mapper layer for MySQL persistence
- file storage service for uploaded source files
- chunking service for converting extracted text into previewable chunks

#### AI Service (`ai-service`)

Add indexing-oriented APIs that accept chunk payloads from the Java backend and write vectors into Chroma.

Also add a hit-reporting path so retrieval usage can be recorded back into MySQL.

## 4. Data Model

### 4.1 `kb_document`

Purpose: source-of-truth record for each knowledge document version currently managed by admins.

Suggested fields:

- `id`
- `title`
- `category`
- `source_type`
- `status`
- `version`
- `storage_path`
- `content_text`
- `created_by`
- `created_at`
- `updated_at`

Notes:

- `source_type` distinguishes manual entry vs uploaded file
- `content_text` stores parsed plain text for downstream chunking
- `storage_path` points to the original file on disk when applicable

### 4.2 `kb_chunk`

Purpose: stores chunk preview records generated from a document.

Suggested fields:

- `id`
- `document_id`
- `chunk_index`
- `content`
- `char_count`
- `status`
- `created_at`

Notes:

- first version uses character count instead of token count for simplicity
- each chunk is persisted so the admin page can preview exact chunk boundaries

### 4.3 `kb_index_record`

Purpose: stores every manual indexing attempt and its result.

Suggested fields:

- `id`
- `document_id`
- `version`
- `embedding_provider`
- `vector_collection`
- `indexed_chunk_count`
- `status`
- `error_message`
- `created_at`

Notes:

- records success/failure for troubleshooting
- makes later rebuild history visible without needing async jobs first

### 4.4 `kb_hit_log`

Purpose: records which chunks were actually retrieved during customer-service chat.

Suggested fields:

- `id`
- `document_id`
- `chunk_id`
- `query_text`
- `conversation_id`
- `hit_time`

Notes:

- first version stores only retrieval hits, not full evaluation metrics
- enough to let admins identify useful vs stale content

## 5. File Storage Layout

Suggested disk layout under the backend project runtime storage directory:

- `storage/kb/{document_id}/original.ext`

Rationale:

- stable per-document directory
- easy overwrite/version replacement in MVP
- easy cleanup when deleting a document

## 6. Admin UI Design

### 6.1 Single Route MVP

Add one admin route/page to the existing frontend, for example:

- `/admin/kb`

This page contains the full MVP workflow instead of splitting into a separate frontend app.

### 6.2 UI Sections

#### Document List

Features:

- filter by category
- filter by status
- search by title
- row actions: view, edit, delete, preview chunks, index

#### Create / Upload

Features:

- manual text entry
- file upload for `txt`, `md`, `docx`
- title
- category
- optional remarks

#### Document Detail

Features:

- source content preview
- version display
- status display
- latest indexing records
- buttons for re-chunk and re-index

#### Chunk Preview

Features:

- displays chunk order and content
- shows chunk length
- lets admins inspect if chunking quality is acceptable before indexing

#### Hit Reference View

Features:

- shows when the document/chunks were retrieved
- surfaces the query text and conversation id

## 7. Backend API Design

Suggested endpoints:

- `GET /api/v1/kb/documents`
- `POST /api/v1/kb/documents`
- `POST /api/v1/kb/documents/upload`
- `GET /api/v1/kb/documents/{id}`
- `PUT /api/v1/kb/documents/{id}`
- `DELETE /api/v1/kb/documents/{id}`
- `POST /api/v1/kb/documents/{id}/chunk`
- `GET /api/v1/kb/documents/{id}/chunks`
- `POST /api/v1/kb/documents/{id}/index`
- `GET /api/v1/kb/documents/{id}/index-records`
- `GET /api/v1/kb/documents/{id}/hits`

API behavior:

- upload parses file and stores extracted text in `kb_document`
- chunk endpoint regenerates chunk records for the current document version
- index endpoint sends chunk payload to `ai-service`
- hit endpoint reads `kb_hit_log`

## 8. AI Service API Changes

### 8.1 Indexing API

Add an internal indexing endpoint that accepts:

- `document_id`
- `version`
- `chunks[]`
- optional metadata such as `category`, `title`

Behavior:

- embed each chunk
- upsert vectors into Chroma
- use stable ids derived from `document_id + version + chunk_index`

### 8.2 Retrieval Hit Reporting

During chat retrieval, when Chroma returns matched chunks:

- include `document_id` and `chunk_id` in chunk metadata
- report matched results back to the Java backend
- backend persists hit logs into MySQL

This is enough for MVP observability without building a full analytics pipeline.

## 9. Data Flow

### 9.1 Document Creation Flow

1. Admin creates a manual document or uploads a file.
2. Backend parses the source into normalized plain text.
3. Backend stores metadata in `kb_document`.
4. Backend stores original file on disk if uploaded.
5. Document status becomes `draft` or `parsed`.

### 9.2 Chunking Flow

1. Admin clicks chunk preview.
2. Backend reads `kb_document.content_text`.
3. Backend splits text into chunks.
4. Backend stores chunk rows in `kb_chunk`.
5. Document status becomes `chunked`.

### 9.3 Indexing Flow

1. Admin clicks index.
2. Backend loads current chunks from `kb_chunk`.
3. Backend calls `ai-service`.
4. AI service embeds chunks and writes them into Chroma.
5. Backend stores result in `kb_index_record`.
6. Document status becomes `indexed` or `failed`.

### 9.4 Retrieval Hit Logging Flow

1. Customer asks a question.
2. AI service retrieves matching chunks from Chroma.
3. AI service sends hit metadata back to backend.
4. Backend stores hit logs in `kb_hit_log`.
5. Admin later reviews which chunks were actually used.

### 9.5 Rebuild Flow

1. Admin edits the document.
2. Admin re-runs chunking.
3. Admin re-runs indexing.
4. AI service overwrites vectors for the current document/version ids.

MVP rule:

- overwrite current active vectors instead of building a complex multi-version coexistence strategy

## 10. Chunking Strategy

The first version uses a simple deterministic chunker:

- split by headings or blank lines first
- if a block is too long, split again by fixed character length
- preserve `chunk_index`

Why this is sufficient:

- easy to explain and debug
- easy to preview in UI
- predictable for customer-service knowledge documents

What is intentionally deferred:

- semantic chunk merging
- token-aware overlap tuning
- model-specific token counting

## 11. Status Model

Recommended document statuses:

- `draft`
- `parsed`
- `chunked`
- `indexed`
- `failed`

Recommended index record statuses:

- `success`
- `failed`

## 12. Error Handling

### 12.1 Parse Errors

- keep original upload metadata
- mark document `failed`
- store error summary for admin display

### 12.2 Chunking Errors

- keep existing source text intact
- do not delete original document
- return error message to UI

### 12.3 Indexing Errors

- write failed `kb_index_record`
- preserve chunk records
- allow manual retry through UI

MVP rule:

- manual retry only
- no automatic retry queue

## 13. Security / Access

This MVP does not implement full RBAC.

Interim approach:

- expose admin route in the existing frontend
- protect visibility with a simple admin-only entry mechanism already available in the project, or a temporary front-end-only gate if no server-side role model exists yet

Important note:

- the long-term version must move admin authorization to server-side enforcement

## 14. Testing Strategy

### 14.1 Backend

Add tests for:

- document CRUD
- file upload parsing
- chunk generation
- indexing state transitions
- hit log query endpoints

### 14.2 AI Service

Add tests for:

- chunk indexing endpoint
- Chroma upsert overwrite behavior
- retrieval hit reporting payload

### 14.3 Frontend

For MVP, prioritize manual integration verification over adding a heavy new test framework.

Manual validation checklist:

1. create a document
2. upload a supported file
3. preview chunks
4. trigger indexing
5. ask a customer-service question that should hit the document
6. confirm hit references appear in admin UI

## 15. Delivery Sequence

Recommended implementation order:

1. MySQL schema and backend storage paths
2. backend document CRUD and upload parsing
3. backend chunk generation and preview APIs
4. AI service chunk indexing API
5. retrieval hit logging path
6. frontend admin page
7. end-to-end validation and bug fixing

## 16. Future Expansion Path

After MVP is stable, the next phase can add:

- PDF support
- async job queue
- retry policies
- cloud embedding switch support
- retrieval quality evaluation set
- hit-rate and recall dashboards
- feedback loop and stale content cleanup
- incremental rebuild strategy

## 17. Final Recommendation

The correct first move is to build the knowledge base admin MVP as a synchronous, manually operated workflow inside the existing frontend and backend.

This gives the project an immediately usable operational layer without prematurely adding queues, evaluation systems, or a separate admin platform.
