from __future__ import annotations

import json
from typing import Any

_META_PREFIX = "[[LIGHTRAG_META:"
_META_SUFFIX = "]]"
_META_VERSION_FIELD = "_lightrag_meta_version"
_META_VERSION = 1
_INT_FIELDS = {"document_id", "chunk_id", "chunk_index", "version"}


def encode_document_with_metadata(document: str, metadata: dict[str, Any] | None) -> str:
    if not isinstance(document, str):
        return ""
    if not isinstance(metadata, dict) or not metadata:
        return document

    envelope_metadata = dict(metadata)
    envelope_metadata[_META_VERSION_FIELD] = _META_VERSION

    try:
        payload = json.dumps(envelope_metadata, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    except (TypeError, ValueError):
        return document
    return f"{_META_PREFIX}{payload}{_META_SUFFIX}\n{document}"


def decode_document_with_metadata(text: str) -> tuple[str, dict[str, Any] | None]:
    if not isinstance(text, str):
        return "", None
    if not text.startswith(_META_PREFIX):
        return text, None

    suffix_idx = text.find(_META_SUFFIX, len(_META_PREFIX))
    if suffix_idx < 0:
        return text, None

    metadata_json = text[len(_META_PREFIX) : suffix_idx]
    body_start = suffix_idx + len(_META_SUFFIX)
    document = text[body_start + 1 :] if text[body_start : body_start + 1] == "\n" else text[body_start:]

    try:
        raw_metadata = json.loads(metadata_json)
    except json.JSONDecodeError:
        return text, None
    if not isinstance(raw_metadata, dict):
        return text, None
    if raw_metadata.get(_META_VERSION_FIELD) != _META_VERSION:
        return text, None

    decoded_metadata = dict(raw_metadata)
    decoded_metadata.pop(_META_VERSION_FIELD, None)
    return document, _normalize_metadata(decoded_metadata)


def _normalize_metadata(metadata: dict[str, Any]) -> dict[str, Any]:
    normalized: dict[str, Any] = {}
    for key, value in metadata.items():
        if key in _INT_FIELDS:
            normalized[key] = _coerce_int(value)
        else:
            normalized[key] = value
    return normalized


def _coerce_int(value: Any) -> Any:
    if isinstance(value, bool):
        return value
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        stripped = value.strip()
        if not stripped:
            return value
        try:
            return int(stripped)
        except ValueError:
            return value
    return value
