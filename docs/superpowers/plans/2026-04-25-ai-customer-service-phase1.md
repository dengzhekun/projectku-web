# AI Customer Service Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a first working knowledge-base customer service system that uses Xunfei Coding Plan as the remote LLM provider and a local BGE-M3 embedding service, while preserving the current `/v1/customer-service/chat` entrypoint.

**Architecture:** The Spring Boot backend stops talking directly to Khoj and instead calls a new `ai-service`. The `ai-service` owns request orchestration, local embedding generation, Chroma retrieval, minimal Neo4j entity lookup, and Xunfei Coding Plan chat completion. Frontend keeps the current chat widget but upgrades the response contract to support citations, actions, and fallback reasons.

**Tech Stack:** Spring Boot 3.2, Java 17, Vue 3, TypeScript, FastAPI, OpenAI Python SDK, ChromaDB, Neo4j Python driver, sentence-transformers / FlagEmbedding, Docker Compose

---

### Task 1: Replace Khoj Coupling With AI Gateway Contract

**Files:**
- Create: `back/src/main/java/com/web/config/AiServiceProperties.java`
- Create: `back/src/main/java/com/web/dto/CustomerServiceChatRequest.java`
- Create: `back/src/main/java/com/web/dto/CustomerServiceChatResponse.java`
- Create: `back/src/main/java/com/web/dto/CustomerServiceCitation.java`
- Create: `back/src/main/java/com/web/dto/CustomerServiceAction.java`
- Create: `back/src/main/java/com/web/service/AiCustomerServiceClient.java`
- Create: `back/src/main/java/com/web/service/impl/AiCustomerServiceClientImpl.java`
- Modify: `back/src/main/java/com/web/controller/CustomerServiceController.java`
- Modify: `back/src/main/java/com/web/service/CustomerServiceService.java`
- Modify: `back/src/main/java/com/web/service/impl/CustomerServiceServiceImpl.java`
- Modify: `back/src/main/java/com/web/dto/CustomerServiceRequests.java`
- Modify: `back/src/main/resources/application-prod.yml`
- Test: `back/src/test/java/com/web/service/impl/CustomerServiceServiceImplTest.java`

- [ ] **Step 1: Write the failing service test for the new response contract**

```java
@Test
void chatReturnsStructuredReplyFromAiGateway() {
    CustomerServiceChatResponse gatewayReply = new CustomerServiceChatResponse();
    gatewayReply.setAnswer("可以先查看订单详情页中的售后入口。");
    gatewayReply.setConfidence(new BigDecimal("0.92"));
    gatewayReply.setFallbackReason(null);

    when(aiCustomerServiceClient.chat(any(CustomerServiceChatRequest.class)))
            .thenReturn(gatewayReply);

    CustomerServiceChatResponse response = customerServiceService.chat("申请退款", "conversation-1");

    assertEquals("可以先查看订单详情页中的售后入口。", response.getAnswer());
    ArgumentCaptor<CustomerServiceChatRequest> requestCaptor =
            ArgumentCaptor.forClass(CustomerServiceChatRequest.class);
    verify(aiCustomerServiceClient).chat(requestCaptor.capture());
    assertEquals("申请退款", requestCaptor.getValue().getMessage());
    assertEquals("conversation-1", requestCaptor.getValue().getConversationId());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=CustomerServiceServiceImplTest test`
Expected: FAIL with missing `AiCustomerServiceClient` dependency or `chat` return type mismatch

- [ ] **Step 3: Add the AI gateway DTOs and client interface**

```java
public class CustomerServiceChatRequest {
    private String message;
    private String conversationId;
    private Long productId;
    private Long orderId;
    private String scene;
}

public class CustomerServiceChatResponse {
    private String answer;
    private BigDecimal confidence;
    private List<CustomerServiceCitation> citations = new ArrayList<>();
    private List<CustomerServiceAction> actions = new ArrayList<>();
    private String fallbackReason;
}

public interface AiCustomerServiceClient {
    CustomerServiceChatResponse chat(CustomerServiceChatRequest request);
}
```

- [ ] **Step 4: Implement the Spring HTTP client and wire the service**

```java
@Service
@ConfigurationProperties(prefix = "ai.service")
public class AiServiceProperties {
    private String baseUrl = "http://127.0.0.1:9000";
    private int timeoutSeconds = 120;
}

@Component
public class AiCustomerServiceClientImpl implements AiCustomerServiceClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiServiceProperties properties;

    @Override
    public CustomerServiceChatResponse chat(CustomerServiceChatRequest request) {
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat";
        try {
            return restTemplate.postForObject(url, request, CustomerServiceChatResponse.class);
        } catch (RestClientException ex) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "智能客服服务暂时不可用，请稍后重试");
        }
    }
}
```

```java
public interface CustomerServiceService {
    CustomerServiceChatResponse chat(String message, String conversationId);
}

@Service
public class CustomerServiceServiceImpl implements CustomerServiceService {
    private final AiCustomerServiceClient aiCustomerServiceClient;

    @Override
    public CustomerServiceChatResponse chat(String message, String conversationId) {
        String normalizedMessage = normalizeMessage(message);
        CustomerServiceChatRequest request = new CustomerServiceChatRequest();
        request.setMessage(normalizedMessage);
        request.setConversationId(conversationId);
        return aiCustomerServiceClient.chat(request);
    }
}
```

- [ ] **Step 5: Update the controller response shape**

```java
@PostMapping("/chat")
public ResponseEntity<Map<String, Object>> chat(@RequestBody CustomerServiceRequests.ChatRequest request) {
    CustomerServiceChatResponse reply =
            customerServiceService.chat(request.getMessage(), request.getConversationId());
    Map<String, Object> result = MapUtil.builder(new HashMap<String, Object>())
            .put("code", 200)
            .put("message", "success")
            .put("data", reply)
            .build();
    return ResponseEntity.ok(result);
}
```

- [ ] **Step 6: Add AI service config**

```yaml
ai:
  service:
    base-url: ${AI_SERVICE_BASE_URL:http://127.0.0.1:9000}
    timeout-seconds: ${AI_SERVICE_TIMEOUT_SECONDS:120}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -Dtest=CustomerServiceServiceImplTest test`
Expected: PASS with the new structured response assertions green

- [ ] **Step 8: Commit**

```bash
git add back/src/main/java/com/web/config/AiServiceProperties.java back/src/main/java/com/web/dto/CustomerServiceChatRequest.java back/src/main/java/com/web/dto/CustomerServiceChatResponse.java back/src/main/java/com/web/dto/CustomerServiceCitation.java back/src/main/java/com/web/dto/CustomerServiceAction.java back/src/main/java/com/web/service/AiCustomerServiceClient.java back/src/main/java/com/web/service/impl/AiCustomerServiceClientImpl.java back/src/main/java/com/web/controller/CustomerServiceController.java back/src/main/java/com/web/service/CustomerServiceService.java back/src/main/java/com/web/service/impl/CustomerServiceServiceImpl.java back/src/main/java/com/web/dto/CustomerServiceRequests.java back/src/main/resources/application-prod.yml back/src/test/java/com/web/service/impl/CustomerServiceServiceImplTest.java
git commit -m "feat: route customer service through ai gateway"
```

### Task 2: Scaffold `ai-service` With Xunfei Coding Plan And Local Embedding

**Files:**
- Create: `ai-service/requirements.txt`
- Create: `ai-service/Dockerfile`
- Create: `ai-service/app/main.py`
- Create: `ai-service/app/config.py`
- Create: `ai-service/app/schemas.py`
- Create: `ai-service/app/api/chat.py`
- Create: `ai-service/app/clients/llm_client.py`
- Create: `ai-service/app/clients/embedding_client.py`
- Create: `ai-service/app/prompts/customer_service_prompt.py`
- Create: `ai-service/app/safety/guardrails.py`
- Create: `ai-service/tests/test_chat_api.py`

- [ ] **Step 1: Write the failing API test for the orchestration entrypoint**

```python
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_chat_returns_structured_response(monkeypatch):
    monkeypatch.setattr(
        "app.api.chat.handle_chat",
        lambda request: {
            "answer": "请先确认订单状态。",
            "confidence": 0.91,
            "citations": [],
            "actions": [],
            "fallbackReason": None,
        },
    )
    response = client.post("/chat", json={"message": "退款", "conversationId": "c1"})
    assert response.status_code == 200
    assert response.json()["answer"] == "请先确认订单状态。"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m pytest ai-service/tests/test_chat_api.py -q`
Expected: FAIL with missing package or missing FastAPI application modules

- [ ] **Step 3: Add the app skeleton, config, and provider clients**

```python
# ai-service/app/config.py
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    ai_llm_provider: str = "xfyun_codingplan"
    ai_llm_base_url: str = "https://maas-coding-api.cn-huabei-1.xf-yun.com/v2"
    ai_llm_api_key: str
    ai_llm_model: str = "astron-code-latest"
    ai_embedding_provider: str = "local_bge_m3"
    ai_embedding_model: str = "BAAI/bge-m3"
    ai_embedding_base_url: str = "http://127.0.0.1:9001"
```

```python
# ai-service/app/clients/llm_client.py
from openai import OpenAI

class LlmClient:
    def __init__(self, settings):
        self.client = OpenAI(base_url=settings.ai_llm_base_url, api_key=settings.ai_llm_api_key)
        self.model = settings.ai_llm_model

    def chat(self, prompt: str) -> str:
        response = self.client.chat.completions.create(
            model=self.model,
            temperature=0.3,
            max_tokens=1024,
            messages=[{"role": "user", "content": prompt}],
        )
        return response.choices[0].message.content or ""
```

```python
# ai-service/app/clients/embedding_client.py
import requests

class EmbeddingClient:
    def __init__(self, settings):
        self.base_url = settings.ai_embedding_base_url.rstrip("/")
        self.model = settings.ai_embedding_model

    def embed(self, text: str) -> list[float]:
        response = requests.post(
            f"{self.base_url}/embed",
            json={"model": self.model, "input": text},
            timeout=60,
        )
        response.raise_for_status()
        payload = response.json()
        return payload["data"][0]["embedding"]
```

- [ ] **Step 4: Add the FastAPI application and prompt builder**

```python
# ai-service/app/main.py
from fastapi import FastAPI
from app.api.chat import router as chat_router

app = FastAPI(title="projectku-ai-service")
app.include_router(chat_router)

@app.get("/health")
def health():
    return {"status": "ok"}
```

```python
# ai-service/app/prompts/customer_service_prompt.py
def build_customer_service_prompt(message: str, retrieved_context: str, business_facts: str) -> str:
    return f"""你是本电商平台的中文在线客服。
1. 只能基于业务事实和知识库证据回答。
2. 不得编造价格、库存、订单状态、退款结果、物流结果。
3. 证据不足时明确说目前无法确认，并给出下一步操作。

【用户问题】
{message}

【业务事实】
{business_facts}

【知识库证据】
{retrieved_context}
"""
```

- [ ] **Step 5: Implement `/chat` and the basic guardrails**

```python
# ai-service/app/api/chat.py
from fastapi import APIRouter, HTTPException
from app.schemas import ChatRequest, ChatResponse

router = APIRouter()

@router.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    if not request.message or not request.message.strip():
        raise HTTPException(status_code=400, detail="message is required")
    return handle_chat(request)
```

```python
# ai-service/app/safety/guardrails.py
def strip_think_tags(text: str) -> str:
    return text.replace("<think>", "").replace("</think>", "").strip()
```

- [ ] **Step 6: Add runtime dependencies**

```txt
fastapi==0.115.0
uvicorn[standard]==0.30.6
openai==1.51.0
pydantic==2.9.2
pydantic-settings==2.5.2
requests==2.32.3
pytest==8.3.3
httpx==0.27.2
```

- [ ] **Step 7: Run test to verify it passes**

Run: `python -m pytest ai-service/tests/test_chat_api.py -q`
Expected: PASS with one successful `/chat` contract test

- [ ] **Step 8: Commit**

```bash
git add ai-service/requirements.txt ai-service/Dockerfile ai-service/app/main.py ai-service/app/config.py ai-service/app/schemas.py ai-service/app/api/chat.py ai-service/app/clients/llm_client.py ai-service/app/clients/embedding_client.py ai-service/app/prompts/customer_service_prompt.py ai-service/app/safety/guardrails.py ai-service/tests/test_chat_api.py
git commit -m "feat: scaffold ai service with xfyun llm and local embedding"
```

### Task 3: Add Chroma Retrieval And Seed Knowledge Documents

**Files:**
- Create: `ai-service/app/retrieval/chroma_retriever.py`
- Create: `ai-service/app/ingest/document_chunker.py`
- Create: `ai-service/app/ingest/seed_documents.py`
- Create: `ai-service/app/ingest/sync_job.py`
- Create: `ai-service/data/faq_seed.md`
- Create: `ai-service/data/policy_seed.md`
- Create: `ai-service/tests/test_chroma_retriever.py`
- Modify: `ai-service/app/api/chat.py`
- Modify: `ai-service/app/schemas.py`

- [ ] **Step 1: Write the failing retrieval test**

```python
from app.retrieval.chroma_retriever import merge_context

def test_merge_context_includes_source_metadata():
    chunks = [
        {"document": "七天无理由适用于未拆封商品", "metadata": {"source_type": "policy", "source_id": "return_7d"}},
        {"document": "订单详情页可以发起售后", "metadata": {"source_type": "faq", "source_id": "after_sale_entry"}},
    ]
    merged = merge_context(chunks)
    assert "七天无理由适用于未拆封商品" in merged
    assert "policy:return_7d" in merged
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m pytest ai-service/tests/test_chroma_retriever.py -q`
Expected: FAIL with missing `chroma_retriever` module

- [ ] **Step 3: Implement chunking and retrieval**

```python
# ai-service/app/retrieval/chroma_retriever.py
import chromadb

def merge_context(chunks: list[dict]) -> str:
    lines = []
    for chunk in chunks:
        metadata = chunk["metadata"]
        lines.append(f"[{metadata['source_type']}:{metadata['source_id']}] {chunk['document']}")
    return "\n".join(lines)

class ChromaRetriever:
    def __init__(self, settings, embedding_client):
        self.client = chromadb.HttpClient(host=settings.chroma_host, port=settings.chroma_port)
        self.collection = self.client.get_or_create_collection(name=settings.chroma_collection)
        self.embedding_client = embedding_client

    def query(self, text: str, top_k: int = 6) -> list[dict]:
        embedding = self.embedding_client.embed(text)
        result = self.collection.query(query_embeddings=[embedding], n_results=top_k)
        return [
            {"document": doc, "metadata": metadata}
            for doc, metadata in zip(result["documents"][0], result["metadatas"][0])
        ]
```

```python
# ai-service/app/ingest/document_chunker.py
def chunk_markdown(text: str, chunk_size: int = 700, overlap: int = 100) -> list[str]:
    chunks = []
    start = 0
    while start < len(text):
        end = min(len(text), start + chunk_size)
        chunks.append(text[start:end])
        if end == len(text):
            break
        start = end - overlap
    return chunks
```

- [ ] **Step 4: Seed a minimum knowledge base**

```markdown
# ai-service/data/faq_seed.md
Q: 如何申请售后？
A: 进入订单详情页，点击申请售后，根据页面提示提交即可。

Q: 是否支持七天无理由？
A: 未拆封且不影响二次销售的商品通常支持，最终以平台规则和订单页面为准。
```

```python
# ai-service/app/ingest/seed_documents.py
def build_seed_records() -> list[dict]:
    return [
        {
            "id": "faq:after_sale_entry:v1",
            "document": "进入订单详情页，点击申请售后，根据页面提示提交即可。",
            "metadata": {"source_type": "faq", "source_id": "after_sale_entry", "visibility": "public", "version": 1},
        }
    ]
```

- [ ] **Step 5: Wire retrieval into `/chat`**

```python
chunks = chroma_retriever.query(request.message, top_k=6)
retrieved_context = merge_context(chunks)
prompt = build_customer_service_prompt(
    message=request.message,
    retrieved_context=retrieved_context,
    business_facts="暂无实时订单事实",
)
answer = strip_think_tags(llm_client.chat(prompt))
return ChatResponse(
    answer=answer,
    confidence=0.80,
    citations=[
        Citation(sourceType=chunk["metadata"]["source_type"], sourceId=chunk["metadata"]["source_id"], title=chunk["metadata"]["source_id"])
        for chunk in chunks[:3]
    ],
    actions=[],
    fallbackReason=None,
)
```

- [ ] **Step 6: Add Chroma dependency and sync job**

```txt
chromadb==0.5.5
```

```python
# ai-service/app/ingest/sync_job.py
def sync_seed_documents(settings, embedding_client):
    records = build_seed_records()
    collection = chromadb.HttpClient(host=settings.chroma_host, port=settings.chroma_port).get_or_create_collection(
        name=settings.chroma_collection
    )
    collection.upsert(
        ids=[record["id"] for record in records],
        documents=[record["document"] for record in records],
        metadatas=[record["metadata"] for record in records],
        embeddings=[embedding_client.embed(record["document"]) for record in records],
    )
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `python -m pytest ai-service/tests/test_chat_api.py ai-service/tests/test_chroma_retriever.py -q`
Expected: PASS with API and retrieval tests green

- [ ] **Step 8: Commit**

```bash
git add ai-service/app/retrieval/chroma_retriever.py ai-service/app/ingest/document_chunker.py ai-service/app/ingest/seed_documents.py ai-service/app/ingest/sync_job.py ai-service/data/faq_seed.md ai-service/data/policy_seed.md ai-service/tests/test_chroma_retriever.py ai-service/app/api/chat.py ai-service/app/schemas.py ai-service/requirements.txt
git commit -m "feat: add chroma retrieval and seed knowledge base"
```

### Task 4: Add Minimal Neo4j Entity Sync And Graph Lookup

**Files:**
- Create: `ai-service/app/retrieval/neo4j_retriever.py`
- Create: `ai-service/app/ingest/mysql_loader.py`
- Create: `ai-service/tests/test_neo4j_retriever.py`
- Modify: `ai-service/app/config.py`
- Modify: `ai-service/app/api/chat.py`
- Modify: `deploy/ai-service.env.example`
- Modify: `docker-compose.prod.yml`

- [ ] **Step 1: Write the failing graph lookup test**

```python
from app.retrieval.neo4j_retriever import format_graph_context

def test_format_graph_context_outputs_entity_edges():
    rows = [
        {"product": "iPhone 15 Pro", "category": "手机", "policy": "七天无理由"},
    ]
    context = format_graph_context(rows)
    assert "iPhone 15 Pro" in context
    assert "手机" in context
    assert "七天无理由" in context
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m pytest ai-service/tests/test_neo4j_retriever.py -q`
Expected: FAIL with missing `neo4j_retriever` module

- [ ] **Step 3: Implement the Neo4j retriever and config**

```python
# ai-service/app/config.py
    chroma_host: str = "127.0.0.1"
    chroma_port: int = 8001
    chroma_collection: str = "ecommerce_kb_v1"
    neo4j_uri: str = "bolt://127.0.0.1:7687"
    neo4j_user: str = "neo4j"
    neo4j_password: str
```

```python
# ai-service/app/retrieval/neo4j_retriever.py
from neo4j import GraphDatabase

def format_graph_context(rows: list[dict]) -> str:
    return "\n".join(
        f"商品:{row['product']} 分类:{row['category']} 政策:{row['policy']}"
        for row in rows
    )

class Neo4jRetriever:
    def __init__(self, settings):
        self.driver = GraphDatabase.driver(settings.neo4j_uri, auth=(settings.neo4j_user, settings.neo4j_password))

    def lookup_product_policy(self, keyword: str) -> list[dict]:
        query = """
        MATCH (p:Product)-[:BELONGS_TO]->(c:Category)
        OPTIONAL MATCH (c)-[:APPLIES_POLICY]->(policy:Policy)
        WHERE p.name CONTAINS $keyword
        RETURN p.name AS product, c.name AS category, coalesce(policy.title, '') AS policy
        LIMIT 5
        """
        with self.driver.session() as session:
            return [record.data() for record in session.run(query, keyword=keyword)]
```

- [ ] **Step 4: Add product/category/policy sync**

```python
# ai-service/app/ingest/mysql_loader.py
import pymysql

def load_products(mysql_url: str) -> list[dict]:
    connection = pymysql.connect(host="127.0.0.1", user="root", password="123456", database="web")
    with connection.cursor() as cursor:
        cursor.execute("SELECT id, name, category_id, price, stock, status FROM products LIMIT 500")
        return [
            {"id": row[0], "name": row[1], "category_id": row[2], "price": float(row[3]), "stock": row[4], "status": row[5]}
            for row in cursor.fetchall()
        ]
```

```python
# ai-service/app/api/chat.py
graph_rows = neo4j_retriever.lookup_product_policy(request.message)
graph_context = format_graph_context(graph_rows)
prompt = build_customer_service_prompt(
    message=request.message,
    retrieved_context=retrieved_context + "\n" + graph_context,
    business_facts="暂无实时订单事实",
)
```

- [ ] **Step 5: Add deployment wiring**

```yaml
  neo4j:
    image: neo4j:5-community
    container_name: projectku-neo4j-prod
    restart: unless-stopped
    environment:
      NEO4J_AUTH: neo4j/${NEO4J_PASSWORD:-12345678}
    ports:
      - "7474:7474"
      - "7687:7687"
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `python -m pytest ai-service/tests/test_chat_api.py ai-service/tests/test_chroma_retriever.py ai-service/tests/test_neo4j_retriever.py -q`
Expected: PASS with graph lookup formatting covered

- [ ] **Step 7: Commit**

```bash
git add ai-service/app/retrieval/neo4j_retriever.py ai-service/app/ingest/mysql_loader.py ai-service/tests/test_neo4j_retriever.py ai-service/app/config.py ai-service/app/api/chat.py deploy/ai-service.env.example docker-compose.prod.yml
git commit -m "feat: add neo4j graph lookup for customer service"
```

### Task 5: Update Frontend Contract, Docker Wiring, And Runbook

**Files:**
- Modify: `frontend/src/lib/customerService.ts`
- Modify: `frontend/src/components/CustomerServiceChat.vue`
- Modify: `docker-compose.prod.yml`
- Modify: `docs/deployment.md`
- Create: `docs/ai-service-runbook.md`
- Test: `frontend/src/components/CustomerServiceChat.vue` via `npm run build`

- [ ] **Step 1: Write the failing frontend type usage change**

```ts
export type CustomerServiceReply = {
  answer: string
  confidence?: number | null
  citations?: Array<{ sourceType: string; sourceId: string; title: string }>
  actions?: Array<{ type: string; label: string; url?: string | null }>
  fallbackReason?: string | null
}
```

- [ ] **Step 2: Run build to verify it fails**

Run: `npm run build`
Expected: FAIL if the component still assumes `reply.answer` is the only field and does not handle the expanded contract safely

- [ ] **Step 3: Update the frontend API wrapper and widget**

```ts
export const askCustomerService = async (message: string, conversationId?: string | null) => {
  const { data } = await api.post<{ code: number; message: string; data: CustomerServiceReply }>(
    "/v1/customer-service/chat",
    { message, conversationId: conversationId || null },
  )
  return data.data
}
```

```vue
<div v-if="lastReply?.citations?.length" class="chat-citations">
  <span v-for="item in lastReply.citations" :key="`${item.sourceType}-${item.sourceId}`">
    {{ item.title }}
  </span>
</div>
<p v-if="lastReply?.fallbackReason" class="chat-tip">{{ lastReply.fallbackReason }}</p>
```

- [ ] **Step 4: Add deployment and runbook updates**

```yaml
  ai-service:
    build:
      context: ./ai-service
    container_name: projectku-ai-service-prod
    restart: unless-stopped
    env_file:
      - ./deploy/ai-service.env.example
    depends_on:
      - neo4j
      - backend
```

```markdown
# AI Service Runbook

1. Fill `deploy/ai-service.env.example` with the Xunfei Coding Plan API key.
2. Start ChromaDB, Neo4j, and `ai-service`.
3. Execute the seed sync job once.
4. Verify `GET /health` on `ai-service`.
5. Send a smoke test to `/api/v1/customer-service/chat`.
```

- [ ] **Step 5: Run project verification**

Run: `mvn test`
Expected: PASS with backend tests green

Run: `python -m pytest ai-service/tests -q`
Expected: PASS with `ai-service` tests green

Run: `npm run build`
Expected: PASS with frontend bundle built successfully

Run: `docker compose -f docker-compose.prod.yml config`
Expected: PASS with a valid production compose file including `ai-service` and `neo4j`

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/customerService.ts frontend/src/components/CustomerServiceChat.vue docker-compose.prod.yml docs/deployment.md docs/ai-service-runbook.md
git commit -m "feat: wire frontend and deployment for ai customer service"
```

## Self-Review

Spec coverage:
- Xunfei Coding Plan as the default LLM is covered in Task 2 config and provider wiring.
- Local BGE-M3 embedding is covered in Task 2 and Task 3.
- ChromaDB retrieval is covered in Task 3.
- Neo4j integration is covered in Task 4.
- Existing backend entrypoint preservation is covered in Task 1.
- Frontend and deployment handoff are covered in Task 5.

Placeholder scan:
- No placeholder markers remain.
- All tasks contain exact file paths, commands, and minimal code snippets.

Type consistency:
- Backend uses `CustomerServiceChatRequest` and `CustomerServiceChatResponse` throughout Task 1.
- `ai-service` uses `ChatRequest` and `ChatResponse` in Task 2 onward.
- Frontend `CustomerServiceReply` matches the backend response fields in Task 5.
