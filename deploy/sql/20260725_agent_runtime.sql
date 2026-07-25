CREATE TABLE IF NOT EXISTS ai_agent_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    profile_json JSON NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    last_message_at DATETIME(6) NULL,
    CONSTRAINT chk_agent_session_status CHECK (status IN ('active', 'archived')),
    CONSTRAINT fk_agent_sessions_user FOREIGN KEY (user_id) REFERENCES platform_users(id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_agent_sessions_user_activity (user_id, status, last_message_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_agent_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    sequence_no INT NOT NULL,
    run_id BIGINT NULL,
    citations_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_agent_message_role CHECK (role IN ('user', 'assistant')),
    CONSTRAINT chk_agent_message_status CHECK (status IN ('pending', 'completed', 'failed')),
    CONSTRAINT fk_agent_messages_session FOREIGN KEY (session_id) REFERENCES ai_agent_sessions(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    UNIQUE KEY uk_agent_message_sequence (session_id, sequence_no),
    INDEX idx_agent_messages_run (run_id),
    INDEX idx_agent_messages_session_created (session_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @agent_session_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='session_id'
);
SET @agent_sql = IF(@agent_session_id_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN session_id BIGINT NULL AFTER case_id', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_user_message_id_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='user_message_id'
);
SET @agent_sql = IF(@agent_user_message_id_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN user_message_id BIGINT NULL AFTER session_id', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_idempotency_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='idempotency_key'
);
SET @agent_sql = IF(@agent_idempotency_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER user_message_id', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_step_count_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='step_count'
);
SET @agent_sql = IF(@agent_step_count_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN step_count INT NOT NULL DEFAULT 0 AFTER diagnostic_code', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_tool_count_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='tool_call_count'
);
SET @agent_sql = IF(@agent_tool_count_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN tool_call_count INT NOT NULL DEFAULT 0 AFTER step_count', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_current_stage_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='current_stage'
);
SET @agent_sql = IF(@agent_current_stage_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN current_stage VARCHAR(40) NULL AFTER tool_call_count', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_visible_progress_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='visible_progress'
);
SET @agent_sql = IF(@agent_visible_progress_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN visible_progress VARCHAR(120) NULL AFTER current_stage', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_cancelled_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='cancelled_at'
);
SET @agent_sql = IF(@agent_cancelled_at_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN cancelled_at DATETIME(6) NULL AFTER visible_progress', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_completed_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='completed_at'
);
SET @agent_sql = IF(@agent_completed_at_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN completed_at DATETIME(6) NULL AFTER cancelled_at', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_session_guard_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='session_active_guard'
);
SET @agent_sql = IF(@agent_session_guard_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN session_active_guard BIGINT GENERATED ALWAYS AS (CASE WHEN status = ''running'' THEN session_id ELSE NULL END) STORED',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_session_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND index_name='idx_ai_runs_session'
);
SET @agent_sql = IF(@agent_session_index_exists=0,
    'ALTER TABLE ai_analysis_runs ADD INDEX idx_ai_runs_session (session_id, created_at, id)', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_session_active_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND index_name='uk_ai_runs_active_session'
);
SET @agent_sql = IF(@agent_session_active_index_exists=0,
    'ALTER TABLE ai_analysis_runs ADD UNIQUE KEY uk_ai_runs_active_session (session_active_guard)', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_idempotency_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND index_name='uk_ai_runs_idempotency'
);
SET @agent_sql = IF(@agent_idempotency_index_exists=0,
    'ALTER TABLE ai_analysis_runs ADD UNIQUE KEY uk_ai_runs_idempotency (user_id, task_type, idempotency_key)', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_run_session_fk_exists = (
    SELECT COUNT(*) FROM information_schema.referential_constraints
    WHERE constraint_schema=DATABASE() AND constraint_name='fk_ai_runs_agent_session'
);
SET @agent_sql = IF(@agent_run_session_fk_exists=0,
    'ALTER TABLE ai_analysis_runs ADD CONSTRAINT fk_ai_runs_agent_session FOREIGN KEY (session_id) REFERENCES ai_agent_sessions(id) ON DELETE RESTRICT ON UPDATE RESTRICT',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_run_message_fk_exists = (
    SELECT COUNT(*) FROM information_schema.referential_constraints
    WHERE constraint_schema=DATABASE() AND constraint_name='fk_ai_runs_user_message'
);
SET @agent_sql = IF(@agent_run_message_fk_exists=0,
    'ALTER TABLE ai_analysis_runs ADD CONSTRAINT fk_ai_runs_user_message FOREIGN KEY (user_message_id) REFERENCES ai_agent_messages(id) ON DELETE RESTRICT ON UPDATE RESTRICT',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_message_run_fk_exists = (
    SELECT COUNT(*) FROM information_schema.referential_constraints
    WHERE constraint_schema=DATABASE() AND constraint_name='fk_agent_messages_run'
);
SET @agent_sql = IF(@agent_message_run_fk_exists=0,
    'ALTER TABLE ai_agent_messages ADD CONSTRAINT fk_agent_messages_run FOREIGN KEY (run_id) REFERENCES ai_analysis_runs(id) ON DELETE SET NULL ON UPDATE RESTRICT',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

CREATE TABLE IF NOT EXISTS ai_agent_tool_calls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    analysis_run_id BIGINT NOT NULL,
    step_no INT NOT NULL,
    tool_name VARCHAR(60) NOT NULL,
    arguments_json JSON NOT NULL,
    result_summary_json JSON NULL,
    evidence_hash CHAR(64) NULL,
    status VARCHAR(20) NOT NULL,
    diagnostic_code VARCHAR(80) NULL,
    evidence_count INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_agent_tool_status CHECK (status IN ('pending','running','completed','failed','cancelled')),
    CONSTRAINT fk_agent_tool_calls_run FOREIGN KEY (analysis_run_id) REFERENCES ai_analysis_runs(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    UNIQUE KEY uk_agent_tool_step (analysis_run_id, step_no),
    INDEX idx_agent_tool_run_status (analysis_run_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @agent_enabled_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_enabled'
);
SET @agent_sql = IF(@agent_enabled_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER enabled', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_rounds_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_max_model_rounds'
);
SET @agent_sql = IF(@agent_rounds_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_max_model_rounds INT NOT NULL DEFAULT 4 AFTER agent_enabled', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_tools_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_max_tool_calls'
);
SET @agent_sql = IF(@agent_tools_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_max_tool_calls INT NOT NULL DEFAULT 6 AFTER agent_max_model_rounds', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_tokens_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_max_tokens'
);
SET @agent_sql = IF(@agent_tokens_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_max_tokens INT NOT NULL DEFAULT 8000 AFTER agent_max_tool_calls', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_history_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_history_window'
);
SET @agent_sql = IF(@agent_history_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_history_window INT NOT NULL DEFAULT 12 AFTER agent_max_tokens', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_timeout_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_timeout_seconds'
);
SET @agent_sql = IF(@agent_timeout_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_timeout_seconds INT NOT NULL DEFAULT 120 AFTER agent_history_window', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_mode_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_tool_mode'
);
SET @agent_sql = IF(@agent_mode_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_tool_mode VARCHAR(20) NOT NULL DEFAULT ''json_plan'' AFTER agent_timeout_seconds', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;
