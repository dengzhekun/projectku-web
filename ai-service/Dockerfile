FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
ENV PIP_TRUSTED_HOST=mirrors.aliyun.com
ENV HF_ENDPOINT=https://hf-mirror.com
ENV HF_HUB_DISABLE_TELEMETRY=1

COPY requirements.remote.txt .
RUN pip install --no-cache-dir -r requirements.remote.txt

COPY app ./app
COPY data ./data

EXPOSE 9000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9000"]
