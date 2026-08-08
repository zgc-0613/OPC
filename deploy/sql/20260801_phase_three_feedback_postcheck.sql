SELECT
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema=DATABASE() AND table_name='ai_agent_run_feedback' AND table_type='BASE TABLE') AS feedback_table,
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='ai_agent_run_feedback'
     AND column_name IN ('user_id','run_id','rating','reason','comment_text','revision','created_at','updated_at')) AS required_columns,
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='ai_agent_run_feedback'
     AND index_name='uk_ai_agent_run_feedback_owner_run') AS owner_run_index,
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema=DATABASE() AND table_name='ai_agent_run_feedback'
     AND index_name='idx_ai_agent_run_feedback_run_created') AS run_created_index,
  (SELECT COUNT(*) FROM ai_agent_run_feedback WHERE revision <= 0) AS invalid_revisions,
  (SELECT COUNT(*) FROM ai_agent_run_feedback WHERE rating NOT IN ('helpful','not_helpful')) AS invalid_ratings;
