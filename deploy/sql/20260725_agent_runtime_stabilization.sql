SET @agent_rollout_state_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_rollout_state'
);
SET @agent_sql = IF(@agent_rollout_state_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_rollout_state VARCHAR(30) NOT NULL DEFAULT ''explicitly_disabled'' AFTER agent_enabled',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;
SET @agent_sql = IF(@agent_rollout_state_exists=0,
    'UPDATE ai_model_settings SET agent_rollout_state=CASE WHEN agent_enabled=1 THEN ''explicitly_enabled'' ELSE ''explicitly_disabled'' END',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_rollout_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_rollout_changed_at'
);
SET @agent_sql = IF(@agent_rollout_at_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_rollout_changed_at DATETIME(6) NULL AFTER agent_rollout_state',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_rollout_admin_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_model_settings' AND column_name='agent_rollout_changed_by_admin_id'
);
SET @agent_sql = IF(@agent_rollout_admin_exists=0,
    'ALTER TABLE ai_model_settings ADD COLUMN agent_rollout_changed_by_admin_id BIGINT NULL AFTER agent_rollout_changed_at',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_context_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions' AND column_name='research_context_json'
);
SET @agent_sql = IF(@agent_context_exists=0,
    'ALTER TABLE ai_agent_sessions ADD COLUMN research_context_json JSON NULL AFTER profile_json',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_lease_owner_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='lease_owner'
);
SET @agent_sql = IF(@agent_lease_owner_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN lease_owner VARCHAR(120) NULL AFTER heartbeat_at', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_lease_expires_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='lease_expires_at'
);
SET @agent_sql = IF(@agent_lease_expires_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN lease_expires_at DATETIME(6) NULL AFTER lease_owner', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_attempts_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='execution_attempts'
);
SET @agent_sql = IF(@agent_attempts_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN execution_attempts INT NOT NULL DEFAULT 0 AFTER lease_expires_at', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_next_attempt_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='next_attempt_at'
);
SET @agent_sql = IF(@agent_next_attempt_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN next_attempt_at DATETIME(6) NULL AFTER execution_attempts', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_recovery_reason_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='last_recovery_reason'
);
SET @agent_sql = IF(@agent_recovery_reason_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN last_recovery_reason VARCHAR(120) NULL AFTER next_attempt_at', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_settlement_status_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='settlement_status'
);
SET @agent_sql = IF(@agent_settlement_status_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN settlement_status VARCHAR(30) NOT NULL DEFAULT ''reserved'' AFTER last_recovery_reason',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_dispatched_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='provider_dispatched_at'
);
SET @agent_sql = IF(@agent_dispatched_at_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN provider_dispatched_at DATETIME(6) NULL AFTER settlement_status', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_settled_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='settled_at'
);
SET @agent_sql = IF(@agent_settled_at_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN settled_at DATETIME(6) NULL AFTER provider_dispatched_at', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_settlement_version_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='settlement_version'
);
SET @agent_sql = IF(@agent_settlement_version_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN settlement_version BIGINT NOT NULL DEFAULT 0 AFTER settled_at', 'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_session_guard_v2_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='session_nonterminal_guard'
);
SET @agent_sql = IF(@agent_session_guard_v2_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN session_nonterminal_guard BIGINT GENERATED ALWAYS AS (CASE WHEN task_type=''agent_research'' AND status IN (''received'',''running'') THEN session_id ELSE NULL END) STORED',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_user_guard_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND column_name='user_agent_nonterminal_guard'
);
SET @agent_sql = IF(@agent_user_guard_exists=0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN user_agent_nonterminal_guard BIGINT GENERATED ALWAYS AS (CASE WHEN task_type=''agent_research'' AND status IN (''received'',''running'') THEN user_id ELSE NULL END) STORED',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_lease_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND index_name='idx_ai_runs_agent_lease'
);
SET @agent_sql = IF(@agent_lease_index_exists=0,
    'ALTER TABLE ai_analysis_runs ADD INDEX idx_ai_runs_agent_lease (status, next_attempt_at, lease_expires_at, id)',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_session_guard_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND index_name='uk_ai_runs_agent_session_nonterminal'
);
SET @agent_sql = IF(@agent_session_guard_index_exists=0,
    'ALTER TABLE ai_analysis_runs ADD UNIQUE KEY uk_ai_runs_agent_session_nonterminal (session_nonterminal_guard)',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

SET @agent_user_guard_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs' AND index_name='uk_ai_runs_agent_user_nonterminal'
);
SET @agent_sql = IF(@agent_user_guard_index_exists=0,
    'ALTER TABLE ai_analysis_runs ADD UNIQUE KEY uk_ai_runs_agent_user_nonterminal (user_agent_nonterminal_guard)',
    'SELECT 1');
PREPARE agent_stmt FROM @agent_sql; EXECUTE agent_stmt; DEALLOCATE PREPARE agent_stmt;

CREATE TABLE IF NOT EXISTS ai_agent_provider_calls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    analysis_run_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    internal_request_id VARCHAR(80) NOT NULL,
    provider_request_id VARCHAR(191) NULL,
    settlement_status VARCHAR(30) NOT NULL DEFAULT 'reserved',
    reserved_tokens INT NOT NULL DEFAULT 0,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    finish_reason VARCHAR(60) NULL,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    dispatched_at DATETIME(6) NULL,
    settled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_provider_calls_run FOREIGN KEY (analysis_run_id) REFERENCES ai_analysis_runs(id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    UNIQUE KEY uk_agent_provider_call_round (analysis_run_id, round_no),
    UNIQUE KEY uk_agent_provider_internal_request (internal_request_id),
    INDEX idx_agent_provider_call_settlement (settlement_status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
