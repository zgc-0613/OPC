-- Phase Three analytics snapshot migration prerequisites and existing state.
SELECT
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema=DATABASE()
       AND table_name IN ('platform_users','ai_analysis_runs')) AS required_tables,
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema=DATABASE()
       AND table_name='ai_analytics_snapshots') AS existing_snapshot_table,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE()
       AND table_name='ai_analytics_snapshots'
       AND column_name IN (
           'id','user_id','metric_id','normalized_filters_json',
           'selected_dimension','selected_bucket_ids_json','data_version',
           'snapshot_json','snapshot_hash','idempotency_key','request_hash',
           'run_id','expires_at',
           'created_at','updated_at')) AS existing_snapshot_columns,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE()
       AND table_name='ai_analysis_runs'
       AND column_name IN (
           'analytics_snapshot_id','analytics_metric_id',
           'analytics_data_version','analytics_filters_json',
           'analytics_snapshot_json')) AS existing_run_snapshot_columns,
    (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
     WHERE table_schema=DATABASE()
       AND table_name='ai_analytics_snapshots'
       AND index_name IN (
           'idx_ai_analytics_snapshots_owner_expiry',
           'idx_ai_analytics_snapshots_expiry',
           'idx_ai_analytics_snapshots_run',
           'uk_ai_analytics_snapshots_owner_idempotency')) AS existing_snapshot_indexes,
    (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
     WHERE table_schema=DATABASE()
       AND table_name='ai_analysis_runs'
       AND index_name IN (
           'idx_ai_runs_analytics_snapshot',
           'idx_ai_runs_analytics_data_version')) AS existing_run_snapshot_indexes;
