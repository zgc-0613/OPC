WITH expected_indexes AS (
    SELECT 'idx_agent_sessions_history_active' AS index_name
    UNION ALL SELECT 'idx_agent_sessions_history_archived'
    UNION ALL SELECT 'idx_agent_sessions_purge_due'
), actual_indexes AS (
    SELECT DISTINCT index_name
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
)
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='ai_agent_sessions'
       AND column_name IN ('title_mode','pinned_at','archived_at','deleted_at','purge_after','purged_at')) AS workspace_columns,
    (SELECT COUNT(*) FROM expected_indexes expected
     JOIN actual_indexes actual ON actual.index_name=expected.index_name) AS workspace_indexes,
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema=DATABASE() AND table_name='ai_agent_messages'
       AND index_name='uk_agent_message_sequence'
       AND column_name IN ('session_id','sequence_no')) AS message_order_index_columns,
    (SELECT COUNT(*) FROM ai_agent_sessions
     WHERE title_mode NOT IN ('auto','manual') OR title_mode IS NULL) AS invalid_title_modes,
    (SELECT COUNT(*) FROM ai_agent_sessions
     WHERE status='archived' AND archived_at IS NULL) AS missing_archived_timestamps,
    (SELECT COALESCE(GROUP_CONCAT(expected.index_name ORDER BY expected.index_name SEPARATOR ','), '')
     FROM expected_indexes expected
     LEFT JOIN actual_indexes actual ON actual.index_name=expected.index_name
     WHERE actual.index_name IS NULL) AS missing_indexes;
