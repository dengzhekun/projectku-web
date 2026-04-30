from typing import Any


def merge_context(chunks: list[dict[str, Any]]) -> str:
    lines: list[str] = []
    for chunk in chunks:
        if not isinstance(chunk, dict):
            continue
        metadata = chunk.get("metadata") if isinstance(chunk.get("metadata"), dict) else {}
        source_type = metadata.get("source_type") or "lightrag"
        source_id = metadata.get("source_id") or "unknown"
        document = chunk.get("document") or ""
        if not document:
            continue
        lines.append(f"[{source_type}:{source_id}] {document}")
    return "\n".join(lines)
