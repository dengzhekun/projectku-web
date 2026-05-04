# AI 接手交接文档

更新时间：2026-05-01  
仓库：`https://github.com/dengzhekun/projectku-web`  
当前本地完整项目目录：`D:\web-main`

> 这份文档是给新的 AI / 开发者接手 ProjectKu 电商项目用的。接手后先读本文，再读 `README.md`、`HANDOFF.md`、`docs/ai-service-runbook.md` 和 `deploy/README.md`。

## 0. 接手第一原则

1. 不要直接更新服务器，除非用户明确说“部署/更新核云服务器”。
2. 不要停止或重建 `metapi`，它和电商项目是同一台服务器上的独立服务。
3. 不要提交真实密钥、Token、数据库密码、Metapi 配置。
4. 不要把 `C:\Users\Administrator\Desktop\web-main` 当成完整项目。完整仓库在 `D:\web-main`。
5. 部署前必须备份服务器 `/opt/evanshine-shop/current`，并验证后再说部署成功。

## 1. 当前项目状态

当前本地/GitHub 已完成到：

- 分支：`main`
- 最新提交：`af885af feat: complete lightrag customer service hardening`
- 最新 tag：`v0.1.7`
- Release：`https://github.com/dengzhekun/projectku-web/releases/tag/v0.1.7`

核心能力：

- Vue 3 商城前台
- Spring Boot 3 后端 API
- MySQL 8 数据库
- FastAPI AI 客服服务
- LightRAG 知识库运行态
- 管理后台知识库文档管理、索引、命中日志、未命中日志
- 商品实时查询工具：商品名、价格、库存、SKU
- 钱包余额、余额支付、优惠券门槛、订单、购物车、评价、售后
- Windows 便携运行入口：`run-portable.bat`

重要变化：

- AI 客服已经切到 LightRAG-only 路径，Chroma 不再作为生产运行时。
- 客服商品查询支持更严格的意图识别：
  - `苹果多少钱` 应澄清“苹果”过宽，不能直接当 iPhone。
  - `苹果15` / `iPhone 15` 才应识别为手机类商品。
  - `苹果15ProMax` / `iphone15promax` / `iPhone SE` 有别名处理。
- 后端增加 `AuthTokenService`，AI 客服透传业务接口时只能使用验证过的 Bearer token。
- 伪造 token 查询个人订单、钱包、优惠券等应返回 401。

## 2. 本地运行入口

完整仓库在：

```powershell
cd D:\web-main
```

最简单的 Windows 便携入口：

```powershell
.\run-portable.bat
```

检查便携环境：

```powershell
.\run-portable.bat doctor
```

停止本地便携运行：

```powershell
.\run-portable.bat stop
```

传统开发启动：

```powershell
.\start_all.ps1 -Mode dev -InstallAiDeps -SeedAiKb
```

前端默认：

- `http://127.0.0.1:5173/`
- 管理后台知识库：`http://127.0.0.1:5173/admin/kb`

后端默认：

- `http://127.0.0.1:8080/api`
- Swagger：`http://localhost:8080/api/swagger-ui-custom.html`

AI 服务默认：

- `http://127.0.0.1:9000/health`

## 3. 目录导航

```text
D:\web-main
├── frontend/      Vue 3 + Vite 前端
├── back/          Spring Boot 后端
├── ai-service/    FastAPI AI 客服与 LightRAG 适配
├── deploy/        生产部署模板、核云部署 compose、环境模板
├── docs/          设计、部署、知识库、运行手册
├── scripts/       本地验证、LightRAG 检查、KB 导入脚本
├── k6/            k6 性能 smoke 脚本
└── run-portable.bat / package-portable.bat
```

重点代码位置：

| 功能 | 位置 |
| --- | --- |
| AI 客服后端入口 | `back/src/main/java/com/web/controller/CustomerServiceController.java` |
| 后端调用 AI 服务 | `back/src/main/java/com/web/service/impl/AiCustomerServiceClientImpl.java` |
| 后端客服编排 | `back/src/main/java/com/web/service/impl/CustomerServiceServiceImpl.java` |
| Token 验证 | `back/src/main/java/com/web/security/AuthTokenService.java` |
| 知识库后台 API | `back/src/main/java/com/web/controller/KnowledgeBaseController.java` |
| 知识库业务逻辑 | `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java` |
| 商品接口 | `back/src/main/java/com/web/controller/ProductController.java` |
| 订单结算/库存 | `back/src/main/java/com/web/service/impl/OrderServiceImpl.java` |
| 支付/余额支付 | `back/src/main/java/com/web/service/impl/PaymentServiceImpl.java` |
| AI 服务聊天路由 | `ai-service/app/api/chat.py` |
| AI 服务流式聊天 | `ai-service/app/api/chat.py` 的 `/chat/stream` |
| 商品实时查询工具 | `ai-service/app/clients/product_tool_client.py` |
| 订单/钱包/优惠券工具 | `ai-service/app/clients/business_tool_client.py` |
| LightRAG 客户端 | `ai-service/app/clients/lightrag_client.py` |
| LightRAG 检索器 | `ai-service/app/retrieval/lightrag_retriever.py` |
| LightRAG 文档映射 | `ai-service/app/retrieval/lightrag_doc_registry.py` |
| 客服提示词 | `ai-service/app/prompts/customer_service_prompt.py` |
| 管理后台知识库页面 | `frontend/src/views/KnowledgeBaseAdminView.vue` |
| 前端客服调用 | `frontend/src/lib/customerService.ts` |
| 商品详情 SKU/库存 | `frontend/src/views/ProductDetailView.vue` |
| 购物车 | `frontend/src/views/CartView.vue` |
| 结算优惠券 | `frontend/src/views/CheckoutView.vue` |
| 收银台余额支付 | `frontend/src/views/CashierView.vue` |

## 4. AI 客服当前设计

请求路径：

```text
前端客服窗口
  -> 后端 /api/v1/customer-service/chat 或 /chat/stream
  -> 后端验证 token，只透传可信 token
  -> ai-service /chat 或 /chat/stream
  -> 路由判断
     -> 商品价格/库存/清单：实时查后端商品 API
     -> 订单/钱包/优惠券个人信息：需要登录 token，走后端业务 API
     -> 售后/物流/优惠券规则：走 LightRAG 知识库
     -> 查不到：按客服安全话术澄清，不瞎编
```

需要保持的行为：

- `苹果多少钱`：不能直接识别为 iPhone，应说明“苹果”可能是水果或词太宽，并追问具体商品。
- `苹果15多少钱`：可以查 iPhone 15 相关商品，但如果版本/内存不明确，应列出候选或追问规格。
- `苹果15 Pro Max 多少钱`：应尽量命中对应商品别名。
- `我的订单到哪了`：未登录应提示登录。
- 带伪造 token 的个人查询：应 401，不能把伪造 token 传给 AI 工具继续查。
- 售后、物流、优惠券规则问题：优先知识库，不应错误要求登录。

## 5. LightRAG / 知识库状态

当前目标是 LightRAG-only，不再继续依赖 Chroma。

相关文档：

- `docs/ai-service-runbook.md`
- `docs/integrations/lightrag-runtime-readiness.md`
- `docs/integrations/lightrag-server-contract.md`
- `docs/integrations/lightrag-embedding-gateway.md`
- `docs/knowledge-base/ai-cs-regression-cases.md`
- `docs/knowledge-base/seed/`

知识库种子文档：

- `docs/knowledge-base/seed/after-sales-policy.md`
- `docs/knowledge-base/seed/coupon-rules.md`
- `docs/knowledge-base/seed/logistics-rules.md`
- `docs/knowledge-base/seed/product-shopping-guide.md`
- `docs/knowledge-base/seed/refund-payment-rules.md`

批量导入/同步：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\import_kb_seed_documents.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\sync_kb_to_lightrag.ps1
```

LightRAG 运行态验证：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-lightrag-runtime.ps1 -CheckEmbeddingGateway
```

AI 客服回归：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-ai-cs-regression.ps1
```

内置回归用例目前重点覆盖：

- `苹果多少钱`
- `售后质量问题退回运费谁承担`
- `优惠券没到门槛为什么不能用`
- `物流一直不动怎么办`

## 6. 核云服务器信息

已知核云服务器：

- IP：`38.76.178.91`
- SSH key：`C:\Users\Administrator\.ssh\evanshine_khoj`
- 主机名：`ser5825434836`

只读检查命令：

```powershell
ssh -i $env:USERPROFILE\.ssh\evanshine_khoj -o StrictHostKeyChecking=no root@38.76.178.91 "hostname; docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'; docker compose ls"
```

最近一次探测到的运行服务：

| 容器 | 状态/端口 |
| --- | --- |
| `evanshine-shop-backend` | `127.0.0.1:18080->8080` |
| `evanshine-shop-mysql` | MySQL，healthy |
| `evanshine-shop-lightrag-lite` | `127.0.0.1:19621->9621` |
| `metapi` | `127.0.0.1:14000->4000` |

Compose 项目：

- 电商项目：`/opt/evanshine-shop/current/docker-compose.yml`
- Metapi：`/opt/metapi/docker-compose.yml`

重要提醒：

- 用户已明确说“先别更新”，因此当前不要部署。
- 服务器上的电商后端不一定已经是 `v0.1.7`。必须部署并验证后才能说“核云已更新好”。
- `/opt/evanshine-shop/current` 可能不是 git repo，不能默认 `git pull`。
- 如果要部署，只更新 `evanshine-shop`，不要碰 `metapi`。

## 7. 未来服务器部署建议

只有用户明确要求部署时再执行。

推荐步骤：

1. 只读确认服务器状态。
2. 备份当前目录。
3. 同步本地 `v0.1.7` 后端相关文件到 `/opt/evanshine-shop/current`。
4. 保留服务器现有端口、volume、MySQL 数据。
5. 如果 compose 仍有旧字段 `CHROMA_COLLECTION`，改为 `LIGHTRAG_COLLECTION`。
6. 只 rebuild/restart backend，不动 Metapi。
7. 做 smoke 验证。

示例备份：

```bash
mkdir -p /opt/evanshine-shop/backups
tar --exclude='./mysql-data' --exclude='./logs' -czf /opt/evanshine-shop/backups/current-$(date +%Y%m%d-%H%M%S).tgz -C /opt/evanshine-shop current
```

后端重建：

```bash
cd /opt/evanshine-shop/current
docker compose up -d --build backend
```

服务器验证：

```bash
curl -sS http://127.0.0.1:18080/api/
```

伪造 token 必须 401：

```bash
curl -i -sS -X POST http://127.0.0.1:18080/api/v1/customer-service/chat \
  -H 'Authorization: Bearer fake-token' \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data '{"message":"我的订单到哪了","conversationId":"server-fake-token-smoke"}'
```

未登录个人查询应提示登录：

```bash
curl -sS -X POST http://127.0.0.1:18080/api/v1/customer-service/chat \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data '{"message":"我的订单到哪了","conversationId":"server-anon-smoke"}'
```

商品宽词澄清：

```bash
curl -sS -X POST http://127.0.0.1:18080/api/v1/customer-service/chat \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data '{"message":"苹果多少钱","conversationId":"server-apple-wide-smoke"}'
```

## 8. 本地验证命令

后端单测：

```powershell
cd D:\web-main\back
mvn test
```

AI 服务单测：

```powershell
cd D:\web-main\ai-service
python -m pytest
```

前端构建：

```powershell
cd D:\web-main\frontend
npm install
npm run build
```

前端编码回归：

```powershell
cd D:\web-main
node scripts/verify_frontend_text_encoding.js
```

Playwright smoke：

```powershell
cd D:\web-main\frontend
npm run test:e2e:install
npm run test:e2e
```

k6 checkout smoke：

```powershell
cd D:\web-main
k6 run k6/checkout-smoke.js
```

## 9. 近期容易误判的问题

### 9.1 “苹果多少钱”不是 bug

现在期望行为是澄清，不直接识别成 iPhone。原因是“苹果”默认可以是水果，只有“苹果15 / iPhone 15 / 苹果15ProMax”这类带型号的才应进入手机商品查询。

如果用户说“之前可以识别苹果15”，要重点检查：

- `ai-service/app/api/chat.py`
- `ai-service/app/clients/product_tool_client.py`
- `docs/knowledge-base/ai-cs-regression-cases.md`

### 9.2 后台命中日志可能没有 chunk id

LightRAG 当前返回的是 answer-level citation。客服回答可以命中文档事实，但后台命中日志不一定能精确到某个 chunk id。

这是来源追踪精细化问题，不等于知识库没命中。后续如果要做精细化来源追踪，需要在 LightRAG 查询返回、metadata codec、backend hit log 三处打通。

### 9.3 购物车不应该立刻扣库存

加入购物车不扣库存。库存应在订单结算/支付相关流程中做原子校验和扣减，避免两个用户同时买最后一个库存时超卖。

相关文件：

- `back/src/main/java/com/web/service/impl/OrderServiceImpl.java`
- `back/src/main/resources/mapper/OrderMapper.xml`
- `back/src/main/resources/mapper/ProductMapper.xml`

### 9.4 余额支付

注册用户默认赠送余额逻辑已经加入，现有用户补余额也做过。后续改余额相关逻辑时检查：

- `back/src/main/java/com/web/service/impl/UserServiceImpl.java`
- `back/src/main/java/com/web/service/impl/PaymentServiceImpl.java`
- `back/src/main/java/com/web/mapper/UserWalletMapper.java`
- `frontend/src/stores/wallet.ts`
- `frontend/src/views/CashierView.vue`

### 9.5 C 盘爆满相关

项目已迁移到 `D:\web-main`。C 盘上的 `C:\Users\Administrator\Desktop\web-main` 不是完整仓库，可能只是便携脚本残留/轻量目录。不要在 C 盘继续跑完整依赖下载。

## 10. 推荐给下一个 AI 的任务顺序

如果用户说“继续优化项目”，建议按这个顺序：

1. 先确认用户是否要本地优化、GitHub 发版，还是核云部署。
2. 如果是本地优化：
   - 运行后端/AI/前端测试。
   - 重点看 AI 客服回归、LightRAG runtime、前端乱码和构建。
3. 如果是部署：
   - 先备份服务器。
   - 只更新 `/opt/evanshine-shop/current`。
   - 只重启 `evanshine-shop-backend` 或必要服务。
   - 不动 `metapi`。
4. 如果是知识库：
   - 先在后台导入/索引真实文档。
   - 再跑 `run-ai-cs-regression.ps1`。
   - 命中日志为空时先判断是不是 answer-level citation 限制。
5. 如果是性能测试：
   - JMeter/k6 测试文件应尽量放在项目外 `D:\ProjectKuTest\JmeterTest`，不要污染主仓库。
   - 被测项目必须先启动。

## 11. 可直接发给用户的当前结论模板

当接手 AI 被问“现在项目什么状态”时，可以这样答：

```text
目前本地和 GitHub 已到 v0.1.7，LightRAG-only AI 客服、商品实时查询、Token 鉴权、余额/优惠券/库存相关优化都已经在代码里。核云服务器我需要先确认是否已经部署 v0.1.7，不能直接假设服务器是最新。下一步如果你要部署，我会先备份 /opt/evanshine-shop/current，只更新电商项目，不碰 Metapi，然后跑伪造 token、苹果宽词、售后/优惠券/物流知识库回归。
```

## 12. 禁止事项

- 不要把服务器 Metapi 当成电商项目的一部分重建。
- 不要删除 Docker volume，尤其是 MySQL volume。
- 不要 `git reset --hard` 用户本地工作区。
- 不要用 `docker compose down -v`。
- 不要把真实 env 文件提交到 GitHub。
- 不要在没有验证的情况下说“已经部署成功”。

