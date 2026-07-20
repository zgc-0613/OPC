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

SET @reviewer_username_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'admin_registration_requests'
      AND column_name = 'reviewed_by_username'
);
SET @reviewer_username_column_sql = IF(
    @reviewer_username_column_exists = 0,
    'ALTER TABLE admin_registration_requests ADD COLUMN reviewed_by_username VARCHAR(30) NULL COMMENT ''Reviewing administrator username snapshot'' AFTER reviewed_by',
    'SELECT 1'
);
PREPARE reviewer_username_column_statement FROM @reviewer_username_column_sql;
EXECUTE reviewer_username_column_statement;
DEALLOCATE PREPARE reviewer_username_column_statement;

UPDATE admin_registration_requests request_record
LEFT JOIN admin_accounts reviewer ON reviewer.id = request_record.reviewed_by
SET request_record.reviewed_by_username = reviewer.username
WHERE request_record.reviewed_by IS NOT NULL
  AND request_record.reviewed_by_username IS NULL;

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
