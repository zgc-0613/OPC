SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'case_items' AND column_name = 'evidence_revision') = 0,
    'ALTER TABLE case_items ADD COLUMN evidence_revision BIGINT NOT NULL DEFAULT 0 AFTER ai_evidence_status',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
UPDATE case_items SET evidence_revision = 0 WHERE evidence_revision IS NULL;
ALTER TABLE case_items MODIFY COLUMN evidence_revision BIGINT NOT NULL DEFAULT 0;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'policies' AND column_name = 'evidence_revision') = 0,
    'ALTER TABLE policies ADD COLUMN evidence_revision BIGINT NOT NULL DEFAULT 0 AFTER ai_evidence_status',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
UPDATE policies SET evidence_revision = 0 WHERE evidence_revision IS NULL;
ALTER TABLE policies MODIFY COLUMN evidence_revision BIGINT NOT NULL DEFAULT 0;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sources' AND column_name = 'evidence_revision') = 0,
    'ALTER TABLE sources ADD COLUMN evidence_revision BIGINT NOT NULL DEFAULT 0 AFTER ai_evidence_status',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
UPDATE sources SET evidence_revision = 0 WHERE evidence_revision IS NULL;
ALTER TABLE sources MODIFY COLUMN evidence_revision BIGINT NOT NULL DEFAULT 0;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'action_type') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN action_type VARCHAR(50) NULL AFTER admin_username',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'reason') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN reason VARCHAR(500) NULL AFTER action_type',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND column_name = 'operation_id') = 0,
    'ALTER TABLE ai_evidence_reviews ADD COLUMN operation_id VARCHAR(36) NULL AFTER notes',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews' AND index_name = 'idx_evidence_reviews_operation') = 0,
    'ALTER TABLE ai_evidence_reviews ADD INDEX idx_evidence_reviews_operation (operation_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ai_evidence_reviews
SET action_type = 'legacy_review'
WHERE action_type IS NULL OR action_type = '';
ALTER TABLE ai_evidence_reviews
    MODIFY COLUMN action_type VARCHAR(50) NOT NULL DEFAULT 'legacy_review';

SELECT
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name IN ('case_items', 'policies', 'sources')
       AND column_name = 'evidence_revision'
       AND data_type = 'bigint'
       AND is_nullable = 'NO'
       AND column_default = '0') AS evidence_revision_column_count,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews'
       AND (
           (column_name = 'action_type' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO')
           OR (column_name = 'reason' AND data_type = 'varchar' AND character_maximum_length = 500)
           OR (column_name = 'operation_id' AND data_type = 'varchar' AND character_maximum_length = 36)
       )) AS workbench_column_count,
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'ai_evidence_reviews'
       AND index_name = 'idx_evidence_reviews_operation'
       AND column_name = 'operation_id'
       AND seq_in_index = 1) AS workbench_index_count;
