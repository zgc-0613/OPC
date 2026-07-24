SET @has_is_industry := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tags' AND column_name = 'is_industry'
);
SET @sql := IF(
    @has_is_industry = 0,
    'ALTER TABLE tags ADD COLUMN is_industry TINYINT(1) NOT NULL DEFAULT 0 AFTER tag_type',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_tags_industry_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tags' AND index_name = 'idx_tags_is_industry'
);
SET @sql := IF(
    @has_tags_industry_index = 0,
    'ALTER TABLE tags ADD INDEX idx_tags_is_industry (is_industry)',
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

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'case_tags' AND column_name = 'id') = 0,
    'ALTER TABLE case_tags ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT UNIQUE FIRST', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'case_tags' AND column_name = 'case_id') = 0,
    'ALTER TABLE case_tags ADD COLUMN case_id BIGINT NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'case_tags' AND column_name = 'tag_id') = 0,
    'ALTER TABLE case_tags ADD COLUMN tag_id BIGINT NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'case_tags' AND column_name = 'created_at') = 0,
    'ALTER TABLE case_tags ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'case_tags' AND constraint_type = 'PRIMARY KEY') = 0,
    'ALTER TABLE case_tags ADD PRIMARY KEY (id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'case_tags' AND index_name = 'uk_case_tags_case_tag') = 0,
    'ALTER TABLE case_tags ADD UNIQUE INDEX uk_case_tags_case_tag (case_id, tag_id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'case_tags' AND index_name = 'idx_case_tags_case_id') = 0,
    'ALTER TABLE case_tags ADD INDEX idx_case_tags_case_id (case_id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'case_tags' AND index_name = 'idx_case_tags_tag_id') = 0,
    'ALTER TABLE case_tags ADD INDEX idx_case_tags_tag_id (tag_id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tag_aliases' AND column_name = 'id') = 0,
    'ALTER TABLE tag_aliases ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT UNIQUE FIRST', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tag_aliases' AND column_name = 'tag_id') = 0,
    'ALTER TABLE tag_aliases ADD COLUMN tag_id BIGINT NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tag_aliases' AND column_name = 'alias') = 0,
    'ALTER TABLE tag_aliases ADD COLUMN alias VARCHAR(100) NOT NULL DEFAULT ''''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tag_aliases' AND column_name = 'normalized_alias') = 0,
    'ALTER TABLE tag_aliases ADD COLUMN normalized_alias VARCHAR(100) NOT NULL DEFAULT ''''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tag_aliases' AND column_name = 'created_at') = 0,
    'ALTER TABLE tag_aliases ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tag_aliases' AND column_name = 'updated_at') = 0,
    'ALTER TABLE tag_aliases ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'tag_aliases' AND constraint_type = 'PRIMARY KEY') = 0,
    'ALTER TABLE tag_aliases ADD PRIMARY KEY (id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tag_aliases' AND index_name = 'uk_tag_aliases_normalized') = 0,
    'ALTER TABLE tag_aliases ADD UNIQUE INDEX uk_tag_aliases_normalized (normalized_alias)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tag_aliases' AND index_name = 'idx_tag_aliases_tag_id') = 0,
    'ALTER TABLE tag_aliases ADD INDEX idx_tag_aliases_tag_id (tag_id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS industry_tag_review_candidates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tag_id BIGINT NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    policy_usage_count INT NOT NULL DEFAULT 0,
    case_usage_count INT NOT NULL DEFAULT 0,
    review_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    review_reason VARCHAR(120) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_review_tag (tag_id),
    INDEX idx_industry_review_status (review_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND column_name = 'id') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT UNIQUE FIRST', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND column_name = 'tag_id') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD COLUMN tag_id BIGINT NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND column_name = 'tag_name') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD COLUMN tag_name VARCHAR(100) NOT NULL DEFAULT ''''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND column_name = 'policy_usage_count') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD COLUMN policy_usage_count INT NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND column_name = 'case_usage_count') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD COLUMN case_usage_count INT NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND column_name = 'review_status') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT ''pending''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND column_name = 'review_reason') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD COLUMN review_reason VARCHAR(120) NOT NULL DEFAULT ''migration_repair_required''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND column_name = 'created_at') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND column_name = 'updated_at') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND constraint_type = 'PRIMARY KEY') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD PRIMARY KEY (id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND index_name = 'uk_industry_review_tag') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD UNIQUE INDEX uk_industry_review_tag (tag_id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'industry_tag_review_candidates' AND index_name = 'idx_industry_review_status') = 0,
    'ALTER TABLE industry_tag_review_candidates ADD INDEX idx_industry_review_status (review_status, updated_at)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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

-- Only an explicit structural industry marker is safe to promote automatically.
UPDATE tags AS tag
JOIN (SELECT DISTINCT tag_id FROM policy_tags) AS used_policy_tag ON used_policy_tag.tag_id = tag.id
SET tag.is_industry = 1
WHERE tag.tag_type = 'industry';

INSERT IGNORE INTO industry_tag_review_candidates (
    tag_id, tag_name, policy_usage_count, case_usage_count, review_status, review_reason
)
SELECT
    tag.id,
    tag.name,
    (SELECT COUNT(*) FROM policy_tags WHERE policy_tags.tag_id = tag.id),
    (SELECT COUNT(*) FROM case_tags WHERE case_tags.tag_id = tag.id),
    CASE WHEN tag.name IN ('最新', '重点', '国家级') THEN 'excluded' ELSE 'pending' END,
    CASE
        WHEN tag.name IN ('最新', '重点', '国家级') THEN 'obvious_metadata_tag'
        ELSE 'policy_usage_requires_manual_industry_review'
    END
FROM tags AS tag
WHERE tag.is_industry = 0
  AND EXISTS (SELECT 1 FROM policy_tags WHERE policy_tags.tag_id = tag.id);

UPDATE industry_tag_review_candidates AS candidate
JOIN tags AS tag ON tag.id = candidate.tag_id
SET
    candidate.policy_usage_count = (
        SELECT COUNT(*) FROM policy_tags WHERE policy_tags.tag_id = candidate.tag_id
    ),
    candidate.case_usage_count = (
        SELECT COUNT(*) FROM case_tags WHERE case_tags.tag_id = candidate.tag_id
    ),
    candidate.tag_name = tag.name;

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
  ON FIND_IN_SET(
      LOWER(REPLACE(REPLACE(tag.name, ' ', ''), CHAR(9), '')),
      LOWER(REPLACE(REPLACE(REPLACE(case_item.tags, '，', ','), ' ', ''), CHAR(9), ''))
  ) > 0
WHERE case_item.tags IS NOT NULL AND TRIM(case_item.tags) <> '';

-- Historical CSV values may contain an alias only (for example AIGC) and must
-- resolve to the canonical industry tag before the legacy text is retired.
INSERT IGNORE INTO case_tags (case_id, tag_id)
SELECT case_item.id, alias.tag_id
FROM case_items AS case_item
JOIN tag_aliases AS alias
  ON FIND_IN_SET(
      LOWER(alias.normalized_alias),
      LOWER(REPLACE(REPLACE(REPLACE(case_item.tags, '，', ','), ' ', ''), CHAR(9), ''))
  ) > 0
JOIN tags AS tag ON tag.id = alias.tag_id AND tag.is_industry = 1
WHERE case_item.tags IS NOT NULL AND TRIM(case_item.tags) <> '';

INSERT IGNORE INTO case_tags (case_id, tag_id)
SELECT case_item.id, tag.id
FROM case_items AS case_item
JOIN tags AS tag
  ON tag.name = TRIM(case_item.category) AND tag.is_industry = 1
WHERE case_item.category IS NOT NULL AND TRIM(case_item.category) <> '';

UPDATE sources SET status = 'published' WHERE status = 'active';

SET @has_api_key_provider := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_model_settings' AND column_name = 'api_key_provider'
);
SET @sql := IF(
    @has_api_key_provider = 0,
    'ALTER TABLE ai_model_settings ADD COLUMN api_key_provider VARCHAR(50) NULL AFTER api_key_ciphertext',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_api_key_origin := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_model_settings' AND column_name = 'api_key_origin'
);
SET @sql := IF(
    @has_api_key_origin = 0,
    'ALTER TABLE ai_model_settings ADD COLUMN api_key_origin VARCHAR(255) NULL AFTER api_key_provider',
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
    'ALTER TABLE ai_analysis_runs ADD COLUMN reserved_tokens BIGINT NOT NULL DEFAULT 0 AFTER total_tokens',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_started_at := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_analysis_runs' AND column_name = 'started_at'
);
SET @sql := IF(
    @has_started_at = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN started_at DATETIME NULL AFTER reserved_tokens',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_deadline_at := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_analysis_runs' AND column_name = 'deadline_at'
);
SET @sql := IF(
    @has_deadline_at = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN deadline_at DATETIME NULL AFTER started_at',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_heartbeat_at := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_analysis_runs' AND column_name = 'heartbeat_at'
);
SET @sql := IF(
    @has_heartbeat_at = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN heartbeat_at DATETIME NULL AFTER deadline_at',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_running_deadline_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_analysis_runs' AND index_name = 'idx_ai_runs_running_deadline'
);
SET @sql := IF(
    @has_running_deadline_index = 0,
    'ALTER TABLE ai_analysis_runs ADD INDEX idx_ai_runs_running_deadline (status, deadline_at)',
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

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'id') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT UNIQUE FIRST', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'item_type') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN item_type VARCHAR(20) NOT NULL DEFAULT ''''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'item_id') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN item_id BIGINT NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'previous_status') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN previous_status VARCHAR(30) NOT NULL DEFAULT ''legacy_unverified''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'new_status') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN new_status VARCHAR(30) NOT NULL DEFAULT ''legacy_unverified''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'admin_id') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN admin_id BIGINT NOT NULL DEFAULT 0', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'admin_username') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN admin_username VARCHAR(100) NOT NULL DEFAULT ''''', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'notes') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN notes VARCHAR(500) NULL', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'created_at') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND constraint_type = 'PRIMARY KEY') = 0,
    'ALTER TABLE ai_evidence_reviews ADD PRIMARY KEY (id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND index_name = 'idx_evidence_reviews_item') = 0,
    'ALTER TABLE ai_evidence_reviews ADD INDEX idx_evidence_reviews_item (item_type, item_id)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND index_name = 'idx_evidence_reviews_created_at') = 0,
    'ALTER TABLE ai_evidence_reviews ADD INDEX idx_evidence_reviews_created_at (created_at)', 'SELECT 1'); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
