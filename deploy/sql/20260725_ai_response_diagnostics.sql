-- Persist only safe model-response diagnostics. Raw model output remains out of long-term storage.

SET @has_finish_reason := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_analysis_runs'
      AND column_name = 'finish_reason'
);
SET @sql := IF(
    @has_finish_reason = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN finish_reason VARCHAR(40) NULL AFTER provider_request_id',
    'SELECT 1'
);
PREPARE ai_response_finish_reason FROM @sql;
EXECUTE ai_response_finish_reason;
DEALLOCATE PREPARE ai_response_finish_reason;

SET @has_response_hash := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_analysis_runs'
      AND column_name = 'response_hash'
);
SET @sql := IF(
    @has_response_hash = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN response_hash CHAR(64) NULL AFTER finish_reason',
    'SELECT 1'
);
PREPARE ai_response_hash FROM @sql;
EXECUTE ai_response_hash;
DEALLOCATE PREPARE ai_response_hash;

SET @has_diagnostic_code := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_analysis_runs'
      AND column_name = 'diagnostic_code'
);
SET @sql := IF(
    @has_diagnostic_code = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN diagnostic_code VARCHAR(80) NULL AFTER error_type',
    'SELECT 1'
);
PREPARE ai_response_diagnostic_code FROM @sql;
EXECUTE ai_response_diagnostic_code;
DEALLOCATE PREPARE ai_response_diagnostic_code;

UPDATE ai_analysis_runs
SET response_hash = COALESCE(response_hash, SHA2(CAST(result_json AS CHAR), 256)),
    result_json = NULL
WHERE status IN ('completed', 'failed')
  AND result_json IS NOT NULL;

SELECT COUNT(*) AS diagnostic_columns
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'ai_analysis_runs'
  AND column_name IN ('finish_reason', 'response_hash', 'diagnostic_code');
