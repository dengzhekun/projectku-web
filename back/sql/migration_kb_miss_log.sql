CREATE TABLE IF NOT EXISTS `kb_miss_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `query_text` VARCHAR(1000) NOT NULL,
  `conversation_id` VARCHAR(128) DEFAULT NULL,
  `confidence` DECIMAL(5,4) DEFAULT NULL,
  `fallback_reason` VARCHAR(1000) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'open',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_miss_log_status` (`status`),
  KEY `idx_kb_miss_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
