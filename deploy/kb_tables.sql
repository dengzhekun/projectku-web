CREATE TABLE IF NOT EXISTS `kb_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(255) NOT NULL,
  `category` VARCHAR(64) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `version` INT NOT NULL DEFAULT 1,
  `storage_path` VARCHAR(512) DEFAULT NULL,
  `content_text` LONGTEXT,
  `created_by` VARCHAR(64) DEFAULT 'admin',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `kb_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `chunk_index` INT NOT NULL,
  `content` LONGTEXT NOT NULL,
  `char_count` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'active',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_chunk_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `kb_index_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `version` INT NOT NULL,
  `embedding_provider` VARCHAR(64) NOT NULL,
  `vector_collection` VARCHAR(128) NOT NULL,
  `indexed_chunk_count` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `error_message` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_index_record_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `kb_hit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `chunk_id` BIGINT NOT NULL,
  `query_text` VARCHAR(1000) NOT NULL,
  `conversation_id` VARCHAR(128) DEFAULT NULL,
  `hit_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_hit_log_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
