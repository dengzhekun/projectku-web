from __future__ import annotations

from typing import Any

import httpx
from json import JSONDecodeError

from app.config import Settings


class LightRagClient:
    _QUERY_ENDPOINT = "/query"
    _QUERY_DATA_ENDPOINT = "/query/data"
    _INSERT_TEXT_ENDPOINT = "/documents/text"
    _INSERT_TEXTS_ENDPOINT = "/documents/texts"
    _DELETE_DOCUMENT_ENDPOINT = "/documents/delete_document"
    _TRACK_STATUS_ENDPOINT_PREFIX = "/documents/track_status"

    def __init__(self, settings: Settings):
        self.base_url = settings.lightrag_base_url.rstrip("/")
        self.api_key = settings.lightrag_api_key
        self.api_key_header = settings.lightrag_api_key_header
        self.timeout_seconds = max(settings.lightrag_timeout_seconds, 1)
        self.query_mode = settings.lightrag_query_mode

    def _headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers[self.api_key_header] = self.api_key
        return headers

    def _post(self, endpoint: str, payload: dict[str, Any]) -> dict[str, Any]:
        url = f"{self.base_url}{endpoint}"
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.post(url, json=payload, headers=self._headers())
                response.raise_for_status()
        except httpx.HTTPError as exc:
            raise RuntimeError(f"LightRAG request failed for {endpoint}: {exc}") from exc
        data = response.json()
        return data if isinstance(data, dict) else {"data": data}

    def _get(self, endpoint: str) -> dict[str, Any]:
        url = f"{self.base_url}{endpoint}"
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.get(url, headers=self._headers())
                response.raise_for_status()
        except httpx.HTTPError as exc:
            raise RuntimeError(f"LightRAG request failed for {endpoint}: {exc}") from exc
        data = response.json()
        return data if isinstance(data, dict) else {"data": data}

    def _delete(self, endpoint: str, payload: dict[str, Any]) -> dict[str, Any]:
        url = f"{self.base_url}{endpoint}"
        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.request("DELETE", url, json=payload, headers=self._headers())
                response.raise_for_status()
        except httpx.HTTPError as exc:
            raise RuntimeError(f"LightRAG request failed for {endpoint}: {exc}") from exc
        if not response.content:
            return {}
        try:
            data = response.json()
        except JSONDecodeError as exc:
            raise RuntimeError(f"LightRAG request returned invalid JSON for {endpoint}: {exc}") from exc
        return data if isinstance(data, dict) else {"data": data}

    def query(self, message: str, mode: str | None = None, top_k: int | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "query": message,
            "mode": mode or self.query_mode,
        }
        if top_k is not None:
            payload["top_k"] = top_k
        return self._post(
            self._QUERY_ENDPOINT,
            payload,
        )

    def query_data(
        self,
        message: str,
        mode: str | None = None,
        top_k: int | None = None,
        chunk_top_k: int | None = None,
        enable_rerank: bool | None = None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "query": message,
            "mode": mode or self.query_mode,
        }
        if top_k is not None:
            payload["top_k"] = top_k
        if chunk_top_k is not None:
            payload["chunk_top_k"] = chunk_top_k
        if enable_rerank is not None:
            payload["enable_rerank"] = enable_rerank
        return self._post(
            self._QUERY_DATA_ENDPOINT,
            payload,
        )

    def insert_text(self, text: str, description: str | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {"text": text}
        if description:
            payload["description"] = description
        return self._post(self._INSERT_TEXT_ENDPOINT, payload)

    def insert_texts(self, texts: list[str]) -> dict[str, Any]:
        return self._post(self._INSERT_TEXTS_ENDPOINT, {"texts": texts})

    def delete_documents(
        self,
        doc_ids: list[str],
        *,
        delete_file: bool = False,
        delete_llm_cache: bool = False,
    ) -> dict[str, Any]:
        return self._delete(
            self._DELETE_DOCUMENT_ENDPOINT,
            {
                "doc_ids": doc_ids,
                "delete_file": delete_file,
                "delete_llm_cache": delete_llm_cache,
            },
        )

    def track_status(self, track_id: str) -> dict[str, Any]:
        return self._get(f"{self._TRACK_STATUS_ENDPOINT_PREFIX}/{track_id}")
