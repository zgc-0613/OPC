SELECT
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='ai_research_reports' AND table_type='BASE TABLE') AS report_table,
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_research_reports' AND column_name IN ('user_id','session_id','run_id','final_message_id','evidence_version','result_json','citation_manifest_json','status','revision')) AS required_columns,
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='ai_research_reports' AND index_name='uk_ai_research_reports_user_idempotency') AS idempotency_index,
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='ai_research_reports' AND index_name='idx_ai_research_reports_owner_status') AS owner_status_index,
  (SELECT COUNT(*) FROM ai_research_reports WHERE revision <= 0) AS invalid_revisions;
