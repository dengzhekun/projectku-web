#!/usr/bin/env bash
set -euo pipefail

SERVICE_DIR="${SERVICE_DIR:-/opt/embedding-service}"
SERVICE_NAME="${SERVICE_NAME:-embedding}"
MODEL_NAME="${MODEL_NAME:-BAAI/bge-small-zh-v1.5}"
PORT="${PORT:-9001}"
API_KEY="${API_KEY:-}"
HF_ENDPOINT="${HF_ENDPOINT:-https://hf-mirror.com}"
PIP_INDEX_URL="${PIP_INDEX_URL:-https://pypi.tuna.tsinghua.edu.cn/simple}"
SWAP_SIZE="${SWAP_SIZE:-2G}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

if [[ -z "$API_KEY" ]]; then
  API_KEY="$(python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
)"
fi

echo "[1/7] Ensuring swap is available..."
swap_mb="$(free -m | awk '/^Swap:/ {print $2}')"
if [[ "${swap_mb:-0}" -lt 2048 ]]; then
  if swapon --show=NAME --noheadings | grep -qx "/swapfile"; then
    swapoff /swapfile
  fi
  fallocate -l "$SWAP_SIZE" /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap -f /swapfile
  swapon /swapfile
  cp /etc/fstab /etc/fstab.bak.embedding || true
  sed -i '\|/swapfile none swap sw 0 0|d' /etc/fstab
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/99-embedding-swap.conf
sysctl -p /etc/sysctl.d/99-embedding-swap.conf >/dev/null

echo "[2/7] Installing system packages..."
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y python3-venv python3-pip curl

echo "[3/7] Creating service directory..."
mkdir -p "$SERVICE_DIR"
cd "$SERVICE_DIR"

echo "[4/7] Creating Python virtual environment..."
python3 -m venv .venv
source .venv/bin/activate
pip config set global.index-url "$PIP_INDEX_URL"
pip install -U pip
pip install --no-cache-dir fastapi "uvicorn[standard]" sentence-transformers

echo "[5/7] Writing embedding API..."
cat > "$SERVICE_DIR/app.py" <<'PY'
import os

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

try:
    import torch
    torch.set_num_threads(int(os.getenv("TORCH_NUM_THREADS", "1")))
except Exception:
    pass

API_KEY = os.getenv("EMBEDDING_API_KEY", "")
MODEL_NAME = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
BATCH_SIZE = int(os.getenv("EMBEDDING_BATCH_SIZE", "4"))

app = FastAPI()
model = SentenceTransformer(MODEL_NAME, device="cpu")


class EmbedRequest(BaseModel):
    texts: list[str]


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/embed")
def embed(req: EmbedRequest, x_api_key: str | None = Header(default=None)):
    if API_KEY and x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="invalid api key")
    if not req.texts:
        raise HTTPException(status_code=400, detail="texts is required")

    vectors = model.encode(
        req.texts,
        normalize_embeddings=True,
        batch_size=BATCH_SIZE,
    )
    return {
        "model": MODEL_NAME,
        "dimension": len(vectors[0]) if len(vectors) else 0,
        "embeddings": vectors.tolist(),
    }
PY

echo "[6/7] Writing systemd service..."
cat > /etc/embedding-service.env <<EOF
EMBEDDING_API_KEY=$API_KEY
EMBEDDING_MODEL=$MODEL_NAME
EMBEDDING_BATCH_SIZE=4
HF_ENDPOINT=$HF_ENDPOINT
HF_HOME=$SERVICE_DIR/hf-cache
HUGGINGFACE_HUB_CACHE=$SERVICE_DIR/hf-cache/hub
HF_HUB_DISABLE_TELEMETRY=1
OMP_NUM_THREADS=1
MKL_NUM_THREADS=1
TORCH_NUM_THREADS=1
EOF
chmod 600 /etc/embedding-service.env

cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<EOF
[Unit]
Description=Embedding Service
After=network.target

[Service]
WorkingDirectory=$SERVICE_DIR
EnvironmentFile=/etc/embedding-service.env
ExecStart=$SERVICE_DIR/.venv/bin/uvicorn app:app --host 0.0.0.0 --port $PORT
Restart=always
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable "$SERVICE_NAME" >/dev/null
systemctl restart "$SERVICE_NAME"

echo "[7/7] Verifying service..."
sleep 3
systemctl --no-pager --full status "$SERVICE_NAME" || true
curl -fsS "http://127.0.0.1:${PORT}/health" && echo

echo
echo "Embedding service installed."
echo "URL: http://SERVER_PUBLIC_IP:${PORT}/embed"
echo "API key: $API_KEY"
echo "Model: $MODEL_NAME"
echo
echo "Remember to allow TCP ${PORT} only from your business server public IP in the cloud security group."
