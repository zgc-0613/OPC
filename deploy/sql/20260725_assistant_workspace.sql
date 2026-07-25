SET @assistant_title_mode_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions' AND column_name='title_mode'
);
SET @assistant_sql = IF(@assistant_title_mode_exists=0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN title_mode VARCHAR(10) NOT NULL DEFAULT ''auto'' AFTER title',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;

SET @assistant_sql = IF(@assistant_title_mode_exists=0,
    'UPDATE ai_agent_sessions SET title_mode=''manual''',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;

SET @assistant_pinned_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions' AND column_name='pinned_at'
);
SET @assistant_sql = IF(@assistant_pinned_at_exists=0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN pinned_at DATETIME(6) NULL AFTER last_message_at',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;

SET @assistant_archived_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions' AND column_name='archived_at'
);
SET @assistant_sql = IF(@assistant_archived_at_exists=0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN archived_at DATETIME(6) NULL AFTER pinned_at',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;

SET @assistant_deleted_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions' AND column_name='deleted_at'
);
SET @assistant_sql = IF(@assistant_deleted_at_exists=0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN deleted_at DATETIME(6) NULL AFTER archived_at',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;

SET @assistant_purge_after_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions' AND column_name='purge_after'
);
SET @assistant_sql = IF(@assistant_purge_after_exists=0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN purge_after DATETIME(6) NULL AFTER deleted_at',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;

SET @assistant_purged_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions' AND column_name='purged_at'
);
SET @assistant_sql = IF(@assistant_purged_at_exists=0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN purged_at DATETIME(6) NULL AFTER purge_after',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;

UPDATE ai_agent_sessions
SET title_mode='manual'
WHERE title_mode IS NULL OR title_mode NOT IN ('auto','manual');

UPDATE ai_agent_sessions
SET archived_at=COALESCE(archived_at,updated_at,created_at)
WHERE status='archived' AND archived_at IS NULL;

SET @assistant_active_history_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
      AND index_name='idx_agent_sessions_history_active'
);
SET @assistant_sql = IF(@assistant_active_history_index_exists=0,
    'ALTER TABLE ai_agent_sessions ADD INDEX idx_agent_sessions_history_active (user_id,deleted_at,pinned_at,last_message_at,id)',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;

SET @assistant_archived_history_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
      AND index_name='idx_agent_sessions_history_archived'
);
SET @assistant_sql = IF(@assistant_archived_history_index_exists=0,
    'ALTER TABLE ai_agent_sessions ADD INDEX idx_agent_sessions_history_archived (user_id,archived_at,last_message_at,id)',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;

SET @assistant_purge_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
      AND index_name='idx_agent_sessions_purge_due'
);
SET @assistant_sql = IF(@assistant_purge_index_exists=0,
    'ALTER TABLE ai_agent_sessions ADD INDEX idx_agent_sessions_purge_due (purge_after,purged_at,id)',
    'SELECT 1');
PREPARE assistant_stmt FROM @assistant_sql; EXECUTE assistant_stmt; DEALLOCATE PREPARE assistant_stmt;
