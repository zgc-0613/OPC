-- Platform collection catalog. Run before 20261025_platform_sites_seed.sql.
CREATE TABLE IF NOT EXISTS platform_sites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_key VARCHAR(80) NOT NULL,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    category VARCHAR(120) NOT NULL,
    owner VARCHAR(255) NULL,
    services VARCHAR(500) NULL,
    summary TEXT NULL,
    domain VARCHAR(255) NULL,
    favicon_url VARCHAR(1000) NULL,
    placeholder_logo_path VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    verification_status VARCHAR(30) NOT NULL DEFAULT 'pending',
    source_batch VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_sites_source_key (source_key),
    INDEX idx_platform_sites_public_order (status, sort_order, id),
    INDEX idx_platform_sites_category (category)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Curated OPC platform and website directory';
