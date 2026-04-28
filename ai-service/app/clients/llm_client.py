from app.config import Settings


class LlmClient:
    def __init__(self, settings: Settings):
        from openai import OpenAI

        self.client = OpenAI(
            base_url=settings.ai_llm_base_url,
            api_key=settings.ai_llm_api_key or "EMPTY",
            timeout=settings.ai_llm_timeout_seconds,
        )
        self.model = settings.ai_llm_model
        self.temperature = settings.ai_llm_temperature
        self.max_tokens = settings.ai_llm_max_tokens

    def chat(self, prompt: str) -> str:
        response = self.client.chat.completions.create(
            model=self.model,
            temperature=self.temperature,
            max_tokens=self.max_tokens,
            messages=[{"role": "user", "content": prompt}],
        )
        return response.choices[0].message.content or ""

    def stream_chat(self, prompt: str):
        stream = self.client.chat.completions.create(
            model=self.model,
            temperature=self.temperature,
            max_tokens=self.max_tokens,
            messages=[{"role": "user", "content": prompt}],
            stream=True,
        )
        for chunk in stream:
            if not chunk.choices:
                continue
            delta = chunk.choices[0].delta
            content = getattr(delta, "content", None)
            if content:
                yield content
