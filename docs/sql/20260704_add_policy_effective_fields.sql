ALTER TABLE policies
    ADD COLUMN document_no VARCHAR(100) NULL COMMENT 'Document number' AFTER issuing_body,
    ADD COLUMN effective_date DATE NULL COMMENT 'Effective date' AFTER publish_date,
    ADD COLUMN valid_period VARCHAR(100) NULL COMMENT 'Valid period' AFTER effective_date,
    ADD COLUMN evidence_url VARCHAR(1000) NULL COMMENT 'Evidence URL' AFTER original_url,
    ADD INDEX idx_policies_document_no (document_no),
    ADD INDEX idx_policies_effective_date (effective_date);

CREATE TABLE IF NOT EXISTS policy_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    policy_id BIGINT NOT NULL COMMENT 'Policy ID',
    tag_id BIGINT NOT NULL COMMENT 'Tag ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',

    UNIQUE KEY uk_policy_tags_policy_tag (policy_id, tag_id),
    INDEX idx_policy_tags_policy_id (policy_id),
    INDEX idx_policy_tags_tag_id (tag_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Policy tag relation table';
