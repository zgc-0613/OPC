CREATE TABLE IF NOT EXISTS sources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    title VARCHAR(255) NOT NULL COMMENT 'Source title',
    source_type VARCHAR(50) NOT NULL COMMENT 'government_site/cnki_journal/cnki_newspaper/news/report/file/other',
    publisher VARCHAR(100) NULL COMMENT 'Publisher',
    url VARCHAR(1000) NULL COMMENT 'Original source URL',
    local_file VARCHAR(255) NULL COMMENT 'Local saved file name',
    accessed_at DATE NOT NULL COMMENT 'Access or download date',
    notes TEXT NULL COMMENT 'Notes',
    status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/reviewed/published',
    ai_evidence_status VARCHAR(30) NOT NULL DEFAULT 'legacy_unverified' COMMENT 'legacy_unverified/verified/excluded',
    evidence_revision BIGINT NOT NULL DEFAULT 0 COMMENT 'Monotonic AI evidence version',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    INDEX idx_sources_source_type (source_type),
    INDEX idx_sources_accessed_at (accessed_at),
    INDEX idx_sources_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Source ledger table';

CREATE TABLE IF NOT EXISTS policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    title VARCHAR(255) NOT NULL COMMENT 'Policy title',
    region_id BIGINT NOT NULL COMMENT 'Related region ID',
    issuing_body VARCHAR(255) NOT NULL COMMENT 'Issuing body',
    document_no VARCHAR(100) NULL COMMENT 'Document number',
    publish_date DATE NULL COMMENT 'Publish date',
    effective_date DATE NULL COMMENT 'Effective date',
    valid_period VARCHAR(100) NULL COMMENT 'Valid period',
    source_id BIGINT NOT NULL COMMENT 'Main source ID',
    policy_level VARCHAR(30) NOT NULL COMMENT 'national/provincial/city/district',
    policy_type VARCHAR(50) NOT NULL COMMENT 'comprehensive/computing_support/space_station/funding_subsidy/scenario_demand/talent_service/investment/other',
    applicability_mode VARCHAR(20) NOT NULL DEFAULT 'unclassified' COMMENT 'general/specific/unclassified',
    summary TEXT NOT NULL COMMENT '100-300 word summary',
    key_points TEXT NULL COMMENT 'Key points',
    support_measures TEXT NULL COMMENT 'Support measures',
    tags VARCHAR(500) NULL COMMENT 'Comma separated tags',
    original_url VARCHAR(1000) NULL COMMENT 'Original URL',
    evidence_url VARCHAR(1000) NULL COMMENT 'Evidence URL',
    local_file VARCHAR(255) NULL COMMENT 'Local file name',
    accessed_at DATE NOT NULL COMMENT 'Access or download date',
    status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/reviewed/published',
    reviewer VARCHAR(100) NULL COMMENT 'Reviewer',
    ai_evidence_status VARCHAR(30) NOT NULL DEFAULT 'legacy_unverified' COMMENT 'legacy_unverified/verified/excluded',
    evidence_revision BIGINT NOT NULL DEFAULT 0 COMMENT 'Monotonic AI evidence version',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    INDEX idx_policies_region_id (region_id),
    INDEX idx_policies_source_id (source_id),
    INDEX idx_policies_document_no (document_no),
    INDEX idx_policies_policy_type (policy_type),
    INDEX idx_policies_status (status),
    INDEX idx_policies_publish_date (publish_date),
    INDEX idx_policies_effective_date (effective_date)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Policy table';

CREATE TABLE IF NOT EXISTS case_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    title VARCHAR(255) NOT NULL COMMENT 'Case title',
    region_id BIGINT NOT NULL COMMENT 'Related region ID',
    category VARCHAR(50) NOT NULL COMMENT 'culture_creative/animation_short/education/office_efficiency/software_dev/ecommerce_marketing/other',
    actor_name VARCHAR(255) NULL COMMENT 'Person, company or project name',
    source_id BIGINT NOT NULL COMMENT 'Main source ID',
    summary TEXT NOT NULL COMMENT 'Case summary',
    business_model TEXT NULL COMMENT 'Business model',
    ai_tools TEXT NULL COMMENT 'AI tools or capabilities',
    outcome TEXT NULL COMMENT 'Outcome or effect',
    tags VARCHAR(500) NULL COMMENT 'Comma separated tags',
    original_url VARCHAR(1000) NULL COMMENT 'Original URL',
    local_file VARCHAR(255) NULL COMMENT 'Local file name',
    accessed_at DATE NOT NULL COMMENT 'Access or download date',
    status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/reviewed/published',
    reviewer VARCHAR(100) NULL COMMENT 'Reviewer',
    ai_evidence_status VARCHAR(30) NOT NULL DEFAULT 'legacy_unverified' COMMENT 'legacy_unverified/verified/excluded',
    evidence_revision BIGINT NOT NULL DEFAULT 0 COMMENT 'Monotonic AI evidence version',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    INDEX idx_case_items_region_id (region_id),
    INDEX idx_case_items_source_id (source_id),
    INDEX idx_case_items_category (category),
    INDEX idx_case_items_status (status),
    INDEX idx_case_items_accessed_at (accessed_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Case item table';

CREATE TABLE IF NOT EXISTS tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    name VARCHAR(100) NOT NULL COMMENT 'Tag name',
    tag_type VARCHAR(20) NOT NULL COMMENT 'policy/case/common',
    is_industry TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Industry taxonomy marker',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Sort order',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    UNIQUE KEY uk_tags_name_type (name, tag_type),
    INDEX idx_tags_tag_type (tag_type),
    INDEX idx_tags_is_industry (is_industry),
    INDEX idx_tags_sort_order (sort_order)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Tag dictionary table';

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

CREATE TABLE IF NOT EXISTS policy_industry_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    policy_id BIGINT NOT NULL COMMENT 'Policy ID',
    industry_tag_id BIGINT NOT NULL COMMENT 'Reviewed industry tag ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',

    UNIQUE KEY uk_policy_industry_policy_tag (policy_id, industry_tag_id),
    INDEX idx_policy_industry_policy_id (policy_id),
    INDEX idx_policy_industry_tag_id (industry_tag_id),
    CONSTRAINT fk_policy_industry_policy FOREIGN KEY (policy_id) REFERENCES policies(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_policy_industry_tag FOREIGN KEY (industry_tag_id) REFERENCES tags(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Reviewed policy industry applicability relations';

CREATE TABLE IF NOT EXISTS case_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    case_id BIGINT NOT NULL COMMENT 'Case ID',
    tag_id BIGINT NOT NULL COMMENT 'Tag ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',

    UNIQUE KEY uk_case_tags_case_tag (case_id, tag_id),
    INDEX idx_case_tags_case_id (case_id),
    INDEX idx_case_tags_tag_id (tag_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Case tag relation table';

CREATE TABLE IF NOT EXISTS tag_aliases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    tag_id BIGINT NOT NULL COMMENT 'Canonical industry tag ID',
    alias VARCHAR(100) NOT NULL COMMENT 'Human-readable alias',
    normalized_alias VARCHAR(100) NOT NULL COMMENT 'Normalized exact-match alias',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    UNIQUE KEY uk_tag_aliases_normalized (normalized_alias),
    INDEX idx_tag_aliases_tag_id (tag_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Industry tag aliases';

CREATE TABLE IF NOT EXISTS search_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    keyword VARCHAR(255) NOT NULL COMMENT 'Search keyword',
    search_scope VARCHAR(50) NOT NULL DEFAULT 'all' COMMENT 'all/policy/case/source/region',
    result_count INT NOT NULL DEFAULT 0 COMMENT 'Matched result count',
    page_path VARCHAR(500) NULL COMMENT 'Page path where search happened',
    ip_address VARCHAR(64) NULL COMMENT 'Client IP',
    user_agent VARCHAR(500) NULL COMMENT 'User agent',
    visitor_key VARCHAR(128) NULL COMMENT 'Rough visitor identifier',
    searched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Search time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',

    INDEX idx_search_logs_keyword (keyword),
    INDEX idx_search_logs_scope (search_scope),
    INDEX idx_search_logs_searched_at (searched_at),
    INDEX idx_search_logs_visitor_key (visitor_key),
    INDEX idx_search_logs_keyword_scope (keyword, search_scope)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Search keyword behavior log';

CREATE TABLE IF NOT EXISTS platform_users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    username VARCHAR(30) NOT NULL COMMENT 'Display username',
    email VARCHAR(255) NOT NULL COMMENT 'Login email',
    password_hash VARCHAR(100) NULL COMMENT 'BCrypt password hash; NULL for legacy email-code accounts',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
    last_login_at DATETIME NULL COMMENT 'Last login time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    UNIQUE KEY uk_platform_users_email (email),
    UNIQUE KEY uk_platform_users_username (username),
    INDEX idx_platform_users_status (status),
    INDEX idx_platform_users_last_login_at (last_login_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Public frontend user table';

CREATE TABLE IF NOT EXISTS email_verification_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    email VARCHAR(255) NOT NULL COMMENT 'Target email',
    code VARCHAR(6) NOT NULL COMMENT 'Six digit verification code',
    purpose VARCHAR(30) NOT NULL COMMENT 'user_register/password_reset/etc',
    used TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether the code is used',
    expires_at DATETIME NOT NULL COMMENT 'Expire time',
    used_at DATETIME NULL COMMENT 'Used time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',

    INDEX idx_email_codes_email_purpose (email, purpose),
    INDEX idx_email_codes_expires_at (expires_at),
    INDEX idx_email_codes_used (used)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Email verification code table';

CREATE TABLE IF NOT EXISTS user_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT NOT NULL COMMENT 'Platform user ID',
    token VARCHAR(64) NOT NULL COMMENT 'Frontend login token',
    expires_at DATETIME NOT NULL COMMENT 'Expire time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',

    UNIQUE KEY uk_user_sessions_token (token),
    INDEX idx_user_sessions_user_id (user_id),
    INDEX idx_user_sessions_expires_at (expires_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Public frontend user session table';

CREATE TABLE IF NOT EXISTS admin_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    username VARCHAR(30) NOT NULL COMMENT 'Administrator login username',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt password hash',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
    last_login_at DATETIME NULL COMMENT 'Last login time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    UNIQUE KEY uk_admin_accounts_username (username),
    INDEX idx_admin_accounts_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Administrator accounts';

CREATE TABLE IF NOT EXISTS admin_registration_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    username VARCHAR(30) NOT NULL COMMENT 'Requested administrator username',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt password hash',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
    pending_username VARCHAR(30) GENERATED ALWAYS AS (
        CASE WHEN status = 'pending' THEN username ELSE NULL END
    ) STORED COMMENT 'Unique key for pending applications only',
    reviewed_by BIGINT NULL COMMENT 'Reviewing administrator ID',
    reviewed_by_username VARCHAR(30) NULL COMMENT 'Reviewing administrator username snapshot',
    reviewed_at DATETIME NULL COMMENT 'Review time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    INDEX idx_admin_requests_username_status (username, status),
    UNIQUE KEY uk_admin_requests_pending_username (pending_username),
    INDEX idx_admin_requests_status_created (status, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Administrator registration approval requests';

CREATE TABLE IF NOT EXISTS admin_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    admin_id BIGINT NOT NULL COMMENT 'Administrator account ID',
    token VARCHAR(64) NOT NULL COMMENT 'Administrator session token',
    expires_at DATETIME NOT NULL COMMENT 'Expire time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',

    UNIQUE KEY uk_admin_sessions_token (token),
    INDEX idx_admin_sessions_admin_id (admin_id),
    INDEX idx_admin_sessions_expires_at (expires_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Persistent administrator sessions';


CREATE TABLE IF NOT EXISTS app_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    setting_key VARCHAR(120) NOT NULL COMMENT 'Unique setting key',
    setting_value TEXT NULL COMMENT 'Plain or encrypted setting value',
    `sensitive` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether the value is encrypted',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    UNIQUE KEY uk_app_settings_key (setting_key)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Runtime application settings';

CREATE TABLE IF NOT EXISTS ai_model_settings (
    id BIGINT PRIMARY KEY COMMENT 'Singleton settings row',
    provider VARCHAR(40) NOT NULL DEFAULT 'deepseek',
    api_format VARCHAR(40) NOT NULL DEFAULT 'openai_compatible',
    api_base_url VARCHAR(500) NULL,
    model_id VARCHAR(191) NULL,
    model_catalog_json JSON NULL,
    api_key_ciphertext TEXT NULL COMMENT 'AES-GCM encrypted provider key',
    api_key_provider VARCHAR(40) NULL COMMENT 'Provider binding for encrypted key',
    api_key_origin VARCHAR(500) NULL COMMENT 'HTTPS origin binding for encrypted key',
    temperature DECIMAL(4,3) NOT NULL DEFAULT 0.200,
    max_output_tokens INT NOT NULL DEFAULT 1200,
    timeout_seconds INT NOT NULL DEFAULT 30,
    retry_count INT NOT NULL DEFAULT 1,
    daily_token_quota BIGINT NOT NULL DEFAULT 100000,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    agent_enabled TINYINT(1) NOT NULL DEFAULT 0,
    agent_max_model_rounds INT NOT NULL DEFAULT 4,
    agent_max_tool_calls INT NOT NULL DEFAULT 6,
    agent_max_tokens INT NOT NULL DEFAULT 8000,
    agent_history_window INT NOT NULL DEFAULT 12,
    agent_timeout_seconds INT NOT NULL DEFAULT 120,
    agent_tool_mode VARCHAR(20) NOT NULL DEFAULT 'json_plan',
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

CREATE TABLE IF NOT EXISTS ai_settings_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    admin_username VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    change_summary VARCHAR(500) NOT NULL,
    success TINYINT(1) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_settings_audit_admin_created (admin_id, created_at),
    INDEX idx_ai_settings_audit_action_created (action, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='AI settings administrator audit trail';

CREATE TABLE IF NOT EXISTS ai_agent_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    profile_json JSON NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    last_message_at DATETIME(6) NULL,
    CONSTRAINT chk_agent_session_status CHECK (status IN ('active', 'archived')),
    CONSTRAINT fk_agent_sessions_user FOREIGN KEY (user_id) REFERENCES platform_users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_agent_sessions_user_activity (user_id, status, last_message_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='User-owned Agent research sessions';

CREATE TABLE IF NOT EXISTS ai_agent_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    sequence_no INT NOT NULL,
    run_id BIGINT NULL,
    citations_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_agent_message_role CHECK (role IN ('user', 'assistant')),
    CONSTRAINT chk_agent_message_status CHECK (status IN ('pending', 'completed', 'failed')),
    CONSTRAINT fk_agent_messages_session FOREIGN KEY (session_id) REFERENCES ai_agent_sessions(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    UNIQUE KEY uk_agent_message_sequence (session_id, sequence_no),
    INDEX idx_agent_messages_run (run_id),
    INDEX idx_agent_messages_session_created (session_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Ordered visible Agent conversation messages';

CREATE TABLE IF NOT EXISTS ai_analysis_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(40) NOT NULL DEFAULT 'case_analysis',
    case_id BIGINT NULL,
    session_id BIGINT NULL,
    user_message_id BIGINT NULL,
    idempotency_key VARCHAR(64) NULL,
    status VARCHAR(30) NOT NULL,
    active_guard BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'running' THEN user_id ELSE NULL END) STORED,
    session_active_guard BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'running' THEN session_id ELSE NULL END) STORED,
    result_json JSON NULL,
    provider VARCHAR(40) NOT NULL,
    model_id VARCHAR(191) NOT NULL,
    prompt_version VARCHAR(60) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    reserved_tokens BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME NULL,
    deadline_at DATETIME NULL,
    heartbeat_at DATETIME NULL,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    provider_request_id VARCHAR(191) NULL,
    finish_reason VARCHAR(40) NULL,
    response_hash CHAR(64) NULL,
    error_type VARCHAR(80) NULL,
    diagnostic_code VARCHAR(80) NULL,
    step_count INT NOT NULL DEFAULT 0,
    tool_call_count INT NOT NULL DEFAULT 0,
    current_stage VARCHAR(40) NULL,
    visible_progress VARCHAR(120) NULL,
    cancelled_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_runs_agent_session FOREIGN KEY (session_id) REFERENCES ai_agent_sessions(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ai_runs_user_message FOREIGN KEY (user_message_id) REFERENCES ai_agent_messages(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    UNIQUE KEY uk_ai_analysis_running_user (active_guard),
    UNIQUE KEY uk_ai_runs_active_session (session_active_guard),
    UNIQUE KEY uk_ai_runs_idempotency (user_id, task_type, idempotency_key),
    INDEX idx_ai_analysis_user_created (user_id, created_at),
    INDEX idx_ai_analysis_case_created (case_id, created_at),
    INDEX idx_ai_analysis_status_created (status, created_at),
    INDEX idx_ai_runs_session (session_id, created_at, id),
    INDEX idx_ai_runs_running_deadline (status, deadline_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Persisted AI task results and usage metadata';

ALTER TABLE ai_agent_messages
    ADD CONSTRAINT fk_agent_messages_run FOREIGN KEY (run_id) REFERENCES ai_analysis_runs(id)
        ON DELETE SET NULL ON UPDATE RESTRICT;

CREATE TABLE IF NOT EXISTS ai_agent_tool_calls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    analysis_run_id BIGINT NOT NULL,
    step_no INT NOT NULL,
    tool_name VARCHAR(60) NOT NULL,
    arguments_json JSON NOT NULL,
    result_summary_json JSON NULL,
    evidence_hash CHAR(64) NULL,
    status VARCHAR(20) NOT NULL,
    diagnostic_code VARCHAR(80) NULL,
    evidence_count INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_agent_tool_status CHECK (status IN ('pending','running','completed','failed','cancelled')),
    CONSTRAINT fk_agent_tool_calls_run FOREIGN KEY (analysis_run_id) REFERENCES ai_analysis_runs(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    UNIQUE KEY uk_agent_tool_step (analysis_run_id, step_no),
    INDEX idx_agent_tool_run_status (analysis_run_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Validated Agent tool invocation audit';

CREATE TABLE IF NOT EXISTS ai_evidence_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_type VARCHAR(20) NOT NULL,
    item_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    admin_id BIGINT NOT NULL,
    admin_username VARCHAR(100) NOT NULL,
    notes VARCHAR(500) NULL,
    action_type VARCHAR(50) NULL,
    reason VARCHAR(500) NULL,
    operation_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evidence_reviews_item (item_type, item_id),
    INDEX idx_evidence_reviews_created_at (created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Administrator AI evidence review audit trail';

INSERT INTO ai_model_settings (
    id, provider, api_format, temperature, max_output_tokens,
    timeout_seconds, retry_count, daily_token_quota, enabled
) VALUES (1, 'deepseek', 'openai_compatible', 0.200, 1200, 30, 1, 100000, 0)
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO app_settings (setting_key, setting_value, `sensitive`) VALUES
    ('site.name', 'SoloFirm', 0),
    ('auth.mail_enabled', 'false', 0),
    ('auth.verification_code_minutes', '10', 0),
    ('auth.resend_interval_seconds', '60', 0),
    ('auth.session_days', '30', 0),
    ('auth.altcha_enabled', 'false', 0),
    ('auth.altcha_cost', '5000', 0),
    ('auth.altcha_expires_seconds', '300', 0),
    ('smtp.host', 'smtp.qq.com', 0),
    ('smtp.port', '465', 0),
    ('smtp.username', '', 0),
    ('smtp.from_email', '', 0),
    ('smtp.from_name', 'SoloFirm', 0),
    ('smtp.security_mode', 'ssl', 0),
    ('smtp.timeout_seconds', '12', 0),
    ('mail.verification_subject', '[{{site_name}}] 邮箱验证码', 0),
    ('mail.verification_html', '__SOLOFIRM_DEFAULT__', 0)
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
