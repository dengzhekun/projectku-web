# 统一后台（Admin Console）说明

本文档用于快速说明当前后台入口、默认账号、知识库到 LightRAG 的操作流程，以及支付看板用途和部署注意事项。

## 1. 后台入口与路由

- 管理员登录页：`/admin/login`
- 统一后台首页：`/admin`
- 知识库后台：`/admin/kb`
- 支付订单看板：`/admin/payments`

路由位于 `frontend/src/router/index.ts`，`/admin`、`/admin/kb` 与 `/admin/payments` 都要求管理员登录（`requiresAdmin: true`）。

## 2. 默认账号与鉴权

- 默认管理员账号：`admin`
- 默认密码：`123456`

来源：
- 前端登录页提示：`frontend/src/views/AdminLoginView.vue`
- 数据库初始化/迁移：`back/sql/init_db.sql`、`back/sql/migration_admin_user.sql`

后台鉴权机制：
- 前端通过 `/v1/auth/login` 获取 token（`frontend/src/stores/adminAuth.ts`）。
- 后端拦截 `/v1/kb/**` 与 `/v1/admin/**`，且仅允许 `account=admin` 访问（`back/src/main/java/com/web/interceptor/AuthInterceptor.java`）。

## 3. 后台菜单与页面职责

### 统一后台首页（`/admin`）

主要能力：
- 汇总后台入口
- 跳转知识库管理
- 跳转支付订单看板
- 返回前台或退出管理员登录

页面实现：`frontend/src/views/AdminHomeView.vue`
后台壳组件：`frontend/src/components/admin/AdminShell.vue`

### 知识库后台（`/admin/kb`）

主要能力：
- 创建/上传文档（`txt`、`md`、`docx`）
- 文档切分（chunk）
- 单文档索引、批量同步到 LightRAG
- 查看索引记录、命中日志、未命中问题、客服查询日志
- 查看同步健康（`sync-health`）

页面实现：`frontend/src/views/KnowledgeBaseAdminView.vue`
接口入口：`/v1/kb/documents/**`（`back/src/main/java/com/web/controller/KnowledgeBaseController.java`）

### 支付订单看板（`/admin/payments`）

主要能力（只读）：
- 查看支付与订单统计卡片
- 查看最近订单
- 查看最近支付流水
- 查看最近余额流水

页面实现：`frontend/src/views/AdminPaymentsView.vue`
接口入口：`/v1/admin/payments/overview`（`back/src/main/java/com/web/controller/AdminPaymentController.java`）

## 4. 知识库导入到 LightRAG（后台操作流）

建议流程：

1. 登录 `/admin/login` 后进入 `/admin` 或 `/admin/kb`。
2. 通过“创建手工文档”或“上传文档”录入内容。
3. 对目标文档执行“切分”。
4. 对目标文档执行“索引”（或使用“批量同步到 LightRAG”）。
5. 在“索引记录 / 命中日志 / 同步健康”确认结果。
6. 到用户侧客服入口提问，回到后台检查命中日志与未命中问题并迭代文档。

相关后端与运行时存储：
- 文档元数据、chunk、索引记录、日志：MySQL
- 上传源文件：`back/storage/kb/<documentId>/`
- 检索运行时：LightRAG（依赖 PostgreSQL/Neo4j）

## 5. 支付看板用途

支付看板定位为**运维/排障只读页面**，主要用于：
- 快速判断订单状态分布（待支付/已支付/取消等）
- 排查支付流水状态（`PENDING/SUCCESS/FAILED`）
- 核对余额相关流水（注册赠送、余额支付）

不用于执行订单改写、退款、补单等写操作。

## 6. 部署注意事项

- 生产环境默认入口：
  - 前台：`http://SERVER_IP/`
  - 统一后台首页：`http://SERVER_IP/admin`
  - 知识库后台：`http://SERVER_IP/admin/kb`
- LightRAG 生产检索路径为 `ai-service -> LightRAG -> PostgreSQL/Neo4j`。
- `deploy/ai-service.env`、`deploy/lightrag.env`、`deploy/prod.env` 为敏感配置文件，不入库。
- LightRAG 健康接口可用不代表 embedding 链路一定可用；索引/查询异常时需同时检查 embedding 网关与上游 embedding 服务。

参考文档：
- `deploy/README.md`
- `docs/deployment.md`
- `docs/ai-service-runbook.md`
