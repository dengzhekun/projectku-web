# 电商平台知识库智能客服系统技术实现方案

> 目标：在当前 Vue 3 + Spring Boot + MySQL 电商平台上，设计一套可本地部署、可切换模型、可接入 ChromaDB 与 Neo4j 的知识库智能客服系统。重点不是“把参数调小”，而是通过 RAG、GraphRAG、业务工具和安全边界提升准确率与可控性。

## 1. 结论

- 你说的 `ChromeDB` 应该是 `ChromaDB`；你说的 `BGD` 大概率是 `BGE`，推荐本地 embedding 使用 `BAAI/bge-m3`。
- 如果你说的“讯飞 codingplan 的 qwen3.5 35B”是讯飞星辰 MaaS Astron Coding Plan，官网文档显示它是远程 OpenAI/Anthropic 兼容 API，模型名统一传 `astron-code-latest`，不是把 35B 模型真正跑在本机。
- 如果你要“真正本地模型”，建议使用开源权重 `Qwen/Qwen3.5-35B-A3B`，通过 vLLM、SGLang、KTransformers 或 Transformers Serving 暴露 OpenAI-compatible API。
- 当前阶段最推荐组合：`Spring Boot 业务后端 + ai-service + ChromaDB + Neo4j + 本地 BGE-M3 + 讯飞 Coding Plan(Qwen3.5 35B 套餐)`。
- 当前项目已有客服入口，不建议推倒重写；保留 `/v1/customer-service/chat`，把内部 `KhojChatClient` 替换成通用 `AiGatewayClient`。
- 本阶段不部署本地大模型服务，不新增 `qwen-llm` 容器；只新增本地 embedding 服务。

## 2. 当前项目可接入点

项目已有在线客服基础：

- 后端入口：`back/src/main/java/com/web/controller/CustomerServiceController.java`
- 业务服务：`back/src/main/java/com/web/service/impl/CustomerServiceServiceImpl.java`
- 当前外部 AI 客户端：`back/src/main/java/com/web/service/impl/KhojChatClientImpl.java`
- 前端组件：`frontend/src/components/CustomerServiceChat.vue`
- 前端请求：`frontend/src/lib/customerService.ts`

现状问题：

- 现在只是搜索几个商品再拼 prompt，没有完整知识库、引用、图谱和重排。
- 客服回答无法追溯来源，涉及价格、库存、订单、退款时容易被模型编造。
- AI 供应商被 `KhojChatClient` 固定住，不方便切换 Ollama、讯飞 Coding Plan、本地 Qwen。
- 缺少 ChromaDB、Neo4j、embedding、业务工具、权限隔离、人工客服兜底。

## 3. 官网技术要点

### 3.1 Ollama

- Ollama 官方支持部分 OpenAI-compatible API，Base URL 通常是 `http://localhost:11434/v1/`。
- Ollama OpenAI 兼容接口支持 `/v1/chat/completions`、`/v1/models`、`/v1/embeddings` 等。
- Ollama 原生 embedding 接口是 `/api/embed`，请求体包含 `model` 和 `input`。
- 本方案不把 Ollama 写死，只把它作为一个 Provider，后续可替换。

### 3.2 讯飞 Astron Coding Plan

- 讯飞官网文档给出的 OpenAI 协议地址是 `https://maas-coding-api.cn-huabei-1.xf-yun.com/v2`。
- Anthropic 协议地址是 `https://maas-coding-api.cn-huabei-1.xf-yun.com/anthropic`。
- OpenAI 协议请求中，模型字段应统一传 `astron-code-latest`，实际底层模型在套餐/平台侧配置。
- 因此它适合做远程 LLM Provider；若正式给终端用户做客服，需要额外确认套餐协议是否允许生产客服场景。

### 3.3 Qwen3.5-35B-A3B

- Qwen 官方模型卡显示 `Qwen/Qwen3.5-35B-A3B` 是 Apache-2.0 许可的开源模型。
- 模型总参数 35B，激活参数约 3B，上下文很长，适合知识库客服和长上下文问答。
- 官方示例支持通过 vLLM、SGLang、KTransformers、Transformers Serving 暴露 `http://localhost:8000/v1`。
- 客服场景建议剥离 `<think>` 思考内容，只给用户最终答复。

### 3.4 ChromaDB

- ChromaDB 支持本地 `PersistentClient` 持久化。
- Collection 可以写入 `ids`、`documents`、`embeddings`、`metadatas`。
- 如果已用 BGE-M3 计算好 embedding，可以直接传给 ChromaDB，避免 ChromaDB 自己生成向量。
- Metadata 支持过滤，适合按 `source_type`、`product_id`、`category_id`、`visibility` 控制召回范围。

### 3.5 Neo4j

- Neo4j 支持 Cypher 图查询，也支持 vector index。
- 在本系统中，Neo4j 主要负责关系推理：商品-分类-SKU-政策-活动-售后规则-订单状态。
- 第一阶段不必把所有文本 chunk 都放 Neo4j；向量主检索交给 ChromaDB，Neo4j 做实体关系补证据。

### 3.6 BGE-M3

- `BAAI/bge-m3` 是 BAAI 发布的多语言 embedding 模型，维度 1024，最长 8192 tokens。
- 支持 dense、sparse、multi-vector 三种检索能力。
- 第一阶段建议先用 dense embedding + ChromaDB；后续再加 sparse 混合召回和 reranker。

## 4. 总体架构

```text
用户浏览器
  -> Vue 客服组件 CustomerServiceChat.vue
  -> Spring Boot /v1/customer-service/chat
  -> AiGatewayClient
  -> ai-service
      -> Query Router 意图识别
      -> BGE-M3 Embedding
      -> ChromaDB 向量检索
      -> Neo4j 图谱检索
      -> MySQL 只读业务工具
      -> Reranker 重排
      -> Prompt Builder
      -> LLM Provider：讯飞 CodingPlan（主）/ Ollama（备）/ 本地 Qwen（后续可选）
  -> answer + citations + actions + confidence
```

为什么要这样设计：

- ChromaDB 解决“用户怎么问都能找到相关知识”的问题。
- Neo4j 解决“商品、SKU、分类、活动、政策之间关系”的问题。
- MySQL 业务工具解决“订单、退款、库存、价格必须实时准确”的问题。
- 大模型只负责组织语言，不负责凭空生成事实。

## 5. Provider 替换方案

### 5.1 统一 LLM 配置

讯飞 Coding Plan（默认主方案）：

```env
AI_LLM_PROVIDER=xfyun_codingplan
AI_LLM_BASE_URL=https://maas-coding-api.cn-huabei-1.xf-yun.com/v2
AI_LLM_API_KEY=your_xfyun_codingplan_key
AI_LLM_MODEL=astron-code-latest
AI_LLM_TEMPERATURE=0.3
AI_LLM_MAX_TOKENS=1024
AI_LLM_STRIP_THINK=true
AI_LLM_TIMEOUT_SECONDS=120
```

本地 Qwen（当前阶段不启用，仅保留后续扩展位）：

```env
AI_LLM_PROVIDER=local_qwen
AI_LLM_BASE_URL=http://qwen-llm:8000/v1
AI_LLM_API_KEY=EMPTY
AI_LLM_MODEL=Qwen/Qwen3.5-35B-A3B
AI_LLM_TEMPERATURE=0.3
AI_LLM_MAX_TOKENS=1024
AI_LLM_STRIP_THINK=true
```

Ollama（可作为临时备用 Provider）：

```env
AI_LLM_PROVIDER=ollama
AI_LLM_BASE_URL=http://ollama:11434/v1
AI_LLM_API_KEY=ollama
AI_LLM_MODEL=qwen3.5:35b-a3b
```

### 5.2 统一 Embedding 配置

推荐 BGE-M3：

```env
AI_EMBEDDING_PROVIDER=local_bge_m3
AI_EMBEDDING_BASE_URL=http://embedding-service:9001
AI_EMBEDDING_MODEL=BAAI/bge-m3
AI_EMBEDDING_DIMENSIONS=1024
AI_EMBEDDING_NORMALIZE=true
AI_EMBEDDING_BATCH_SIZE=16
```

如果后续需要通过 Ollama 跑 embedding：

```env
AI_EMBEDDING_PROVIDER=ollama
AI_EMBEDDING_BASE_URL=http://ollama:11434
AI_EMBEDDING_MODEL=bge-m3
AI_EMBEDDING_ENDPOINT=/api/embed
```

## 6. 知识库数据设计

| 来源 | 写入位置 | 用途 |
| --- | --- | --- |
| 商品、SKU、库存、价格 | MySQL + ChromaDB + Neo4j | 售前问答、商品推荐、规格解释 |
| 分类、品牌、标签 | MySQL + Neo4j | 图谱关系、推荐约束 |
| FAQ、售后政策、平台规则 | ChromaDB + Neo4j | 规则问答、政策解释 |
| 活动、优惠券、公告 | MySQL + ChromaDB + Neo4j | 优惠解释、适用范围 |
| 订单、支付、售后状态 | MySQL 只读工具 | 个性化客服，不进入向量库 |
| 客服历史高频问题 | 脱敏后人工审核再入库 | 提升命中率 |

文档切块规则：

- 商品描述：按商品摘要、参数、卖点、售后说明拆块。
- 政策文档：按标题层级拆块，建议 500~900 中文字，重叠 80~120 字。
- FAQ：一问一答作为一个 chunk。
- 活动说明：按规则、适用品类、有效期、叠加规则拆块。
- 每个 chunk 必须带 `source_type`、`source_id`、`version`、`hash`、`updated_at`、`visibility`。

ChromaDB Collection 建议：`ecommerce_kb_v1`

```json
{
  "id": "product:123:overview:v3",
  "document": "商品卖点、参数、适用场景、售后摘要……",
  "metadata": {
    "tenant": "default",
    "source_type": "product",
    "source_id": "123",
    "product_id": 123,
    "category_id": 8,
    "visibility": "public",
    "version": 3,
    "hash": "sha256..."
  }
}
```

## 7. Neo4j 图谱设计

节点：

- `Product {id, name, price, status, stock}`
- `Sku {id, productId, attrs, price, stock}`
- `Category {id, name}`
- `Brand {id, name}`
- `Policy {id, title, type}`
- `Faq {id, question, answer, intent}`
- `Promotion {id, name, startAt, endAt}`
- `Coupon {id, code, threshold, discount}`
- `OrderStatus {code, name}`
- `AftersaleRule {id, type, days, condition}`

关系：

- `(Product)-[:BELONGS_TO]->(Category)`
- `(Product)-[:HAS_SKU]->(Sku)`
- `(Product)-[:MADE_BY]->(Brand)`
- `(Category)-[:APPLIES_POLICY]->(Policy)`
- `(Product)-[:JOIN_PROMOTION]->(Promotion)`
- `(Promotion)-[:GRANTS_COUPON]->(Coupon)`
- `(Faq)-[:EXPLAINS]->(Policy)`
- `(AftersaleRule)-[:APPLIES_TO]->(Category)`
- `(OrderStatus)-[:ALLOWS_ACTION]->(:Action {code})`

索引示例：

```cypher
CREATE CONSTRAINT product_id IF NOT EXISTS FOR (p:Product) REQUIRE p.id IS UNIQUE;
CREATE CONSTRAINT sku_id IF NOT EXISTS FOR (s:Sku) REQUIRE s.id IS UNIQUE;
CREATE CONSTRAINT policy_id IF NOT EXISTS FOR (p:Policy) REQUIRE p.id IS UNIQUE;

CREATE VECTOR INDEX product_embedding IF NOT EXISTS
FOR (p:Product)
ON p.embedding
OPTIONS { indexConfig: {
  `vector.dimensions`: 1024,
  `vector.similarity_function`: 'cosine'
}};
```

## 8. 问答流程

1. 前端提交 `message`、`conversationId`、`scene`、可选 `productId`、可选 `orderId`。
2. Spring Boot 校验登录态、限流、消息长度、敏感词。
3. Query Router 判断意图：售前、订单、支付、退款、售后、平台规则、闲聊、危险请求。
4. 涉及订单、支付、退款、物流时，调用只读业务工具；无权限则拒绝展示。
5. 用 BGE-M3 生成 query embedding，查询 ChromaDB top 20~50。
6. 用实体识别结果查询 Neo4j，补充商品、SKU、分类、政策、活动关系。
7. 融合 ChromaDB、Neo4j、业务工具结果，重排后保留 top 5~8。
8. 构建强约束 prompt，要求只基于证据回答。
9. 调用 LLM，剥离 `<think>`，检查是否包含未引用事实。
10. 返回 `answer`、`citations`、`actions`、`confidence`、`fallbackReason`。

返回格式建议：

```json
{
  "answer": "这类耳机通常支持七天无理由，但是否可退还要看订单状态和商品是否影响二次销售。请以订单详情页显示为准。",
  "confidence": 0.86,
  "citations": [
    {"sourceType": "policy", "sourceId": "return_7d", "title": "七天无理由退货规则"},
    {"sourceType": "order", "sourceId": "10086", "title": "订单状态"}
  ],
  "actions": [
    {"type": "OPEN_PAGE", "label": "查看订单详情", "url": "/orders/10086"},
    {"type": "TRANSFER_HUMAN", "label": "转人工客服"}
  ],
  "fallbackReason": null
}
```

## 9. 后端改造方案

保留接口：

```text
POST /v1/customer-service/chat
```

内部改造为：

```text
CustomerServiceController
  -> CustomerServiceService
  -> AiCustomerServiceClient
  -> ai-service /chat
```

建议新增文件：

```text
back/src/main/java/com/web/dto/CustomerServiceChatRequest.java
back/src/main/java/com/web/dto/CustomerServiceChatResponse.java
back/src/main/java/com/web/service/AiCustomerServiceClient.java
back/src/main/java/com/web/service/impl/AiCustomerServiceClientImpl.java
back/src/main/java/com/web/config/AiServiceProperties.java
```

建议配置：

```yaml
ai:
  service:
    base-url: ${AI_SERVICE_BASE_URL:http://127.0.0.1:9000}
    timeout-seconds: ${AI_SERVICE_TIMEOUT_SECONDS:120}
  customer-service:
    max-message-length: ${AI_CS_MAX_MESSAGE_LENGTH:800}
    max-history-turns: ${AI_CS_MAX_HISTORY_TURNS:8}
    enable-order-tools: ${AI_CS_ENABLE_ORDER_TOOLS:true}
```

AI 可调用的只读工具：

- 查询商品基础信息。
- 查询用户本人订单摘要。
- 查询售后申请状态。
- 查询优惠券可用性。

AI 禁止直接执行：

- 自动付款。
- 自动取消订单。
- 自动退款。
- 自动改地址。
- 自动发优惠券。

这些动作只能返回前端按钮，由用户确认后走原业务接口。

## 10. ai-service 设计

推荐用 Python FastAPI，原因是 ChromaDB、FlagEmbedding、Neo4j Python driver 和 OpenAI SDK 接入更快。
当前阶段的核心目标不是自建大模型推理，而是把远程讯飞 LLM 和本地 embedding 编排稳定接好。

目录建议：

```text
ai-service/
  app/main.py
  app/config.py
  app/api/chat.py
  app/clients/llm_client.py
  app/clients/embedding_client.py
  app/retrieval/chroma_retriever.py
  app/retrieval/neo4j_retriever.py
  app/retrieval/reranker.py
  app/ingest/mysql_loader.py
  app/ingest/document_chunker.py
  app/ingest/sync_job.py
  app/prompts/customer_service_prompt.py
  app/safety/guardrails.py
  app/schemas.py
  requirements.txt
  Dockerfile
```

核心 API：

```text
POST /chat
POST /ingest/products
POST /ingest/policies
POST /ingest/faqs
POST /admin/rebuild-index
GET  /health
GET  /metrics
```

Prompt 模板：

```text
你是本电商平台的中文在线客服。你必须遵守：
1. 只能基于【业务事实】和【知识库证据】回答。
2. 不得编造价格、库存、订单状态、退款结果、物流结果。
3. 涉及订单、支付、退款、售后进度，以系统实时查询结果为准。
4. 证据不足时明确说“目前无法确认”，并给用户下一步操作。
5. 答案要短、清楚、友好，优先给可执行步骤。
6. 不输出内部检索过程，不输出 <think> 内容。

【用户问题】
{user_message}

【业务事实】
{business_facts}

【知识库证据】
{retrieved_context}
```

## 11. 一键部署扩展

在当前 `docker-compose.prod.yml` 基础上，后续新增：

```yaml
  chroma:
    image: chromadb/chroma:latest
    container_name: projectku-chroma-prod
    restart: unless-stopped
    volumes:
      - chroma-data:/chroma/chroma
    ports:
      - "8001:8000"

  neo4j:
    image: neo4j:5-community
    container_name: projectku-neo4j-prod
    restart: unless-stopped
    environment:
      NEO4J_AUTH: neo4j/${NEO4J_PASSWORD:-12345678}
      NEO4J_server_memory_heap_initial__size: 512m
      NEO4J_server_memory_heap_max__size: 2G
    volumes:
      - neo4j-data:/data
      - neo4j-logs:/logs

  ai-service:
    build:
      context: ./ai-service
    container_name: projectku-ai-service-prod
    restart: unless-stopped
    environment:
      AI_LLM_BASE_URL: ${AI_LLM_BASE_URL:-http://qwen-llm:8000/v1}
      AI_LLM_API_KEY: ${AI_LLM_API_KEY:-EMPTY}
      AI_LLM_MODEL: ${AI_LLM_MODEL:-Qwen/Qwen3.5-35B-A3B}
      CHROMA_HOST: chroma
      CHROMA_PORT: 8000
      NEO4J_URI: bolt://neo4j:7687
      NEO4J_USER: neo4j
      NEO4J_PASSWORD: ${NEO4J_PASSWORD:-12345678}
    depends_on:
      - chroma
      - neo4j

volumes:
  chroma-data:
  neo4j-data:
  neo4j-logs:
```

如果用讯飞 Coding Plan，不需要部署 `qwen-llm`，当前推荐直接删掉这一层。
当前推荐新增一个 `embedding-service` 容器或进程，专门加载 `BAAI/bge-m3`。

## 12. 服务器资源评估

当前目标是不本地跑 35B，只用讯飞 Coding Plan：

- 最低：`2核4G`，只适合小数据量和低并发。
- 推荐：`4核8G`，可跑 MySQL、Spring Boot、Nginx、ChromaDB、Neo4j、ai-service、本地 embedding。
- 更稳：`4核16G`，适合生产早期。

大致内存：

- MySQL：600MB~1.2GB
- Spring Boot：400MB~900MB
- Nginx：20MB~80MB
- ChromaDB：300MB~1.5GB
- Neo4j：1GB~3GB
- ai-service：500MB~2GB，不加载大模型时较轻
- embedding-service：2GB~6GB，取决于 BGE-M3 的加载方式和并发
- 合计：约 4GB~8GB 起步，生产建议 8GB~16GB。

本地跑 BGE-M3：

- BGE-M3 模型大小约 2GB 级别。
- CPU 可跑但慢；推荐 8GB 以上内存，最好有 4GB~8GB GPU 显存。

本阶段不部署本地 Qwen3.5-35B-A3B：

- 直接调用讯飞 Coding Plan，避免本地 35B 推理的 GPU 成本和维护复杂度。
- 如果后续因成本、合规或延迟需要切回本地 Qwen，再单独规划 AI 推理服务器。

## 13. 分阶段落地计划

### Phase 1：最小可用 RAG 客服

- 新增 `ai-service`。
- 接入 BGE-M3 embedding。
- 配置讯飞 Coding Plan 作为默认 LLM Provider。
- 接入 ChromaDB。
- 导入 FAQ、售后政策、商品摘要。
- Spring Boot 将 `KhojChatClient` 替换为 AI 网关客户端。
- 前端继续使用现有客服组件。

### Phase 2：GraphRAG 增强

- 新增 Neo4j。
- 同步商品、分类、SKU、政策、活动、FAQ 关系。
- 售后、优惠、适用品类问题走 Neo4j 补证据。
- 前端展示引用来源和推荐动作。

### Phase 3：业务工具与安全

- 增加只读订单、支付、售后工具。
- 增加权限校验和字段脱敏。
- 增加危险操作拦截和人工客服兜底。
- 加日志、指标、失败率、平均延迟、召回命中率。

### Phase 4：效果优化

- 增加 reranker。
- BGE-M3 dense + sparse 混合召回。
- 客服历史问题脱敏后人工审核入库。
- 建立 100~300 条电商问题评测集，持续评估命中率、幻觉率、满意度。

## 14. 验收标准

- `POST /v1/customer-service/chat` 正常返回结构化结果。
- ChromaDB 能检索 FAQ、政策、商品 chunk，并返回 metadata。
- Neo4j 能查询商品到分类、政策、活动的路径。
- 用户未登录时不能查询订单；登录后只能查本人订单摘要。
- 用户问退款结果时，模型不能承诺“已退款”，只能引用系统状态或引导申请售后。
- LLM Provider 默认使用 `xfyun_codingplan`，并可通过环境变量切换到 `ollama` 或 `local_qwen`。
- Embedding Provider 可通过环境变量在 `local_bge_m3`、`ollama_bge_m3` 之间切换。
- 一键启动脚本能检查 MySQL、后端、前端、ChromaDB、Neo4j、AI 服务健康状态。

## 15. 参考资料

- Ollama OpenAI compatibility：https://docs.ollama.com/api/openai-compatibility
- Ollama embeddings API：https://docs.ollama.com/api/embed
- 讯飞星辰 MaaS Astron Coding Plan：https://www.xfyun.cn/doc/spark/CodingPlan.html
- Qwen3.5-35B-A3B：https://huggingface.co/Qwen/Qwen3.5-35B-A3B
- ChromaDB clients：https://docs.trychroma.com/docs/run-chroma/clients
- ChromaDB add data：https://docs.trychroma.com/docs/collections/add-data
- ChromaDB embedding functions：https://docs.trychroma.com/docs/embeddings/embedding-functions
- Neo4j vector indexes：https://neo4j.com/docs/cypher-manual/current/indexes/semantic-indexes/vector-indexes/
- BGE-M3：https://huggingface.co/BAAI/bge-m3
