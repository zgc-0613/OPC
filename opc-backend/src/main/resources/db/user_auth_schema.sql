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
