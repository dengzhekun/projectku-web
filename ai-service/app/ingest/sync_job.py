from pathlib import Path
import sys


def ensure_project_root_on_path(script_path: str | Path, sys_path: list[str]) -> None:
    project_root = Path(script_path).resolve().parents[2]
    project_root_str = str(project_root)
    if project_root_str not in sys_path:
        sys_path.insert(0, project_root_str)


if __package__ in (None, ""):
    ensure_project_root_on_path(__file__, sys.path)

from app.clients.embedding_client import EmbeddingClient
from app.config import get_settings
from app.ingest.seed_documents import build_seed_records
from app.retrieval.knowledge_retriever import build_knowledge_retriever


def sync_seed_documents() -> int:
    settings = get_settings()
    embedding_client = EmbeddingClient(settings)
    retriever = build_knowledge_retriever(settings, embedding_client)
    records = build_seed_records()
    retriever.upsert(records)
    return len(records)


if __name__ == "__main__":
    total = sync_seed_documents()
    print(f"seeded {total} records")
