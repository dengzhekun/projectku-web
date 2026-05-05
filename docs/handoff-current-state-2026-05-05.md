# Current State Handoff

Date: 2026-05-05

This note is the short, current handoff for the local machine and the production AI customer-service runtime after the LightRAG small-model cutover.

## Use This Local Directory

Use `D:\web-main-clean` for further code reading, testing, and development.

Reasons:

- It is on the latest published project commit: `943ff33`.
- `ai-service` tests pass in this directory (`120 passed`).
- It reflects the current GitHub `origin/main` state.

Do not use these as the primary development directory:

- `D:\web-main`: old worktree on `68c32db`, kept only as a fallback copy.
- `D:\web-main-push`: temporary push worktree, kept only because it contains one local-only line-ending cleanup commit.

## GitHub State

Repository:

- `https://github.com/dengzhekun/projectku-web`

Current published `origin/main` commit:

- `943ff33 fix: align lightrag small embedding runtime`

There is one local-only commit not pushed yet:

- `eddc131 chore: normalize text file line endings`

This local-only commit only updates `.gitattributes` line-ending rules. It does not change ecommerce, AI, or LightRAG business behavior.

Local safety reference for that commit:

- tag: `backup/eddc131-20260505`

## Production AI / LightRAG Baseline

Production verification date:

- 2026-05-05

Current embedding baseline:

```env
AI_EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
AI_EMBEDDING_DIMENSIONS=512
AI_EMBEDDING_REMOTE_URL=http://127.0.0.1:9001/embed

EMBEDDING_MODEL=BAAI/bge-small-zh-v1.5
EMBEDDING_DIM=512
EMBEDDING_BINDING_HOST=http://172.21.0.1:19000/v1
```

Current production services verified active:

- `evanshine-ai-service.service`
- `embedding-local.service`
- `embedding-tunnel.service`

Known production AI endpoint shape:

- systemd starts `uvicorn` on `172.21.0.1:19000`
- it is not bound to local `127.0.0.1:9000` on the server

LightRAG status verified:

- documents `processed = 134`

## Verified Runtime Behavior

These production smoke checks passed after the cutover:

- `苹果多少钱` -> `clarification`
- `苹果15多少钱？` -> `product`
- `物流一直不动怎么办？` -> `knowledge`
- `优惠券没到门槛为什么不能用？` -> `knowledge`
- `售后质量问题退回运费谁承担？` -> `knowledge`

Regression expectation note:

- `scripts/run-ai-cs-regression.ps1` should assert `苹果多少钱` as `route/sourceType=clarification` and keep `苹果15多少钱` as `product`.

## Remaining Loose End

The only remaining repo-maintenance loose end is the local-only line-ending cleanup commit `eddc131`.

Why it exists:

- Windows keeps marking `README.md`, `docs/ai-service-runbook.md`, and `scripts/run-ai-cs-regression.ps1` as modified because of line-ending normalization noise.

Why it is not urgent:

- GitHub `origin/main` already has the functional LightRAG/AI fix (`943ff33`).
- Production runtime is already verified healthy.
- `eddc131` is a repo hygiene commit, not a functional fix.

What to do later when network access to GitHub is normal again:

```bash
cd D:\web-main-push
git push origin push-small-embedding:main
```

## Recommended Next Action

If continuing feature work, start from:

```bash
cd D:\web-main-clean
```

Then re-run the relevant local tests before making new changes.
