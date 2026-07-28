SET @sql := IF((
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
      AND column_name='requested_intent'
) = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN requested_intent VARCHAR(40) NOT NULL DEFAULT ''auto'' AFTER submission_kind',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ai_model_settings
SET agent_max_tokens=28000
WHERE id=1 AND agent_max_tokens < 28000;

UPDATE ai_model_settings
SET agent_max_model_rounds=5
WHERE id=1 AND agent_max_model_rounds < 5;
