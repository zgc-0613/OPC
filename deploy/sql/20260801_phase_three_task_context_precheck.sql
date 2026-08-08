-- table_name='ai_agent_sessions' column_name='task_context_version'
-- table_name='ai_agent_sessions' column_name='task_context_json'
-- table_name='ai_agent_sessions' column_name='task_context_hash'
SELECT
    -- Explicit column names remain in comments so scripts can audit each field.
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema=DATABASE()
       AND table_name IN ('ai_agent_sessions','ai_analysis_runs')) AS required_tables,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE()
       AND table_name='ai_agent_sessions'
       AND column_name IN ('task_context_version','task_context_json','task_context_hash'))
       AS existing_task_context_columns,
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE()
       AND table_name='ai_agent_sessions'
       AND index_name='idx_agent_sessions_task_context_hash')
       AS existing_task_context_indexes;
