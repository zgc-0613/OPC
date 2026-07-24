SELECT
    (
        SELECT COUNT(*)
        FROM (
            SELECT 'tags' table_name, 'is_industry' object_name UNION ALL
            SELECT 'ai_model_settings', 'api_key_provider' UNION ALL
            SELECT 'ai_model_settings', 'api_key_origin' UNION ALL
            SELECT 'ai_analysis_runs', 'reserved_tokens' UNION ALL
            SELECT 'ai_analysis_runs', 'started_at' UNION ALL
            SELECT 'ai_analysis_runs', 'deadline_at' UNION ALL
            SELECT 'ai_analysis_runs', 'heartbeat_at' UNION ALL
            SELECT 'case_tags', 'id' UNION ALL
            SELECT 'case_tags', 'case_id' UNION ALL
            SELECT 'case_tags', 'tag_id' UNION ALL
            SELECT 'case_tags', 'created_at' UNION ALL
            SELECT 'tag_aliases', 'id' UNION ALL
            SELECT 'tag_aliases', 'tag_id' UNION ALL
            SELECT 'tag_aliases', 'alias' UNION ALL
            SELECT 'tag_aliases', 'normalized_alias' UNION ALL
            SELECT 'tag_aliases', 'created_at' UNION ALL
            SELECT 'tag_aliases', 'updated_at' UNION ALL
            SELECT 'industry_tag_review_candidates', 'id' UNION ALL
            SELECT 'industry_tag_review_candidates', 'tag_id' UNION ALL
            SELECT 'industry_tag_review_candidates', 'tag_name' UNION ALL
            SELECT 'industry_tag_review_candidates', 'policy_usage_count' UNION ALL
            SELECT 'industry_tag_review_candidates', 'case_usage_count' UNION ALL
            SELECT 'industry_tag_review_candidates', 'review_status' UNION ALL
            SELECT 'industry_tag_review_candidates', 'review_reason' UNION ALL
            SELECT 'industry_tag_review_candidates', 'created_at' UNION ALL
            SELECT 'industry_tag_review_candidates', 'updated_at' UNION ALL
            SELECT 'ai_evidence_reviews', 'id' UNION ALL
            SELECT 'ai_evidence_reviews', 'item_type' UNION ALL
            SELECT 'ai_evidence_reviews', 'item_id' UNION ALL
            SELECT 'ai_evidence_reviews', 'previous_status' UNION ALL
            SELECT 'ai_evidence_reviews', 'new_status' UNION ALL
            SELECT 'ai_evidence_reviews', 'admin_id' UNION ALL
            SELECT 'ai_evidence_reviews', 'admin_username' UNION ALL
            SELECT 'ai_evidence_reviews', 'notes' UNION ALL
            SELECT 'ai_evidence_reviews', 'created_at'
        ) expected
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema = DATABASE()
         AND actual.table_name = expected.table_name
         AND actual.column_name = expected.object_name
        WHERE actual.column_name IS NULL
    )
    + (
        SELECT COUNT(*)
        FROM (
            SELECT 'tags' table_name, 'idx_tags_is_industry' object_name UNION ALL
            SELECT 'ai_analysis_runs', 'idx_ai_runs_running_deadline' UNION ALL
            SELECT 'case_tags', 'uk_case_tags_case_tag' UNION ALL
            SELECT 'case_tags', 'idx_case_tags_case_id' UNION ALL
            SELECT 'case_tags', 'idx_case_tags_tag_id' UNION ALL
            SELECT 'tag_aliases', 'uk_tag_aliases_normalized' UNION ALL
            SELECT 'tag_aliases', 'idx_tag_aliases_tag_id' UNION ALL
            SELECT 'industry_tag_review_candidates', 'uk_industry_review_tag' UNION ALL
            SELECT 'industry_tag_review_candidates', 'idx_industry_review_status' UNION ALL
            SELECT 'ai_evidence_reviews', 'idx_evidence_reviews_item' UNION ALL
            SELECT 'ai_evidence_reviews', 'idx_evidence_reviews_created_at'
        ) expected
        LEFT JOIN information_schema.statistics actual
          ON actual.table_schema = DATABASE()
         AND actual.table_name = expected.table_name
         AND actual.index_name = expected.object_name
        WHERE actual.index_name IS NULL
    )
    + (
        SELECT COUNT(*)
        FROM (
            SELECT 'case_tags' table_name UNION ALL
            SELECT 'tag_aliases' UNION ALL
            SELECT 'industry_tag_review_candidates' UNION ALL
            SELECT 'ai_evidence_reviews'
        ) expected
        LEFT JOIN information_schema.table_constraints actual
          ON actual.table_schema = DATABASE()
         AND actual.table_name = expected.table_name
         AND actual.constraint_type = 'PRIMARY KEY'
        WHERE actual.constraint_name IS NULL
    )
    + (SELECT COUNT(*) FROM sources WHERE status = 'active')
    + (
        SELECT COUNT(*)
        FROM case_items case_item
        JOIN tag_aliases alias
          ON FIND_IN_SET(
              LOWER(alias.normalized_alias),
              LOWER(REPLACE(REPLACE(REPLACE(case_item.tags, '，', ','), ' ', ''), CHAR(9), ''))
          ) > 0
        JOIN tags tag ON tag.id = alias.tag_id AND tag.is_industry = 1
        LEFT JOIN case_tags relation
          ON relation.case_id = case_item.id AND relation.tag_id = alias.tag_id
        WHERE case_item.tags IS NOT NULL
          AND TRIM(case_item.tags) <> ''
          AND relation.id IS NULL
    )
    AS postcheck_failures;
