CREATE TABLE IF NOT EXISTS ai_model_settings (
    id BIGINT PRIMARY KEY COMMENT 'Singleton settings row',
    provider VARCHAR(40) NOT NULL DEFAULT 'deepseek',
    api_format VARCHAR(40) NOT NULL DEFAULT 'openai_compatible',
    api_base_url VARCHAR(500) NULL,
    model_id VARCHAR(191) NULL,
    api_key_ciphertext TEXT NULL COMMENT 'AES-GCM encrypted provider key',
    temperature DECIMAL(4,3) NOT NULL DEFAULT 0.200,
    max_output_tokens INT NOT NULL DEFAULT 1200,
    timeout_seconds INT NOT NULL DEFAULT 30,
    retry_count INT NOT NULL DEFAULT 1,
    daily_token_quota BIGINT NOT NULL DEFAULT 100000,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    last_test_status VARCHAR(30) NOT NULL DEFAULT 'not_tested',
    last_tested_at DATETIME NULL,
    last_test_message VARCHAR(240) NULL,
    updated_by_admin_id BIGINT NULL,
    updated_by_admin_username VARCHAR(30) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Runtime AI model configuration';

INSERT INTO ai_model_settings (
    id, provider, api_format, temperature, max_output_tokens,
    timeout_seconds, retry_count, daily_token_quota, enabled
) VALUES (1, 'deepseek', 'openai_compatible', 0.200, 1200, 30, 1, 100000, 0)
ON DUPLICATE KEY UPDATE id = VALUES(id);

CREATE TABLE IF NOT EXISTS ai_settings_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    admin_username VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    change_summary VARCHAR(500) NOT NULL COMMENT 'Safe summary; never contains provider secret',
    success TINYINT(1) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_settings_audit_admin_created (admin_id, created_at),
    INDEX idx_ai_settings_audit_action_created (action, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='AI settings administrator audit trail';

CREATE TABLE IF NOT EXISTS ai_analysis_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL COMMENT 'running/completed/failed/evidence_insufficient',
    active_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'running' THEN user_id ELSE NULL END
    ) STORED,
    result_json JSON NULL,
    provider VARCHAR(40) NOT NULL,
    model_id VARCHAR(191) NOT NULL,
    prompt_version VARCHAR(60) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    provider_request_id VARCHAR(191) NULL,
    error_type VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_analysis_running_user (active_guard),
    INDEX idx_ai_analysis_user_created (user_id, created_at),
    INDEX idx_ai_analysis_case_created (case_id, created_at),
    INDEX idx_ai_analysis_status_created (status, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Persisted AI case analysis results and usage metadata';

SET @case_ai_status_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_items' AND column_name = 'ai_evidence_status'
);
SET @case_ai_status_sql = IF(
    @case_ai_status_exists = 0,
    'ALTER TABLE case_items ADD COLUMN ai_evidence_status VARCHAR(30) NOT NULL DEFAULT ''legacy_unverified'' COMMENT ''legacy_unverified/verified/excluded'' AFTER reviewer, ADD INDEX idx_case_items_ai_evidence_status (ai_evidence_status)',
    'SELECT 1'
);
PREPARE case_ai_status_statement FROM @case_ai_status_sql;
EXECUTE case_ai_status_statement;
DEALLOCATE PREPARE case_ai_status_statement;

SET @policy_ai_status_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'policies' AND column_name = 'ai_evidence_status'
);
SET @policy_ai_status_sql = IF(
    @policy_ai_status_exists = 0,
    'ALTER TABLE policies ADD COLUMN ai_evidence_status VARCHAR(30) NOT NULL DEFAULT ''legacy_unverified'' COMMENT ''legacy_unverified/verified/excluded'' AFTER reviewer, ADD INDEX idx_policies_ai_evidence_status (ai_evidence_status)',
    'SELECT 1'
);
PREPARE policy_ai_status_statement FROM @policy_ai_status_sql;
EXECUTE policy_ai_status_statement;
DEALLOCATE PREPARE policy_ai_status_statement;

SET @source_ai_status_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sources' AND column_name = 'ai_evidence_status'
);
SET @source_ai_status_sql = IF(
    @source_ai_status_exists = 0,
    'ALTER TABLE sources ADD COLUMN ai_evidence_status VARCHAR(30) NOT NULL DEFAULT ''legacy_unverified'' COMMENT ''legacy_unverified/verified/excluded'' AFTER status, ADD INDEX idx_sources_ai_evidence_status (ai_evidence_status)',
    'SELECT 1'
);
PREPARE source_ai_status_statement FROM @source_ai_status_sql;
EXECUTE source_ai_status_statement;
DEALLOCATE PREPARE source_ai_status_statement;
