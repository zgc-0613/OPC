SELECT
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema=DATABASE()
       AND table_name IN ('ai_agent_sessions','ai_agent_messages','ai_agent_tool_calls')) AS agent_tables,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND column_name IN ('session_id','user_message_id','idempotency_key','step_count',
         'tool_call_count','current_stage','visible_progress','cancelled_at','completed_at','session_active_guard')) AS run_columns,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_model_settings'
       AND column_name IN ('agent_enabled','agent_max_model_rounds','agent_max_tool_calls',
         'agent_max_tokens','agent_history_window','agent_timeout_seconds','agent_tool_mode')) AS settings_columns,
    (SELECT COUNT(*) FROM information_schema.referential_constraints
     WHERE constraint_schema=DATABASE()
       AND constraint_name IN ('fk_agent_sessions_user','fk_agent_messages_session',
         'fk_ai_runs_agent_session','fk_ai_runs_user_message','fk_agent_messages_run',
         'fk_agent_tool_calls_run')) AS agent_foreign_keys,
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE()
       AND index_name IN ('uk_agent_message_sequence','uk_ai_runs_active_session',
         'uk_ai_runs_idempotency','uk_agent_tool_step')) AS agent_unique_indexes,
    (SELECT COUNT(*) FROM ai_model_settings
     WHERE agent_max_model_rounds NOT BETWEEN 1 AND 8
        OR agent_max_tool_calls NOT BETWEEN 1 AND 12
        OR agent_max_tokens NOT BETWEEN 512 AND 32000
        OR agent_history_window NOT BETWEEN 1 AND 24
        OR agent_timeout_seconds NOT BETWEEN 10 AND 600
        OR agent_tool_mode NOT IN ('json_plan','native')) AS invalid_agent_settings;
