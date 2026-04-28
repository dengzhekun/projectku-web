from pathlib import Path

from app import config as config_module


def test_default_env_files_include_repo_deploy_env(monkeypatch, tmp_path):
    fake_config_path = tmp_path / "ai-service" / "app" / "config.py"
    fake_config_path.parent.mkdir(parents=True)
    fake_config_path.write_text("", encoding="utf-8")
    monkeypatch.setattr(config_module, "__file__", str(fake_config_path))

    env_files = config_module.get_default_env_files()

    assert env_files == (
        str(tmp_path / "ai-service" / ".env"),
        str(tmp_path / "deploy" / "ai-service.env"),
    )


def test_apply_runtime_environment_sets_embedding_download_env(monkeypatch, tmp_path):
    for name in (
        "HF_ENDPOINT",
        "HF_HOME",
        "HUGGINGFACE_HUB_CACHE",
        "HF_HUB_DISABLE_TELEMETRY",
        "ANONYMIZED_TELEMETRY",
    ):
        monkeypatch.delenv(name, raising=False)

    cache_dir = tmp_path / "hf-cache"
    settings = config_module.Settings(
        ai_embedding_hf_endpoint="https://hf-mirror.com",
        ai_embedding_cache_dir=str(cache_dir),
        chroma_anonymized_telemetry=False,
    )

    config_module.apply_runtime_environment(settings)

    assert config_module.os.environ["HF_ENDPOINT"] == "https://hf-mirror.com"
    assert config_module.os.environ["HF_HOME"] == str(cache_dir)
    assert config_module.os.environ["HUGGINGFACE_HUB_CACHE"] == str(cache_dir / "hub")
    assert config_module.os.environ["HF_HUB_DISABLE_TELEMETRY"] == "1"
    assert config_module.os.environ["ANONYMIZED_TELEMETRY"] == "False"
