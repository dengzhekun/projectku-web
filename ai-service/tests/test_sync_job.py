from pathlib import Path

from app.ingest import sync_job


def test_ensure_project_root_on_path_adds_repo_root_for_direct_script_execution(tmp_path):
    script_path = tmp_path / "ai-service" / "app" / "ingest" / "sync_job.py"
    script_path.parent.mkdir(parents=True)
    script_path.write_text("", encoding="utf-8")
    sys_path = []

    sync_job.ensure_project_root_on_path(script_path, sys_path)

    assert sys_path == [str(tmp_path / "ai-service")]
