-- Forward-only, idempotent Phase Three task-context storage.
-- Context is nullable for legacy sessions and becomes immutable at the service boundary.

SET @phase_three_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
       AND column_name='task_context_version') = 0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN task_context_version VARCHAR(40) NULL AFTER research_context_json',
    'SELECT 1');
PREPARE phase_three_stmt FROM @phase_three_sql;
EXECUTE phase_three_stmt;
DEALLOCATE PREPARE phase_three_stmt;

SET @phase_three_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
       AND column_name='task_context_json') = 0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN task_context_json JSON NULL AFTER task_context_version',
    'SELECT 1');
PREPARE phase_three_stmt FROM @phase_three_sql;
EXECUTE phase_three_stmt;
DEALLOCATE PREPARE phase_three_stmt;

SET @phase_three_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
       AND column_name='task_context_hash') = 0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN task_context_hash CHAR(64) NULL AFTER task_context_json',
    'SELECT 1');
PREPARE phase_three_stmt FROM @phase_three_sql;
EXECUTE phase_three_stmt;
DEALLOCATE PREPARE phase_three_stmt;

SET @phase_three_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
       AND index_name='idx_agent_sessions_task_context_hash') = 0,
    'ALTER TABLE ai_agent_sessions ADD INDEX idx_agent_sessions_task_context_hash (task_context_hash)',
    'SELECT 1');
PREPARE phase_three_stmt FROM @phase_three_sql;
EXECUTE phase_three_stmt;
DEALLOCATE PREPARE phase_three_stmt;
