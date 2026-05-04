# 邮箱验证码配置说明

本项目的邮箱验证码采用“后端生成和校验，邮箱平台只负责发送”的设计。

## 当前接口

- `POST /api/v1/auth/email-code`
  - 请求：`{ "email": "user@example.com" }`
  - 用途：发送注册验证码
- `POST /api/v1/auth/register`
  - 请求：`{ "account": "user@example.com", "password": "123456", "nickname": "用户", "emailCode": "123456" }`
  - 用途：校验验证码后注册用户
- `POST /api/v1/auth/login-code`
  - 请求：`{ "email": "user@example.com" }`
  - 用途：发送邮箱验证码登录验证码
- `POST /api/v1/auth/login-with-code`
  - 请求：`{ "account": "user@example.com", "emailCode": "123456" }`
  - 用途：校验邮箱验证码后签发登录 token
- `POST /api/v1/auth/password-reset-code`
  - 请求：`{ "email": "user@example.com" }`
  - 用途：发送找回密码验证码
  - 说明：为避免泄露账号是否存在，发送验证码接口统一返回发送成功；只有账号存在时才会真实发送邮件
- `POST /api/v1/auth/reset-password`
  - 请求：`{ "account": "user@example.com", "password": "newpass123", "emailCode": "123456" }`
  - 用途：校验找回密码验证码后重置密码

## 本地开发

默认模式是 `console`，不会真实发邮件。验证码会打印在后端日志里：

```text
Email verification code [123456] for user@example.com purpose=REGISTER expiresIn=10min
```

验证码登录日志里的用途是 `purpose=LOGIN`，找回密码验证码日志里的用途是 `purpose=RESET_PASSWORD`。

本地不需要配置 SMTP。

## 生产 SMTP 配置

上线真实发邮件时设置：

```env
EMAIL_CODE_MODE=smtp
MAIL_HOST=smtp.example.com
MAIL_PORT=465
MAIL_USERNAME=notice@example.com
MAIL_PASSWORD=邮箱授权码或应用密码
MAIL_SSL_ENABLE=true
MAIL_STARTTLS_ENABLE=false
EMAIL_SENDER_NAME=元气购
EMAIL_CODE_SUBJECT_PREFIX=元气购验证码
```

QQ 邮箱示例：

```env
EMAIL_CODE_MODE=smtp
MAIL_HOST=smtp.qq.com
MAIL_PORT=587
MAIL_USERNAME=你的QQ邮箱@qq.com
MAIL_PASSWORD=QQ邮箱生成的SMTP授权码
MAIL_SSL_ENABLE=false
MAIL_STARTTLS_ENABLE=true
```

常见端口：

- SSL：`465`，`MAIL_SSL_ENABLE=true`，`MAIL_STARTTLS_ENABLE=false`
- STARTTLS：`587`，`MAIL_SSL_ENABLE=false`，`MAIL_STARTTLS_ENABLE=true`

多数邮箱平台要求使用“授权码 / 应用密码”，不是网页登录密码。

## 验证码规则

- 6 位数字
- 默认 10 分钟过期
- 同邮箱 60 秒内不能重复发送
- 同邮箱同用途每小时最多 5 次
- 错误最多 5 次，超过后需要重新获取
- 数据库存储验证码哈希，不存明文
- 注册或重置密码成功后验证码会标记为已使用，不能重复使用

## OpenClaw / Hermes / 网易 Agent 邮箱

OpenClaw、Hermes 或网易 Agent 邮箱适合做 Agent 的收发信工作流。如果它们提供 SMTP，直接按上面的 SMTP 配置接入最简单。

如果它们只提供 HTTP API，不提供 SMTP，则新增一个 `EmailSender` 实现即可，例如：

```text
ClawApiEmailSender implements EmailSender
```

验证码生成、过期、限流、校验、注册逻辑不需要改。

## 数据库

新库初始化已包含 `email_verification_code` 表。

已有数据库执行：

```sql
source back/sql/migration_email_verification_code.sql;
```
