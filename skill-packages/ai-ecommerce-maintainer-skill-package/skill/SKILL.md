---
name: ai-ecommerce-maintainer
description: Maintain, improve, test, and safely deploy AI-enabled ecommerce platforms. Use when Codex is asked to work on ecommerce projects with storefronts, admin panels, products/SKUs, carts, orders, payments, wallet/balance, coupons, inventory, reviews, after-sales, AI customer service, RAG/LightRAG knowledge bases, realtime product lookup tools, Docker/server deployment, GitHub releases, Playwright/k6/JMeter testing, or ProjectKu-like second-development projects.
---

# AI Ecommerce Maintainer

Use this skill to take over an AI ecommerce codebase without rediscovering the same lessons every time. It applies to ProjectKu-like projects and to similar ecommerce systems built with Vue/React, Spring Boot/Node/Python, FastAPI AI services, MySQL/PostgreSQL, Docker, and RAG/LightRAG.

## First Response Workflow

1. Identify the active project root before editing. Do not assume the current shell directory is the real repo.
2. Read existing handoff docs first if present: `README.md`, `HANDOFF.md`, `AI_HANDOFF.md`, `docs/`, `deploy/`.
3. Run a cheap status check: git status, recent commits, top-level directories, known start scripts.
4. Classify the user's request:
   - architecture or planning,
   - frontend/backend feature work,
   - AI customer service or RAG,
   - knowledge-base content/indexing,
   - testing/performance,
   - local packaging,
   - server deployment.
5. Load only the reference file needed for that class of work.
6. Before claiming success, run the smallest verification that proves the changed behavior.

## Reference Selection

- Project discovery and code navigation: read `references/architecture-discovery.md`.
- AI customer service, agent routing, product tools, prompt behavior: read `references/ai-customer-service.md`.
- RAG/LightRAG, KB documents, indexing, hit logs, source tracing: read `references/knowledge-base.md`.
- Ecommerce business correctness: read `references/ecommerce-core.md`.
- Server, Docker, domain, deployment, rollback: read `references/deployment-safety.md`.
- Test strategy, smoke scripts, performance testing: read `references/testing-verification.md`.
- ProjectKu-specific facts and known traps: read `references/projectku-case-study.md`.

## Core Principles

- Use realtime tools for volatile data: product price, stock, SKU, order status, wallet balance, coupon ownership, payment state.
- Use knowledge base retrieval for stable rules: after-sales policy, logistics rules, coupon rules, refund policy, shopping guide.
- Ask a clarifying question when confidence is low. Do not invent product versions, memory sizes, order state, refund promises, or coupon eligibility.
- Treat personal business queries as authenticated operations. Never trust or forward an unverified Bearer token.
- Cart operations should not reduce stock. Order/payment flows must prevent overselling with atomic checks.
- Deployment is a separate explicit step. Local/GitHub changes do not mean the server is updated.
- Back up before server changes. Do not delete Docker volumes or stop unrelated services.

## AI Customer-Service Decision Pattern

Route each user question through this order:

1. Does it ask about a specific user's orders, wallet, coupons, address, payment, or after-sales application? Require a verified auth token, then call backend business APIs.
2. Does it ask for product price, stock, SKU, version, model, or product list? Call realtime product APIs.
3. Does it ask about policy/rules/how-to content? Query RAG/LightRAG.
4. Is the product term ambiguous? Clarify. Example: "apple" can be fruit, but "iPhone 15" or "苹果15" is likely a phone.
5. If no reliable answer is found, say the system cannot determine it and ask for the missing field.

## Knowledge-Base Improvement Pattern

For every KB/RAG task:

1. Check whether the issue is content, indexing, retrieval, prompt/routing, or citation logging.
2. Verify that real documents are indexed into the active runtime.
3. Keep business documents structured by category, tags, scope, version, and source.
4. Log route, query, candidate documents, answer source, fallback reason, and low-confidence cases.
5. Turn repeated misses into candidate knowledge-base drafts.

## Deployment Safety Rules

- Never deploy because a commit exists. Deploy only after the user asks to update the server.
- Before deployment, identify compose project, containers, volumes, ports, and unrelated services.
- Back up code/config before replacing files.
- Restart the minimum service needed.
- Verify health, key APIs, AI customer-service regression, and fake-token behavior.
- Do not run `docker compose down -v`, delete database volumes, or reset a server directory without explicit approval.

## Reusable Verification Checklist

Use the subset that matches the change:

- Frontend: install/build, text encoding check, Playwright smoke.
- Backend: unit tests, API smoke, auth/permission tests, order/payment/inventory tests.
- AI service: pytest, customer-service regression cases, RAG runtime health.
- KB: seed import, indexing result, query smoke, hit/miss logs.
- Performance: k6/JMeter against a running environment, with generated reports.
- Deployment: container status, backend health, auth smoke, AI chat smoke, rollback notes.

## Skill Iteration Rule

When a new durable lesson appears, update this skill rather than leaving it buried in chat:

- generic ecommerce lesson -> relevant `references/*.md`;
- ProjectKu-specific lesson -> `references/projectku-case-study.md`;
- fragile command sequence -> `references/testing-verification.md` or `references/deployment-safety.md`;
- AI customer-service behavior -> `references/ai-customer-service.md`.

Keep `SKILL.md` short. Put detailed, project-specific, or evolving knowledge in references.
