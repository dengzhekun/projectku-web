SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `user_wallets` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `balance` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '可用余额',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户余额钱包表';

CREATE TABLE IF NOT EXISTS `wallet_transactions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `order_id` bigint(20) DEFAULT NULL COMMENT '关联订单ID',
  `trade_id` varchar(64) DEFAULT NULL COMMENT '支付流水号',
  `type` varchar(32) NOT NULL COMMENT '流水类型: INIT, PAYMENT, REFUND, RECHARGE, REGISTRATION_BONUS',
  `amount` decimal(10,2) NOT NULL COMMENT '变动金额',
  `balance_after` decimal(10,2) NOT NULL COMMENT '变动后余额',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_create_time` (`user_id`, `create_time`),
  KEY `idx_trade_id` (`trade_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包流水表';

SET @paid_at_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'payments'
    AND COLUMN_NAME = 'paid_at'
);

SET @add_paid_at_sql = IF(
  @paid_at_column_exists = 0,
  'ALTER TABLE `payments` ADD COLUMN `paid_at` datetime DEFAULT NULL COMMENT ''实际支付完成时间'' AFTER `status`',
  'SELECT 1'
);

PREPARE add_paid_at_stmt FROM @add_paid_at_sql;
EXECUTE add_paid_at_stmt;
DEALLOCATE PREPARE add_paid_at_stmt;

INSERT IGNORE INTO `user_wallets`(`user_id`, `balance`)
SELECT `id`, 20000.00 FROM `users`;

INSERT INTO `wallet_transactions`(`user_id`, `type`, `amount`, `balance_after`, `remark`)
SELECT u.`id`, 'INIT', 20000.00, 20000.00, '注册赠送余额'
FROM `users` u
LEFT JOIN `wallet_transactions` wt
  ON wt.`user_id` = u.`id` AND wt.`type` = 'INIT'
WHERE wt.`id` IS NULL;
