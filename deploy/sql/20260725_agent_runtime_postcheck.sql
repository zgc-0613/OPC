WITH expected_indexes AS (
    SELECT 'ai_agent_sessions' AS table_name, 'idx_agent_sessions_user_activity' AS index_name
    UNION ALL SELECT 'ai_agent_messages', 'uk_agent_message_sequence'
    UNION ALL SELECT 'ai_agent_messages', 'idx_agent_messages_run'
    UNION ALL SELECT 'ai_agent_messages', 'idx_agent_messages_session_created'
    UNION ALL SELECT 'ai_analysis_runs', 'idx_ai_runs_session'
    UNION ALL SELECT 'ai_analysis_runs', 'uk_ai_runs_active_session'
    UNION ALL SELECT 'ai_analysis_runs', 'uk_ai_runs_idempotency'
    UNION ALL SELECT 'ai_analysis_runs', 'idx_ai_runs_agent_lease'
    UNION ALL SELECT 'ai_analysis_runs', 'uk_ai_runs_agent_session_nonterminal'
    UNION ALL SELECT 'ai_analysis_runs', 'uk_ai_runs_agent_user_nonterminal'
    UNION ALL SELECT 'ai_agent_tool_calls', 'uk_agent_tool_step'
    UNION ALL SELECT 'ai_agent_tool_calls', 'idx_agent_tool_run_status'
    UNION ALL SELECT 'ai_agent_provider_calls', 'uk_agent_provider_call_round'
    UNION ALL SELECT 'ai_agent_provider_calls', 'uk_agent_provider_internal_request'
    UNION ALL SELECT 'ai_agent_provider_calls', 'idx_agent_provider_call_settlement'
), actual_agent_indexes AS (
    SELECT DISTINCT table_name, index_name
    FROM information_schema.statistics
    WHERE table_schema=DATABASE()
      AND index_name <> 'PRIMARY'
      AND (
        table_name IN ('ai_agent_sessions','ai_agent_messages','ai_agent_tool_calls','ai_agent_provider_calls')
        OR (table_name='ai_analysis_runs' AND index_name IN (
          'idx_ai_runs_session','uk_ai_runs_active_session','uk_ai_runs_idempotency',
          'idx_ai_runs_agent_lease','uk_ai_runs_agent_session_nonterminal',
          'uk_ai_runs_agent_user_nonterminal'
        ))
      )
)
SELECT
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema=DATABASE()
       AND table_name IN ('ai_agent_sessions','ai_agent_messages','ai_agent_tool_calls',
         'ai_agent_provider_calls')) AS agent_tables,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND column_name IN ('session_id','user_message_id','idempotency_key','step_count',
         'tool_call_count','current_stage','visible_progress','cancelled_at','completed_at','session_active_guard',
         'lease_owner','lease_expires_at','execution_attempts','next_attempt_at','last_recovery_reason',
         'settlement_status','provider_dispatched_at','settled_at','settlement_version',
         'session_nonterminal_guard','user_agent_nonterminal_guard')) AS run_columns,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_model_settings'
       AND column_name IN ('agent_enabled','agent_max_model_rounds','agent_max_tool_calls',
         'agent_max_tokens','agent_history_window','agent_timeout_seconds','agent_tool_mode',
         'agent_rollout_state','agent_rollout_changed_at','agent_rollout_changed_by_admin_id')) AS settings_columns,
    (SELECT COUNT(*) FROM information_schema.referential_constraints
     WHERE constraint_schema=DATABASE()
       AND constraint_name IN ('fk_agent_sessions_user','fk_agent_messages_session',
         'fk_ai_runs_agent_session','fk_ai_runs_user_message','fk_agent_messages_run',
         'fk_agent_tool_calls_run','fk_agent_provider_calls_run')) AS agent_foreign_keys,
    (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
     WHERE table_schema=DATABASE()
       AND index_name IN ('uk_agent_message_sequence','uk_ai_runs_active_session',
         'uk_ai_runs_idempotency','uk_agent_tool_step','uk_ai_runs_agent_session_nonterminal',
         'uk_ai_runs_agent_user_nonterminal','uk_agent_provider_call_round',
         'uk_agent_provider_internal_request')) AS agent_unique_indexes,
    (SELECT COALESCE(GROUP_CONCAT(
        CONCAT(expected.table_name, '.', expected.index_name)
        ORDER BY expected.table_name, expected.index_name SEPARATOR ','
     ), '')
     FROM expected_indexes expected
     LEFT JOIN actual_agent_indexes actual
       ON actual.table_name=expected.table_name AND actual.index_name=expected.index_name
     WHERE actual.index_name IS NULL) AS missing_indexes,
    (SELECT COALESCE(GROUP_CONCAT(
        CONCAT(actual.table_name, '.', actual.index_name)
        ORDER BY actual.table_name, actual.index_name SEPARATOR ','
     ), '')
     FROM actual_agent_indexes actual
     LEFT JOIN expected_indexes expected
       ON expected.table_name=actual.table_name AND expected.index_name=actual.index_name
     WHERE expected.index_name IS NULL) AS unexpected_indexes,
    (SELECT COUNT(*) FROM ai_model_settings
     WHERE agent_max_model_rounds NOT BETWEEN 1 AND 8
        OR agent_max_tool_calls NOT BETWEEN 1 AND 12
        OR agent_max_tokens NOT BETWEEN 512 AND 32000
        OR agent_history_window NOT BETWEEN 1 AND 24
        OR agent_timeout_seconds NOT BETWEEN 10 AND 600
        OR agent_tool_mode NOT IN ('json_plan','native')
        OR agent_rollout_state NOT IN ('explicitly_disabled','explicitly_enabled')
        OR (agent_enabled=1 AND agent_rollout_state<>'explicitly_enabled')
        OR (agent_enabled=0 AND agent_rollout_state<>'explicitly_disabled')) AS invalid_agent_settings;
