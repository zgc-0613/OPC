SELECT COUNT(*) AS preferences_table_exists
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='ai_research_preferences';
SELECT COUNT(*) AS preferences_user_index_exists
FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='ai_research_preferences' AND index_name='uk_ai_research_preferences_user';
