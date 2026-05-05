from functools import lru_cache
import os
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


def get_default_env_files() -> tuple[str, str]:
    app_dir = Path(__file__).resolve().parent.parent
    project_dir = app_dir.parent
    return (
        str(app_dir / ".env"),
        str(project_dir / "deploy" / "ai-service.env"),
    )


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=get_default_env_files(), extra="ignore")

    ai_llm_provider: str = "xfyun_codingplan"
    ai_llm_base_url: str = "https://maas-coding-api.cn-huabei-1.xf-yun.com/v2"
    ai_llm_api_key: str = ""
    ai_llm_model: str = "astron-code-latest"
    ai_llm_temperature: float = 0.3
    ai_llm_max_tokens: int = 1024
    ai_llm_strip_think: bool = True
    ai_llm_timeout_seconds: int = 120

    ai_embedding_provider: str = "local_bge"
    ai_embedding_model: str = "BAAI/bge-small-zh-v1.5"
    ai_embedding_dimensions: int = 512
    ai_embedding_device: str = "cpu"
    ai_embedding_hf_endpoint: str = "https://hf-mirror.com"
    ai_embedding_cache_dir: str = "./data/huggingface"
    ai_embedding_normalize: bool = True
    ai_embedding_batch_size: int = 16
    ai_embedding_remote_url: str = ""
    ai_embedding_remote_api_key: str = ""
    ai_embedding_remote_timeout_seconds: int = 30
    ai_embedding_gateway_api_key: str = ""
    lightrag_collection: str = "ecommerce_kb_v1"
    knowledge_retriever: str = "lightrag"
    lightrag_base_url: str = "http://127.0.0.1:19621"
    lightrag_api_key: str = ""
    lightrag_api_key_header: str = "X-API-Key"
    lightrag_timeout_seconds: int = 60
    lightrag_query_mode: str = "hybrid"
    lightrag_doc_registry_path: str = "./data/lightrag_doc_registry.json"

    neo4j_uri: str = "bolt://127.0.0.1:7687"
    neo4j_user: str = "neo4j"
    neo4j_password: str = ""

    ai_cs_max_message_length: int = 800
    ai_cs_max_history_turns: int = 8
    ai_cs_enable_order_tools: bool = True

    backend_api_base_url: str = "http://127.0.0.1:8080/api"
    product_tool_timeout_seconds: int = 10
    product_tool_max_results: int = 5

    def ensure_dirs(self) -> None:
        Path(self.ai_embedding_cache_dir).mkdir(parents=True, exist_ok=True)
        Path(self.lightrag_doc_registry_path).resolve().parent.mkdir(parents=True, exist_ok=True)


def apply_runtime_environment(settings: Settings) -> None:
    cache_dir = Path(settings.ai_embedding_cache_dir)
    os.environ.setdefault("HF_ENDPOINT", settings.ai_embedding_hf_endpoint.rstrip("/"))
    os.environ.setdefault("HF_HOME", str(cache_dir))
    os.environ.setdefault("HUGGINGFACE_HUB_CACHE", str(cache_dir / "hub"))
    os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    settings = Settings()
    apply_runtime_environment(settings)
    settings.ensure_dirs()
    return settings
