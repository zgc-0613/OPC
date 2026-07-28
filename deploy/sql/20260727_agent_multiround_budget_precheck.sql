SELECT
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='ai_model_settings'
     AND column_name='agent_max_tokens') AS token_budget_column_count,
  (SELECT COUNT(*) FROM ai_model_settings WHERE id=1) AS settings_row_count,
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
     AND column_name='requested_intent') AS requested_intent_column_count;
