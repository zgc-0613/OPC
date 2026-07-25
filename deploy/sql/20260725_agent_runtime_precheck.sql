SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('platform_users', 'ai_analysis_runs', 'ai_model_settings');
