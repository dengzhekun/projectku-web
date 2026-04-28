CREATE TABLE IF NOT EXISTS `customer_service_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `query_text` VARCHAR(1000) NOT NULL,
  `conversation_id` VARCHAR(128) DEFAULT NULL,
  `route` VARCHAR(64) NOT NULL,
  `source_type` VARCHAR(64) NOT NULL,
  `source_id` VARCHAR(128) DEFAULT NULL,
  `confidence` DECIMAL(5,4) DEFAULT NULL,
  `fallback_reason` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_customer_service_log_route` (`route`),
  KEY `idx_customer_service_log_source_type` (`source_type`),
  KEY `idx_customer_service_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
