SELECT
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema=DATABASE() AND table_name='platform_users'
       AND table_type='BASE TABLE') AS required_user_tables,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='platform_users'
       AND column_name='assistant_history_revision') AS existing_revision_columns;
