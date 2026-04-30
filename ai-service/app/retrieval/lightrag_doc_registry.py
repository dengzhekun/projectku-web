from __future__ import annotations

import hashlib
import json
from pathlib import Path


def sanitize_lightrag_text(text: str) -> str:
    if not isinstance(text, str):
        return ""
    return text.encode("utf-8", errors="ignore").decode("utf-8").strip()


def compute_lightrag_doc_id(text: str) -> str:
    sanitized = sanitize_lightrag_text(text)
    digest = hashlib.md5(sanitized.encode("utf-8")).hexdigest()
    return f"doc-{digest}"


class LightRagDocRegistry:
    def __init__(self, path: str | Path):
        self.path = Path(path)

    def get_document_doc_ids(self, document_id: int) -> list[str]:
        data = self._load()
        return list(data.get("documents", {}).get(str(document_id), []))

    def replace_document_doc_ids(self, document_id: int, doc_ids: list[str]) -> None:
        data = self._load()
        documents = data.setdefault("documents", {})
        documents[str(document_id)] = [doc_id for doc_id in doc_ids if isinstance(doc_id, str) and doc_id]
        self._save(data)

    def remove_document(self, document_id: int) -> list[str]:
        data = self._load()
        documents = data.setdefault("documents", {})
        removed = list(documents.pop(str(document_id), []))
        self._save(data)
        return removed

    def _load(self) -> dict:
        if not self.path.exists():
            return {"documents": {}}
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return {"documents": {}}
        if not isinstance(data, dict):
            return {"documents": {}}
        documents = data.get("documents")
        if not isinstance(documents, dict):
            return {"documents": {}}
        normalized: dict[str, list[str]] = {}
        for key, value in documents.items():
            if not isinstance(value, list):
                continue
            normalized[str(key)] = [item for item in value if isinstance(item, str) and item]
        return {"documents": normalized}

    def _save(self, data: dict) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True)
        tmp_path = self.path.with_suffix(f"{self.path.suffix}.tmp")
        tmp_path.write_text(payload, encoding="utf-8")
        tmp_path.replace(self.path)
