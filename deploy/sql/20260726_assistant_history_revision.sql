-- Forward-only, repeatable owner-scoped metadata revision for Assistant history cursors.
SET @assistant_history_revision_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='platform_users'
      AND column_name='assistant_history_revision'
);
SET @assistant_history_revision_sql = IF(
    @assistant_history_revision_exists=0,
    'ALTER TABLE platform_users ADD COLUMN assistant_history_revision BIGINT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE assistant_history_revision_stmt FROM @assistant_history_revision_sql;
EXECUTE assistant_history_revision_stmt;
DEALLOCATE PREPARE assistant_history_revision_stmt;

UPDATE platform_users
SET assistant_history_revision=0
WHERE assistant_history_revision IS NULL OR assistant_history_revision < 0;
