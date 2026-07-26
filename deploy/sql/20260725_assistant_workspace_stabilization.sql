-- Forward-only stabilization for the deployed Assistant workspace migration.
-- The deployment workflow persists the original rollout boundary before this
-- script runs. INSERT IGNORE preserves that boundary across every rerun.
INSERT IGNORE INTO app_settings (setting_key, setting_value, `sensitive`)
VALUES (
    'migration.assistant_workspace_rollout_at',
    '2026-07-25 21:56:34.000000',
    0
);

SET @assistant_workspace_backfill_cutoff = (
    SELECT STR_TO_DATE(setting_value, '%Y-%m-%d %H:%i:%s.%f')
    FROM app_settings
    WHERE setting_key='migration.assistant_workspace_rollout_at'
    LIMIT 1
);
SET @assistant_workspace_backfill_cutoff = COALESCE(
    @assistant_workspace_backfill_cutoff,
    TIMESTAMP('2026-07-25 21:56:34.000000')
);

UPDATE ai_agent_sessions
SET title_mode='manual'
WHERE title_mode='auto'
  AND created_at < @assistant_workspace_backfill_cutoff;

SET @assistant_content_generation_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
      AND column_name='content_generation'
);
SET @assistant_stability_sql = IF(@assistant_content_generation_exists=0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN content_generation BIGINT NOT NULL DEFAULT 0 AFTER purged_at',
    'SELECT 1');
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;

SET @assistant_submission_kind_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
      AND column_name='submission_kind'
);
SET @assistant_stability_sql = IF(@assistant_submission_kind_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN submission_kind VARCHAR(20) NOT NULL DEFAULT ''message'' AFTER task_type',
    'SELECT 1');
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;

SET @assistant_request_content_hash_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
      AND column_name='request_content_hash'
);
SET @assistant_stability_sql = IF(@assistant_request_content_hash_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN request_content_hash CHAR(64) NULL AFTER idempotency_key',
    'SELECT 1');
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;

SET @assistant_start_profile_hash_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
      AND column_name='start_profile_hash'
);
SET @assistant_stability_sql = IF(@assistant_start_profile_hash_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN start_profile_hash CHAR(64) NULL AFTER request_content_hash',
    'SELECT 1');
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;

SET @assistant_session_content_generation_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
      AND column_name='session_content_generation'
);
SET @assistant_stability_sql = IF(@assistant_session_content_generation_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN session_content_generation BIGINT NOT NULL DEFAULT 0 AFTER session_id',
    'SELECT 1');
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;

CREATE TABLE IF NOT EXISTS ai_agent_content_purge_audits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operation VARCHAR(40) NOT NULL,
    session_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    operator_type VARCHAR(20) NOT NULL,
    operator_id BIGINT NULL,
    result VARCHAR(20) NOT NULL,
    diagnostic_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_agent_purge_audits_session_created (session_id,created_at),
    INDEX idx_agent_purge_audits_user_created (user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @assistant_active_history_signature = (
    SELECT CONCAT(
        MIN(non_unique), ':',
        GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    )
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
      AND index_name='idx_agent_sessions_history_active'
);
SET @assistant_stability_sql = IF(
    @assistant_active_history_signature IS NULL
    OR @assistant_active_history_signature='1:user_id,deleted_at,pinned_at,last_message_at,id',
    'SELECT 1',
    'ALTER TABLE ai_agent_sessions DROP INDEX idx_agent_sessions_history_active'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;
SET @assistant_stability_sql = IF(
    @assistant_active_history_signature='1:user_id,deleted_at,pinned_at,last_message_at,id',
    'SELECT 1',
    'ALTER TABLE ai_agent_sessions ADD INDEX idx_agent_sessions_history_active (user_id,deleted_at,pinned_at,last_message_at,id)'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;

SET @assistant_archived_history_signature = (
    SELECT CONCAT(
        MIN(non_unique), ':',
        GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    )
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
      AND index_name='idx_agent_sessions_history_archived'
);
SET @assistant_stability_sql = IF(
    @assistant_archived_history_signature IS NULL
    OR @assistant_archived_history_signature='1:user_id,archived_at,last_message_at,id',
    'SELECT 1',
    'ALTER TABLE ai_agent_sessions DROP INDEX idx_agent_sessions_history_archived'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;
SET @assistant_stability_sql = IF(
    @assistant_archived_history_signature='1:user_id,archived_at,last_message_at,id',
    'SELECT 1',
    'ALTER TABLE ai_agent_sessions ADD INDEX idx_agent_sessions_history_archived (user_id,archived_at,last_message_at,id)'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;

SET @assistant_purge_due_signature = (
    SELECT CONCAT(
        MIN(non_unique), ':',
        GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    )
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
      AND index_name='idx_agent_sessions_purge_due'
);
SET @assistant_stability_sql = IF(
    @assistant_purge_due_signature IS NULL
    OR @assistant_purge_due_signature='1:purge_after,purged_at,id',
    'SELECT 1',
    'ALTER TABLE ai_agent_sessions DROP INDEX idx_agent_sessions_purge_due'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;
SET @assistant_stability_sql = IF(
    @assistant_purge_due_signature='1:purge_after,purged_at,id',
    'SELECT 1',
    'ALTER TABLE ai_agent_sessions ADD INDEX idx_agent_sessions_purge_due (purge_after,purged_at,id)'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;

SET @assistant_message_created_signature = (
    SELECT CONCAT(
        MIN(non_unique), ':',
        GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    )
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_agent_messages'
      AND index_name='idx_agent_messages_session_created'
);
SET @assistant_stability_sql = IF(
    @assistant_message_created_signature IS NULL
    OR @assistant_message_created_signature='1:session_id,created_at,id',
    'SELECT 1',
    'ALTER TABLE ai_agent_messages DROP INDEX idx_agent_messages_session_created'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;
SET @assistant_stability_sql = IF(
    @assistant_message_created_signature='1:session_id,created_at,id',
    'SELECT 1',
    'ALTER TABLE ai_agent_messages ADD INDEX idx_agent_messages_session_created (session_id,created_at,id)'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;

SET @assistant_message_sequence_signature = (
    SELECT CONCAT(
        MIN(non_unique), ':',
        GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    )
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_agent_messages'
      AND index_name='uk_agent_message_sequence'
);
SET @assistant_stability_sql = IF(
    @assistant_message_sequence_signature IS NULL
    OR @assistant_message_sequence_signature='0:session_id,sequence_no',
    'SELECT 1',
    'ALTER TABLE ai_agent_messages DROP INDEX uk_agent_message_sequence'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;
SET @assistant_stability_sql = IF(
    @assistant_message_sequence_signature='0:session_id,sequence_no',
    'SELECT 1',
    'ALTER TABLE ai_agent_messages ADD UNIQUE INDEX uk_agent_message_sequence (session_id,sequence_no)'
);
PREPARE assistant_stability_stmt FROM @assistant_stability_sql;
EXECUTE assistant_stability_stmt;
DEALLOCATE PREPARE assistant_stability_stmt;
