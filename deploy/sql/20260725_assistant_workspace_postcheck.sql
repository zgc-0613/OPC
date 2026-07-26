WITH migration_boundary AS (
    SELECT COALESCE(
        (SELECT STR_TO_DATE(setting_value, '%Y-%m-%d %H:%i:%s.%f')
         FROM app_settings
         WHERE setting_key='migration.assistant_workspace_rollout_at'
         LIMIT 1),
        TIMESTAMP('2026-07-25 21:56:34.000000')
    ) AS assistant_workspace_backfill_cutoff
), expected_indexes AS (
    SELECT 'ai_agent_sessions' AS table_name,
           'idx_agent_sessions_history_active' AS index_name,
           1 AS non_unique,
           'user_id,deleted_at,pinned_at,last_message_at,id' AS ordered_columns
    UNION ALL SELECT 'ai_agent_sessions', 'idx_agent_sessions_history_archived', 1,
                     'user_id,archived_at,last_message_at,id'
    UNION ALL SELECT 'ai_agent_sessions', 'idx_agent_sessions_purge_due', 1,
                     'purge_after,purged_at,id'
    UNION ALL SELECT 'ai_agent_messages', 'uk_agent_message_sequence', 0,
                     'session_id,sequence_no'
    UNION ALL SELECT 'ai_agent_messages', 'idx_agent_messages_session_created', 1,
                     'session_id,created_at,id'
    UNION ALL SELECT 'ai_agent_content_purge_audits',
                     'idx_agent_purge_audits_session_created', 1,
                     'session_id,created_at'
    UNION ALL SELECT 'ai_agent_content_purge_audits',
                     'idx_agent_purge_audits_user_created', 1,
                     'user_id,created_at'
), actual_indexes AS (
    SELECT table_name, index_name, MIN(non_unique) AS non_unique,
           GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS ordered_columns
    FROM information_schema.statistics
    WHERE table_schema=DATABASE()
      AND table_name IN (
        'ai_agent_sessions','ai_agent_messages','ai_agent_content_purge_audits'
      )
      AND index_name IN (
        'idx_agent_sessions_history_active',
        'idx_agent_sessions_history_archived',
        'idx_agent_sessions_purge_due',
        'uk_agent_message_sequence',
        'idx_agent_messages_session_created',
        'idx_agent_purge_audits_session_created',
        'idx_agent_purge_audits_user_created'
      )
    GROUP BY table_name,index_name
), matched_indexes AS (
    SELECT expected.table_name, expected.index_name
    FROM expected_indexes expected
    JOIN actual_indexes actual
      ON actual.table_name=expected.table_name
     AND actual.index_name=expected.index_name
     AND actual.non_unique=expected.non_unique
     AND actual.ordered_columns=expected.ordered_columns
)
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
       AND column_name IN ('title_mode','pinned_at','archived_at','deleted_at','purge_after','purged_at')) AS workspace_columns,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND (
       (table_name='ai_agent_sessions' AND column_name='content_generation'
        AND data_type='bigint' AND is_nullable='NO' AND column_default='0')
       OR (table_name='ai_analysis_runs' AND column_name='submission_kind'
        AND data_type='varchar' AND character_maximum_length=20
        AND is_nullable='NO' AND column_default='message')
       OR (table_name='ai_analysis_runs' AND column_name IN ('request_content_hash','start_profile_hash')
        AND data_type='char' AND character_maximum_length=64 AND is_nullable='YES')
       OR (table_name='ai_analysis_runs' AND column_name='session_content_generation'
        AND data_type='bigint' AND is_nullable='NO' AND column_default='0')
     )) AS stability_columns,
    (SELECT COUNT(*) FROM matched_indexes
     WHERE table_name='ai_agent_sessions') AS workspace_indexes,
    (SELECT IF(COUNT(*)=1,2,0) FROM matched_indexes
     WHERE table_name='ai_agent_messages' AND index_name='uk_agent_message_sequence')
       AS message_order_index_columns,
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema=DATABASE()
       AND table_name='ai_agent_content_purge_audits'
       AND table_type='BASE TABLE') AS purge_audit_tables,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE()
       AND table_name='ai_agent_content_purge_audits'
       AND (
         (column_name='id' AND data_type='bigint' AND is_nullable='NO'
          AND extra LIKE '%auto_increment%')
         OR (column_name='operation' AND data_type='varchar'
          AND character_maximum_length=40 AND is_nullable='NO')
         OR (column_name='session_id' AND data_type='bigint' AND is_nullable='NO')
         OR (column_name='user_id' AND data_type='bigint' AND is_nullable='YES')
         OR (column_name='operator_type' AND data_type='varchar'
          AND character_maximum_length=20 AND is_nullable='NO')
         OR (column_name='operator_id' AND data_type='bigint' AND is_nullable='YES')
         OR (column_name='result' AND data_type='varchar'
          AND character_maximum_length=20 AND is_nullable='NO')
         OR (column_name='diagnostic_code' AND data_type='varchar'
          AND character_maximum_length=80 AND is_nullable='YES')
         OR (column_name='created_at' AND data_type='datetime'
          AND datetime_precision=6 AND is_nullable='NO'
          AND UPPER(column_default)='CURRENT_TIMESTAMP(6)')
       )) AS purge_audit_columns,
    (SELECT COUNT(*) FROM matched_indexes
     WHERE table_name='ai_agent_content_purge_audits') AS purge_audit_indexes,
    (SELECT COUNT(*) FROM information_schema.key_column_usage
     WHERE table_schema=DATABASE()
       AND table_name='ai_agent_content_purge_audits'
       AND referenced_table_name IS NOT NULL) AS purge_audit_foreign_keys,
    (SELECT COUNT(*) FROM app_settings
     WHERE setting_key='migration.assistant_workspace_rollout_at'
       AND STR_TO_DATE(setting_value, '%Y-%m-%d %H:%i:%s.%f') IS NOT NULL)
       AS rollout_boundary_settings,
    (SELECT COUNT(*) FROM ai_agent_sessions
     WHERE title_mode NOT IN ('auto','manual') OR title_mode IS NULL) AS invalid_title_modes,
    (SELECT COUNT(*) FROM ai_agent_sessions
     WHERE status='archived' AND archived_at IS NULL) AS missing_archived_timestamps,
    (SELECT COALESCE(GROUP_CONCAT(
        CONCAT(expected.table_name,'.',expected.index_name)
        ORDER BY expected.table_name,expected.index_name SEPARATOR ','
     ), '')
     FROM expected_indexes expected
     LEFT JOIN actual_indexes actual ON actual.index_name=expected.index_name
       AND actual.table_name=expected.table_name
     WHERE actual.index_name IS NULL) AS missing_indexes,
    (SELECT COALESCE(GROUP_CONCAT(
        CONCAT(
          expected.table_name,'.',expected.index_name,
          '(non_unique=',actual.non_unique,
          ';columns=',actual.ordered_columns,
          ';expected_non_unique=',expected.non_unique,
          ';expected_columns=',expected.ordered_columns,')'
        )
        ORDER BY expected.table_name,expected.index_name SEPARATOR ','
     ), '')
     FROM expected_indexes expected
     JOIN actual_indexes actual
       ON actual.index_name=expected.index_name AND actual.table_name=expected.table_name
     WHERE actual.non_unique<>expected.non_unique
        OR actual.ordered_columns<>expected.ordered_columns) AS invalid_index_definitions,
    (SELECT COUNT(*) FROM ai_agent_sessions sessions
     CROSS JOIN migration_boundary boundary
     WHERE sessions.title_mode='auto'
       AND sessions.created_at < boundary.assistant_workspace_backfill_cutoff)
       AS historic_auto_titles;
