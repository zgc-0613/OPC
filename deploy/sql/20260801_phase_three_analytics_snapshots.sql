-- Forward-only, idempotent Phase Three analytics snapshot storage.
-- Snapshots are user-owned immutable inputs for analytics-originated research runs.

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema=DATABASE() AND table_name='ai_analytics_snapshots') = 0,
    'CREATE TABLE ai_analytics_snapshots (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        user_id BIGINT NOT NULL,
        metric_id VARCHAR(80) NOT NULL,
        normalized_filters_json JSON NOT NULL,
        selected_dimension VARCHAR(80) NULL,
        selected_bucket_ids_json JSON NOT NULL,
        data_version VARCHAR(128) NOT NULL,
        snapshot_json JSON NOT NULL,
        snapshot_hash CHAR(64) NOT NULL,
        idempotency_key VARCHAR(64) NOT NULL,
        request_hash CHAR(64) NOT NULL,
        run_id BIGINT NULL,
        expires_at DATETIME(6) NOT NULL,
        created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
        updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
        KEY idx_ai_analytics_snapshots_owner_expiry (user_id, expires_at, id),
        KEY idx_ai_analytics_snapshots_expiry (expires_at, id),
        KEY idx_ai_analytics_snapshots_run (run_id),
        UNIQUE KEY uk_ai_analytics_snapshots_owner_idempotency (user_id, idempotency_key)
    ) ENGINE=InnoDB',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analytics_snapshots'
       AND column_name='idempotency_key') = 0,
    'ALTER TABLE ai_analytics_snapshots ADD COLUMN idempotency_key VARCHAR(64) NOT NULL',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analytics_snapshots'
       AND column_name='request_hash') = 0,
    'ALTER TABLE ai_analytics_snapshots ADD COLUMN request_hash CHAR(64) NOT NULL',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='ai_analytics_snapshots'
       AND index_name='idx_ai_analytics_snapshots_owner_expiry') = 0,
    'ALTER TABLE ai_analytics_snapshots ADD INDEX idx_ai_analytics_snapshots_owner_expiry (user_id, expires_at, id)',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='ai_analytics_snapshots'
       AND index_name='uk_ai_analytics_snapshots_owner_idempotency') = 0,
    'ALTER TABLE ai_analytics_snapshots ADD UNIQUE INDEX uk_ai_analytics_snapshots_owner_idempotency (user_id, idempotency_key)',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='ai_analytics_snapshots'
       AND index_name='idx_ai_analytics_snapshots_expiry') = 0,
    'ALTER TABLE ai_analytics_snapshots ADD INDEX idx_ai_analytics_snapshots_expiry (expires_at, id)',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='ai_analytics_snapshots'
       AND index_name='idx_ai_analytics_snapshots_run') = 0,
    'ALTER TABLE ai_analytics_snapshots ADD INDEX idx_ai_analytics_snapshots_run (run_id)',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND column_name='analytics_snapshot_id') = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN analytics_snapshot_id BIGINT NULL',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND column_name='analytics_metric_id') = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN analytics_metric_id VARCHAR(80) NULL',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND column_name='analytics_data_version') = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN analytics_data_version VARCHAR(128) NULL',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND column_name='analytics_filters_json') = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN analytics_filters_json JSON NULL',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND column_name='analytics_snapshot_json') = 0,
    'ALTER TABLE ai_analysis_runs ADD COLUMN analytics_snapshot_json JSON NULL',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND index_name='idx_ai_runs_analytics_snapshot') = 0,
    'ALTER TABLE ai_analysis_runs ADD INDEX idx_ai_runs_analytics_snapshot (analytics_snapshot_id)',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;

SET @phase_three_analytics_snapshots_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
       AND index_name='idx_ai_runs_analytics_data_version') = 0,
    'ALTER TABLE ai_analysis_runs ADD INDEX idx_ai_runs_analytics_data_version (analytics_data_version)',
    'SELECT 1');
PREPARE phase_three_analytics_snapshots_stmt FROM @phase_three_analytics_snapshots_sql;
EXECUTE phase_three_analytics_snapshots_stmt;
DEALLOCATE PREPARE phase_three_analytics_snapshots_stmt;
