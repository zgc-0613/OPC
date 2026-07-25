-- Separate policy support-measure tags from reviewed industry applicability.
-- Existing policies remain unclassified until an administrator reviews them.

SET @has_applicability_mode := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'policies'
      AND column_name = 'applicability_mode'
);
SET @sql := IF(
    @has_applicability_mode = 0,
    'ALTER TABLE policies ADD COLUMN applicability_mode VARCHAR(20) NOT NULL DEFAULT ''unclassified'' AFTER policy_type',
    'SELECT 1'
);
PREPARE policy_applicability_column FROM @sql;
EXECUTE policy_applicability_column;
DEALLOCATE PREPARE policy_applicability_column;

UPDATE policies
SET applicability_mode = 'unclassified'
WHERE applicability_mode IS NULL
   OR applicability_mode NOT IN ('general', 'specific', 'unclassified');

CREATE TABLE IF NOT EXISTS policy_industry_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_id BIGINT NOT NULL,
    industry_tag_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_policy_industry_policy_tag (policy_id, industry_tag_id),
    INDEX idx_policy_industry_policy_id (policy_id),
    INDEX idx_policy_industry_tag_id (industry_tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @has_policy_industry_unique := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'policy_industry_tags'
      AND index_name = 'uk_policy_industry_policy_tag'
);
SET @sql := IF(
    @has_policy_industry_unique = 0,
    'ALTER TABLE policy_industry_tags ADD UNIQUE KEY uk_policy_industry_policy_tag (policy_id, industry_tag_id)',
    'SELECT 1'
);
PREPARE policy_applicability_unique FROM @sql;
EXECUTE policy_applicability_unique;
DEALLOCATE PREPARE policy_applicability_unique;

SET @has_policy_industry_policy_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'policy_industry_tags'
      AND index_name = 'idx_policy_industry_policy_id'
);
SET @sql := IF(
    @has_policy_industry_policy_index = 0,
    'ALTER TABLE policy_industry_tags ADD INDEX idx_policy_industry_policy_id (policy_id)',
    'SELECT 1'
);
PREPARE policy_applicability_policy_index FROM @sql;
EXECUTE policy_applicability_policy_index;
DEALLOCATE PREPARE policy_applicability_policy_index;

SET @has_policy_industry_tag_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'policy_industry_tags'
      AND index_name = 'idx_policy_industry_tag_id'
);
SET @sql := IF(
    @has_policy_industry_tag_index = 0,
    'ALTER TABLE policy_industry_tags ADD INDEX idx_policy_industry_tag_id (industry_tag_id)',
    'SELECT 1'
);
PREPARE policy_applicability_tag_index FROM @sql;
EXECUTE policy_applicability_tag_index;
DEALLOCATE PREPARE policy_applicability_tag_index;

SET @policy_industry_policy_orphans := (
    SELECT COUNT(*) FROM policy_industry_tags relation
    LEFT JOIN policies policy ON policy.id = relation.policy_id
    WHERE policy.id IS NULL
);
SET @policy_industry_tag_orphans := (
    SELECT COUNT(*) FROM policy_industry_tags relation
    LEFT JOIN tags tag ON tag.id = relation.industry_tag_id
    WHERE tag.id IS NULL
);
SET @sql := IF(
    @policy_industry_policy_orphans = 0 AND @policy_industry_tag_orphans = 0,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''policy_industry_tags contains orphaned rows; repair before adding foreign keys'''
);
PREPARE policy_applicability_orphan_guard FROM @sql;
EXECUTE policy_applicability_orphan_guard;
DEALLOCATE PREPARE policy_applicability_orphan_guard;

SET @has_policy_industry_policy_fk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'policy_industry_tags'
      AND constraint_name = 'fk_policy_industry_policy' AND constraint_type = 'FOREIGN KEY'
);
SET @sql := IF(
    @has_policy_industry_policy_fk = 0,
    'ALTER TABLE policy_industry_tags ADD CONSTRAINT fk_policy_industry_policy FOREIGN KEY (policy_id) REFERENCES policies(id) ON DELETE RESTRICT ON UPDATE RESTRICT',
    'SELECT 1'
);
PREPARE policy_applicability_policy_fk FROM @sql;
EXECUTE policy_applicability_policy_fk;
DEALLOCATE PREPARE policy_applicability_policy_fk;

SET @has_policy_industry_tag_fk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'policy_industry_tags'
      AND constraint_name = 'fk_policy_industry_tag' AND constraint_type = 'FOREIGN KEY'
);
SET @sql := IF(
    @has_policy_industry_tag_fk = 0,
    'ALTER TABLE policy_industry_tags ADD CONSTRAINT fk_policy_industry_tag FOREIGN KEY (industry_tag_id) REFERENCES tags(id) ON DELETE RESTRICT ON UPDATE RESTRICT',
    'SELECT 1'
);
PREPARE policy_applicability_tag_fk FROM @sql;
EXECUTE policy_applicability_tag_fk;
DEALLOCATE PREPARE policy_applicability_tag_fk;

SELECT
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema=DATABASE() AND table_name='policies' AND column_name='applicability_mode') AS applicability_columns,
    (SELECT COUNT(*) FROM information_schema.referential_constraints
     WHERE constraint_schema=DATABASE()
       AND constraint_name IN ('fk_policy_industry_policy', 'fk_policy_industry_tag')) AS applicability_foreign_keys,
    (SELECT COUNT(*) FROM policies WHERE applicability_mode='unclassified') AS unclassified_policies;
