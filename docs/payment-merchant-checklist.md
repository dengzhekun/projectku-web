# 支付宝 / 微信支付开通资料与项目配置清单

本清单用于元气购电商项目接入真实支付。开发阶段可以继续使用 `mock` 或支付宝沙箱；真正上线收款前，需要完成商户资料、应用审核、回调域名和密钥配置。

## 推荐顺序

1. 先用余额支付验证完整下单链路。
2. 再接支付宝沙箱，跑通“下单 -> 支付宝页面 -> 异步回调 -> 订单已支付”。
3. 支付宝正式支付审核通过后切正式网关。
4. 最后接微信 Native 支付，因为微信商户资料、证书和回调验签要求更重。

## 支付宝资料

开通真实支付宝支付一般需要：

- 支付宝开放平台账号。
- 支付宝商家平台账号。
- 营业执照。
- 法人身份证。
- 对公银行账户。
- 可访问的业务网站或应用。
- 已备案域名，正式上线强烈建议准备。

项目上线前需要拿到：

- `ALIPAY_APP_ID`
- `ALIPAY_MERCHANT_PRIVATE_KEY`
- `ALIPAY_PUBLIC_KEY`
- `ALIPAY_NOTIFY_URL`
- `ALIPAY_RETURN_URL`

本项目环境变量：

```env
ALIPAY_MODE=mock
ALIPAY_GATEWAY_URL=https://openapi-sandbox.dl.alipaydev.com/gateway.do
ALIPAY_APP_ID=
ALIPAY_MERCHANT_PRIVATE_KEY=
ALIPAY_PUBLIC_KEY=
ALIPAY_NOTIFY_URL=https://你的域名/api/v1/payments/alipay/notify
ALIPAY_RETURN_URL=https://你的域名/pay-result
ALIPAY_SIGN_TYPE=RSA2
ALIPAY_SUBJECT_PREFIX=元气购订单
```

启用支付宝 SDK 模式：

```env
ALIPAY_MODE=sdk
```

## 微信支付资料

开通真实微信支付一般需要：

- 微信支付商户平台账号。
- 营业执照。
- 法人身份证。
- 银行账户。
- 商户号。
- 绑定的 `APPID`，通常来自公众号、小程序、开放平台应用或网页支付场景。
- 已备案域名，网页/H5/Native 场景通常需要。

项目上线前需要拿到：

- `WECHAT_PAY_APP_ID`
- `WECHAT_PAY_MCH_ID`
- `WECHAT_PAY_API_V3_KEY`
- `WECHAT_PAY_MERCHANT_PRIVATE_KEY`
- `WECHAT_PAY_MERCHANT_SERIAL_NO`
- `WECHAT_PAY_NOTIFY_URL`

本项目环境变量：

```env
WECHAT_PAY_MODE=mock
WECHAT_PAY_APP_ID=
WECHAT_PAY_MCH_ID=
WECHAT_PAY_API_V3_KEY=
WECHAT_PAY_MERCHANT_PRIVATE_KEY=
WECHAT_PAY_MERCHANT_SERIAL_NO=
WECHAT_PAY_NOTIFY_URL=https://你的域名/api/v1/payments/wechat/notify
WECHAT_PAY_RETURN_URL=https://你的域名/pay-result
WECHAT_PAY_DESCRIPTION_PREFIX=元气购订单
```

注意：当前项目已经预留微信支付适配层，但 `WECHAT_PAY_MODE=sdk` 还没有接真实微信 Native 下单和回调验签。拿到商户资料后再实现。

## 回调域名要求

支付平台必须能公网访问后端回调地址。

推荐结构：

```text
https://你的域名/api/v1/payments/alipay/notify
https://你的域名/api/v1/payments/wechat/notify
```

要求：

- 域名解析到服务器。
- HTTPS 可访问。
- Nginx / 反向代理能转发到后端 `/api`。
- 支付回调接口不能依赖用户登录 token。
- 后端必须做验签、金额校验、订单号校验和幂等更新。

## 当前项目代码状态

已有能力：

- 余额支付：真实扣余额。
- 支付宝：已接 SDK 适配层，默认 mock，配置完整后可生成支付宝电脑网站支付表单。
- 微信：已接独立适配层，默认 mock，真实 SDK 下单和回调验签待商户资料齐全后实现。
- 支付失败不会直接取消订单，可以重试支付。
- 同一订单同一支付渠道会复用待支付交易号，避免重复创建。

重要文件：

- `back/src/main/java/com/web/service/impl/PaymentServiceImpl.java`
- `back/src/main/java/com/web/service/payment/AlipayPaymentGatewayImpl.java`
- `back/src/main/java/com/web/service/payment/WechatPaymentGatewayImpl.java`
- `back/src/main/resources/application.yml`
- `back/src/main/resources/application-prod.yml`
- `frontend/src/views/PayResultView.vue`
- `docs/payment-mvp.md`

## 安全注意事项

- 私钥、API v3 Key、SMTP 授权码都不能提交到 GitHub。
- 只放在服务器 `.env`、宝塔环境变量或云平台密钥管理里。
- 回调接口不能只靠前端跳转判断支付成功。
- 支付成功必须以支付平台异步回调验签通过为准。
- 金额必须以后端订单金额为准，不能信任前端传入金额。
