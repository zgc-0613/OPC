SELECT DATABASE() AS database_name, VERSION() AS mysql_version,
       (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='platform_users') AS platform_users_exists,
       (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='ai_research_preferences') AS preferences_exists;
