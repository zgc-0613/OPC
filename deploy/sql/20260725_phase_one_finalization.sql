-- Phase-one finalization: protect evidence parents from orphaned children.
-- This migration is additive and intentionally aborts when historical orphan
-- rows exist so an administrator can repair them instead of losing evidence.

SET @case_orphans := (
    SELECT COUNT(*)
    FROM case_items c
    LEFT JOIN sources s ON s.id = c.source_id
    WHERE c.source_id IS NOT NULL AND s.id IS NULL
);
SET @sql := IF(
    @case_orphans = 0,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''case_items contains orphaned source_id rows; repair before adding foreign key'''
);
PREPARE phase_one_case_orphan_guard FROM @sql;
EXECUTE phase_one_case_orphan_guard;
DEALLOCATE PREPARE phase_one_case_orphan_guard;

SET @policy_orphans := (
    SELECT COUNT(*)
    FROM policies p
    LEFT JOIN sources s ON s.id = p.source_id
    WHERE p.source_id IS NOT NULL AND s.id IS NULL
);
SET @sql := IF(
    @policy_orphans = 0,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''policies contains orphaned source_id rows; repair before adding foreign key'''
);
PREPARE phase_one_policy_orphan_guard FROM @sql;
EXECUTE phase_one_policy_orphan_guard;
DEALLOCATE PREPARE phase_one_policy_orphan_guard;

SET @has_case_source_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'case_items'
      AND index_name = 'idx_case_items_source_id'
);
SET @sql := IF(
    @has_case_source_index = 0,
    'ALTER TABLE case_items ADD INDEX idx_case_items_source_id (source_id)',
    'SELECT 1'
);
PREPARE phase_one_case_index FROM @sql;
EXECUTE phase_one_case_index;
DEALLOCATE PREPARE phase_one_case_index;

SET @has_policy_source_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'policies'
      AND index_name = 'idx_policies_source_id'
);
SET @sql := IF(
    @has_policy_source_index = 0,
    'ALTER TABLE policies ADD INDEX idx_policies_source_id (source_id)',
    'SELECT 1'
);
PREPARE phase_one_policy_index FROM @sql;
EXECUTE phase_one_policy_index;
DEALLOCATE PREPARE phase_one_policy_index;

SET @has_case_fk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'case_items'
      AND constraint_name = 'fk_case_items_source'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql := IF(
    @has_case_fk = 0,
    'ALTER TABLE case_items ADD CONSTRAINT fk_case_items_source FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE RESTRICT ON UPDATE RESTRICT',
    'SELECT 1'
);
PREPARE phase_one_case_fk FROM @sql;
EXECUTE phase_one_case_fk;
DEALLOCATE PREPARE phase_one_case_fk;

SET @has_policy_fk := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'policies'
      AND constraint_name = 'fk_policies_source'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql := IF(
    @has_policy_fk = 0,
    'ALTER TABLE policies ADD CONSTRAINT fk_policies_source FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE RESTRICT ON UPDATE RESTRICT',
    'SELECT 1'
);
PREPARE phase_one_policy_fk FROM @sql;
EXECUTE phase_one_policy_fk;
DEALLOCATE PREPARE phase_one_policy_fk;

SELECT @case_orphans AS case_orphans, @policy_orphans AS policy_orphans;
SELECT COUNT(*) AS source_foreign_keys
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN ('fk_case_items_source', 'fk_policies_source');
