SET @has_is_industry := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tags' AND column_name = 'is_industry'
);
SET @sql := IF(
    @has_is_industry = 0,
    'ALTER TABLE tags ADD COLUMN is_industry TINYINT(1) NOT NULL DEFAULT 0 AFTER tag_type, ADD INDEX idx_tags_is_industry (is_industry)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS case_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_case_tags_case_tag (case_id, tag_id),
    INDEX idx_case_tags_case_id (case_id),
    INDEX idx_case_tags_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tag_aliases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tag_id BIGINT NOT NULL,
    alias VARCHAR(100) NOT NULL,
    normalized_alias VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tag_aliases_normalized (normalized_alias),
    INDEX idx_tag_aliases_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO tags (name, tag_type, is_industry, sort_order)
SELECT DISTINCT TRIM(category), 'case', 1, 0
FROM case_items
WHERE category IS NOT NULL AND TRIM(category) <> '';

INSERT IGNORE INTO tags (name, tag_type, is_industry, sort_order)
VALUES ('人工智能应用', 'common', 1, 0);

UPDATE tags AS tag
JOIN (
    SELECT DISTINCT TRIM(category) AS category
    FROM case_items
    WHERE category IS NOT NULL AND TRIM(category) <> ''
) AS category_names ON category_names.category = tag.name
SET tag.is_industry = 1;

UPDATE tags SET is_industry = 1
WHERE name = '人工智能应用' AND tag_type = 'common';

SET @ai_industry_tag_id := (
    SELECT id FROM tags
    WHERE name = '人工智能应用' AND tag_type = 'common'
    ORDER BY id LIMIT 1
);

INSERT IGNORE INTO tag_aliases (tag_id, alias, normalized_alias)
SELECT @ai_industry_tag_id, 'AI应用', 'ai应用' FROM DUAL WHERE @ai_industry_tag_id IS NOT NULL;
INSERT IGNORE INTO tag_aliases (tag_id, alias, normalized_alias)
SELECT @ai_industry_tag_id, '人工智能', '人工智能' FROM DUAL WHERE @ai_industry_tag_id IS NOT NULL;
INSERT IGNORE INTO tag_aliases (tag_id, alias, normalized_alias)
SELECT @ai_industry_tag_id, 'AIGC', 'aigc' FROM DUAL WHERE @ai_industry_tag_id IS NOT NULL;
INSERT IGNORE INTO tag_aliases (tag_id, alias, normalized_alias)
SELECT @ai_industry_tag_id, '生成式AI', '生成式ai' FROM DUAL WHERE @ai_industry_tag_id IS NOT NULL;

INSERT IGNORE INTO case_tags (case_id, tag_id)
SELECT case_item.id, tag.id
FROM case_items AS case_item
JOIN tags AS tag
  ON FIND_IN_SET(tag.name, REPLACE(case_item.tags, '，', ',')) > 0
WHERE case_item.tags IS NOT NULL AND TRIM(case_item.tags) <> '';

INSERT IGNORE INTO case_tags (case_id, tag_id)
SELECT case_item.id, tag.id
FROM case_items AS case_item
JOIN tags AS tag
  ON tag.name = TRIM(case_item.category) AND tag.is_industry = 1
WHERE case_item.category IS NOT NULL AND TRIM(case_item.category) <> '';

SET @has_key_provider := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_model_settings' AND column_name = 'api_key_provider'
);
SET @sql := IF(
    @has_key_provider = 0,
    'ALTER TABLE ai_model_settings ADD COLUMN api_key_provider VARCHAR(50) NULL AFTER api_key_ciphertext, ADD COLUMN api_key_origin VARCHAR(255) NULL AFTER api_key_provider',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_reserved_tokens := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_analysis_runs' AND column_name = 'reserved_tokens'
);
SET @sql := IF(
    @has_reserved_tokens = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN reserved_tokens BIGINT NOT NULL DEFAULT 0 AFTER total_tokens, ADD COLUMN started_at DATETIME NULL AFTER reserved_tokens, ADD COLUMN deadline_at DATETIME NULL AFTER started_at, ADD COLUMN heartbeat_at DATETIME NULL AFTER deadline_at, ADD INDEX idx_ai_runs_running_deadline (status, deadline_at)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_evidence_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_type VARCHAR(20) NOT NULL,
    item_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    admin_id BIGINT NOT NULL,
    admin_username VARCHAR(100) NOT NULL,
    notes VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evidence_reviews_item (item_type, item_id),
    INDEX idx_evidence_reviews_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
