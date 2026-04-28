# Contributing

Thanks for contributing to ProjectKu Web.

## Before You Start

- Read `README.md`
- Read `docs/deployment.md` if your change touches runtime or deployment
- Keep changes scoped; avoid unrelated refactors

## Local Setup

Windows PowerShell:

```powershell
.\start_all.ps1 -Mode dev -InstallAiDeps -SeedAiKb
```

Linux / macOS:

```bash
./start_all.sh dev --install-ai-deps --seed-ai-kb
```

Minimum local requirements:

- Java 17
- Node.js 18+
- Python 3.10+
- MySQL 8

## Branch and Commit Style

- Use short focused branches
- Prefer small commits with clear intent
- Follow simple commit prefixes when possible:
  - `feat:`
  - `fix:`
  - `docs:`
  - `refactor:`
  - `chore:`
  - `test:`

Examples:

```text
feat: add coupon threshold validation
fix: avoid cart encoding regression
docs: clarify production deployment flow
```

## Change Guidelines

- Match existing stack and file layout
- Do not commit secrets or local env files
- Do not check in generated caches, logs, or node_modules
- If you touch shared behavior, add or update tests
- If you change deployment behavior, update both `deploy/README.md` and `docs/deployment.md`

## Pull Request Checklist

- Explain what changed
- Explain why it changed
- List manual verification or test commands used
- Include screenshots only when UI changes are relevant
- Note any follow-up work that remains out of scope

## Areas That Need Extra Care

- AI customer-service prompts and retrieval behavior
- Inventory, order, payment, and wallet flows
- Encoding and text rendering in frontend views
- Production deployment env templates
