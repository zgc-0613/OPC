SELECT
  (SELECT COUNT(*)
   FROM ai_model_settings
   WHERE id=1
     AND agent_max_tokens BETWEEN 28000 AND 32000
     AND agent_max_model_rounds BETWEEN 5 AND 8) AS valid_runtime_budget_count,
  (SELECT COUNT(*)
   FROM information_schema.columns
   WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
     AND column_name='requested_intent'
     AND data_type='varchar' AND character_maximum_length=40
     AND is_nullable='NO' AND column_default='auto') AS valid_requested_intent_column_count;
