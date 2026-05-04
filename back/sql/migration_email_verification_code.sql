CREATE TABLE IF NOT EXISTS `email_verification_code` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `email` varchar(128) NOT NULL COMMENT '邮箱',
  `purpose` varchar(32) NOT NULL COMMENT '用途: REGISTER/LOGIN/RESET_PASSWORD',
  `code_hash` varchar(128) NOT NULL COMMENT '验证码哈希',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `used_at` datetime DEFAULT NULL COMMENT '使用时间',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '错误尝试次数',
  `send_ip` varchar(64) DEFAULT NULL COMMENT '发送请求IP',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_email_purpose_time` (`email`, `purpose`, `created_at`),
  KEY `idx_email_purpose_usable` (`email`, `purpose`, `used_at`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱验证码表';
