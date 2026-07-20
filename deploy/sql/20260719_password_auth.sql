SET @password_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_users'
      AND column_name = 'password_hash'
);
SET @password_column_sql = IF(
    @password_column_exists = 0,
    'ALTER TABLE platform_users ADD COLUMN password_hash VARCHAR(100) NULL COMMENT ''BCrypt password hash; NULL for legacy email-code accounts'' AFTER email',
    'SELECT 1'
);
PREPARE password_column_statement FROM @password_column_sql;
EXECUTE password_column_statement;
DEALLOCATE PREPARE password_column_statement;

SET @username_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_users'
      AND index_name = 'uk_platform_users_username'
);
SET @username_index_sql = IF(
    @username_index_exists = 0,
    'ALTER TABLE platform_users ADD UNIQUE KEY uk_platform_users_username (username)',
    'SELECT 1'
);
PREPARE username_index_statement FROM @username_index_sql;
EXECUTE username_index_statement;
DEALLOCATE PREPARE username_index_statement;
