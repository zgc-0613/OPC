SELECT
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema=DATABASE()
       AND table_name IN ('ai_agent_sessions','ai_agent_messages','ai_analysis_runs')) AS required_tables,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
       AND column_name IN ('user_id','title','status','version','last_message_at')) AS required_session_columns,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_agent_messages'
       AND column_name IN ('session_id','sequence_no','content')) AS required_message_columns;
