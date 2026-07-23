SET @model_catalog_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_model_settings'
      AND column_name = 'model_catalog_json'
);

SET @model_catalog_sql = IF(
    @model_catalog_column_exists = 0,
    'ALTER TABLE ai_model_settings ADD COLUMN model_catalog_json JSON NULL AFTER model_id',
    'SELECT 1'
);

PREPARE model_catalog_statement FROM @model_catalog_sql;
EXECUTE model_catalog_statement;
DEALLOCATE PREPARE model_catalog_statement;
