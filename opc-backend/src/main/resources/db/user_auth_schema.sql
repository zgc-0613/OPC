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
