SELECT
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
       AND column_name IN ('task_context_version','task_context_json','task_context_hash'))
       AS session_context_columns,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND column_name IN ('task_context_version','task_context_json','task_context_hash'))
       AS run_context_columns,
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE()
       AND table_name='ai_agent_sessions'
       AND index_name='idx_agent_sessions_task_context_hash')
       AS task_context_indexes,
    (SELECT COUNT(*) FROM ai_agent_sessions
     WHERE (task_context_version IS NULL AND task_context_json IS NOT NULL)
        OR (task_context_version IS NOT NULL AND task_context_json IS NULL)
        OR (task_context_hash IS NULL AND task_context_json IS NOT NULL)
        OR (task_context_hash IS NOT NULL AND task_context_json IS NULL)
        OR (task_context_json IS NOT NULL AND JSON_VALID(task_context_json)=0))
       AS incomplete_session_contexts;
