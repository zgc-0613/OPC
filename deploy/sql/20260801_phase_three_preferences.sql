SET @phase3_preferences_table_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='ai_research_preferences'
);
SET @phase3_preferences_sql = IF(@phase3_preferences_table_exists=0,
    'CREATE TABLE ai_research_preferences (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, memory_enabled BOOLEAN NOT NULL DEFAULT FALSE, common_region VARCHAR(120) NULL, common_industry VARCHAR(120) NULL, technology_direction VARCHAR(80) NULL, venture_stage VARCHAR(80) NULL, budget_range VARCHAR(120) NULL, team_capabilities VARCHAR(500) NULL, existing_resources VARCHAR(500) NULL, policy_focus VARCHAR(500) NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), UNIQUE KEY uk_ai_research_preferences_user (user_id), CONSTRAINT fk_ai_research_preferences_user FOREIGN KEY (user_id) REFERENCES platform_users(id))',
    'SELECT 1');
PREPARE phase3_preferences_stmt FROM @phase3_preferences_sql; EXECUTE phase3_preferences_stmt; DEALLOCATE PREPARE phase3_preferences_stmt;

SET @phase3_preferences_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='ai_research_preferences' AND column_name='memory_enabled'
);
SET @phase3_preferences_postcheck = IF(@phase3_preferences_column_exists=1, 'SELECT 1', 'SELECT 1/0');
PREPARE phase3_preferences_postcheck_stmt FROM @phase3_preferences_postcheck; EXECUTE phase3_preferences_postcheck_stmt; DEALLOCATE PREPARE phase3_preferences_postcheck_stmt;
