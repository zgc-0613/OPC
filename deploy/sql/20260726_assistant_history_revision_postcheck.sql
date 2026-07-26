SELECT
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='platform_users'
       AND column_name='assistant_history_revision') AS revision_columns,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='platform_users'
       AND column_name='assistant_history_revision'
       AND column_type='bigint' AND is_nullable='NO' AND column_default='0')
       AS valid_revision_definitions,
    (SELECT COUNT(*) FROM platform_users
     WHERE assistant_history_revision < 0) AS invalid_revision_values;
