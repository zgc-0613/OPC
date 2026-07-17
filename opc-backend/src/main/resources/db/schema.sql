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
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Sort order',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    UNIQUE KEY uk_tags_name_type (name, tag_type),
    INDEX idx_tags_tag_type (tag_type),
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
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
    last_login_at DATETIME NULL COMMENT 'Last login time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',

    UNIQUE KEY uk_platform_users_email (email),
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
    purpose VARCHAR(30) NOT NULL COMMENT 'user_login/register/etc',
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
