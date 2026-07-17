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
