# 支付 MVP 说明

本项目当前支付采用“真实余额支付 + 第三方模拟通道”的设计。

## 支付入口

- 收银台页面：`/cashier?orderId=订单ID`
- 支付结果页：`/pay-result?orderId=订单ID&channel=balance&autoPay=1`
- 发起支付接口：`POST /api/v1/payments/{orderId}/pay`
- 查询支付状态：`GET /api/v1/payments/{orderId}/status`
- 模拟回调：`POST /api/v1/payments/webhook`
- 支付宝正式异步回调：`POST /api/v1/payments/alipay/notify`

## 支付渠道

- `balance`：余额支付，后端真实扣减用户余额。
- `alipay`、`wechat`、`unionpay`：模拟第三方支付，创建待支付记录后通过模拟回调完成支付。

## 余额支付

余额支付会在后端事务内执行：

1. 校验订单属于当前用户。
2. 校验订单是待支付状态。
3. 原子扣减余额，余额不足则失败。
4. 写入钱包流水。
5. 关闭同订单旧的待支付记录。
6. 写入成功支付记录。
7. 更新订单为已支付。

余额扣减使用条件更新，避免并发扣成负数：

```sql
UPDATE user_wallets
SET balance = balance - :amount
WHERE user_id = :userId AND balance >= :amount;
```

## 第三方模拟支付

第三方通道现在只用于本地和演示测试：

- 同一订单同一渠道已有 `PENDING` 支付记录时，会复用旧交易号，不重复创建。
- 切换渠道时，会把旧的 `PENDING` 记录标记为 `FAILED`，再创建新支付记录。
- 第三方支付失败只标记支付失败，不直接取消订单，用户可以重新支付。
- 订单超时或用户取消时，才会取消订单并恢复库存。

## 支付宝正式模式

当 `ALIPAY_MODE=sdk` 时，后端会使用支付宝电脑网站支付 SDK 生成支付表单，前端会自动打开支付宝页面。需要准备：

- `ALIPAY_APP_ID`
- `ALIPAY_MERCHANT_PRIVATE_KEY`
- `ALIPAY_PUBLIC_KEY`
- `ALIPAY_NOTIFY_URL`
- `ALIPAY_RETURN_URL`

沙箱默认网关：

```text
https://openapi-sandbox.dl.alipaydev.com/gateway.do
```

如果这些配置没填完整，系统会自动回退到 mock 模式，不会影响余额支付。

支付宝异步回调会执行：

1. 使用支付宝公钥验签。
2. 校验 `out_trade_no` 对应的支付流水存在。
3. 校验支付渠道必须是 `alipay`。
4. 校验 `total_amount` 与后端支付记录金额一致。
5. 将 `TRADE_SUCCESS` / `TRADE_FINISHED` 幂等更新为支付成功。
6. 将订单状态更新为已支付。

生产环境不要依赖前端跳转判断支付成功，必须以后端异步回调验签通过为准。前端模拟支付自动完成只在开发环境默认启用，生产环境如需演示才显式设置：

```env
VITE_ENABLE_MOCK_PAYMENT_AUTOCOMPLETE=true
```

## 微信支付预留模式

微信通道也已经拆成独立适配层，默认仍是 mock。后续接入微信 Native 支付时需要准备：

- `WECHAT_PAY_APP_ID`
- `WECHAT_PAY_MCH_ID`
- `WECHAT_PAY_API_V3_KEY`
- `WECHAT_PAY_MERCHANT_PRIVATE_KEY`
- `WECHAT_PAY_MERCHANT_SERIAL_NO`
- `WECHAT_PAY_NOTIFY_URL`

微信支付比支付宝更依赖商户资质、API v3 证书/私钥、回调验签和平台证书更新，所以当前 `WECHAT_PAY_MODE=sdk` 只做配置校验和预留，不会伪装成生产可用。

## 上线真实支付还需要补充

接入支付宝或微信支付时，需要新增：

- 商户号、应用 ID、私钥/证书配置。
- 服务端创建真实支付单。
- 支付网关回调验签。
- 回调金额、订单号、商户号校验。
- 支付回调幂等处理。
- 退款接口和售后退款联动。

在没有真实商户配置前，不要把当前模拟 webhook 当作生产支付能力。
