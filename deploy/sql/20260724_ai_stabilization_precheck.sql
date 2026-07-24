SELECT 'ai_stabilization_precheck' AS check_name, NOW() AS checked_at;

SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'tags', 'ai_model_settings', 'ai_analysis_runs', 'case_tags',
      'tag_aliases', 'industry_tag_review_candidates', 'ai_evidence_reviews'
  )
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN (
      'tags', 'ai_analysis_runs', 'case_tags', 'tag_aliases',
      'industry_tag_review_candidates', 'ai_evidence_reviews'
  )
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

SELECT status, COUNT(*) AS source_count
FROM sources
WHERE status IN ('active', 'published')
GROUP BY status
ORDER BY status;
