from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from urllib import parse, request, error


DOCUMENTS = [
    ("after-sales-policy.md", "KB After Sales Policy", "after_sales"),
    ("coupon-rules.md", "KB Coupon Rules", "coupon"),
    ("logistics-rules.md", "KB Logistics Rules", "logistics"),
    ("refund-payment-rules.md", "KB Refund Payment Rules", "payment_refund"),
    ("product-shopping-guide.md", "KB Product Shopping Guide", "shopping_guide"),
]


def api_json(method: str, url: str, token: str | None = None, body: dict | None = None, timeout: int = 60):
    data = None
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"

    req = request.Request(url, data=data, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} failed: HTTP {exc.code} {detail}") from exc
    except error.URLError as exc:
        raise RuntimeError(f"{method} {url} failed: {exc.reason}") from exc

    if not raw:
        return None
    payload = json.loads(raw)
    if isinstance(payload, dict) and "code" in payload and payload.get("code") != 200:
        raise RuntimeError(f"{method} {url} failed: {payload}")
    return payload.get("data") if isinstance(payload, dict) and "data" in payload else payload


def main() -> int:
    parser = argparse.ArgumentParser(description="Import seed knowledge-base documents through backend APIs.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080/api/v1")
    parser.add_argument("--account", default="user@example.com")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--chunk", action="store_true", help="Chunk each imported document.")
    parser.add_argument("--index", action="store_true", help="Index each imported document after chunking.")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--timeout", type=int, default=60)
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    seed_dir = repo_root / "docs" / "knowledge-base" / "seed"
    base_url = args.base_url.rstrip("/")

    missing = [name for name, _, _ in DOCUMENTS if not (seed_dir / name).is_file()]
    if missing:
        raise SystemExit(f"Missing seed files: {', '.join(missing)}")

    print(f"Seed directory: {seed_dir}")
    print(f"Target API: {base_url}")
    print(f"Options: chunk={args.chunk}, index={args.index}, dry_run={args.dry_run}")

    token = None
    if not args.dry_run:
        login = api_json(
            "POST",
            f"{base_url}/auth/login",
            body={"account": args.account, "password": args.password},
            timeout=args.timeout,
        )
        token = login.get("token") if isinstance(login, dict) else None
        if not token:
            raise SystemExit("Login succeeded but token is missing.")

    results = []
    for filename, title, category in DOCUMENTS:
        content = (seed_dir / filename).read_text(encoding="utf-8")
        result = {"file": filename, "title": title, "id": "", "write": "skipped", "chunk": "skipped", "index": "skipped"}

        if args.dry_run:
            result["write"] = "would-upsert"
            if args.chunk:
                result["chunk"] = "would-chunk"
            if args.index:
                result["index"] = "would-index"
            results.append(result)
            continue

        query = parse.urlencode({"keyword": title})
        existing = api_json("GET", f"{base_url}/kb/documents?{query}", token=token, timeout=args.timeout) or []
        exact = next((doc for doc in existing if doc.get("title") == title), None)
        payload = {"title": title, "category": category, "contentText": content}

        if exact:
            document_id = exact["id"]
            api_json("PUT", f"{base_url}/kb/documents/{document_id}", token=token, body=payload, timeout=args.timeout)
            result["write"] = "updated"
        else:
            created = api_json("POST", f"{base_url}/kb/documents", token=token, body=payload, timeout=args.timeout)
            document_id = created["id"]
            result["write"] = "created"

        result["id"] = document_id

        if args.chunk or args.index:
            api_json("POST", f"{base_url}/kb/documents/{document_id}/chunk", token=token, timeout=args.timeout)
            result["chunk"] = "ok"

        if args.index:
            api_json("POST", f"{base_url}/kb/documents/{document_id}/index", token=token, timeout=args.timeout)
            result["index"] = "ok"

        results.append(result)

    print("\nImport summary:")
    for item in results:
        print(
            f"- {item['file']}: id={item['id'] or '-'} "
            f"write={item['write']} chunk={item['chunk']} index={item['index']}"
        )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
