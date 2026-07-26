import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / "deploy" / "sql" / "20260724_ai_stabilization.sql"
PRECHECK = ROOT / "deploy" / "sql" / "20260724_ai_stabilization_precheck.sql"
POSTCHECK = ROOT / "deploy" / "sql" / "20260724_ai_stabilization_postcheck.sql"
DEPLOY_SCRIPT = ROOT / ".codex_deploy_opc.py"
EVIDENCE_WORKBENCH = ROOT / "deploy" / "sql" / "20260725_evidence_workbench.sql"
PHASE_ONE_FINALIZATION = ROOT / "deploy" / "sql" / "20260725_phase_one_finalization.sql"
AI_RESPONSE_DIAGNOSTICS = ROOT / "deploy" / "sql" / "20260725_ai_response_diagnostics.sql"
ASSISTANT_WORKSPACE_STABILIZATION = (
    ROOT / "deploy" / "sql" / "20260725_assistant_workspace_stabilization.sql"
)
ASSISTANT_WORKSPACE_PRECHECK = (
    ROOT / "deploy" / "sql" / "20260725_assistant_workspace_precheck.sql"
)
ASSISTANT_WORKSPACE_POSTCHECK = (
    ROOT / "deploy" / "sql" / "20260725_assistant_workspace_postcheck.sql"
)
SCHEMA = ROOT / "opc-backend" / "src" / "main" / "resources" / "db" / "schema.sql"


class AiStabilizationMigrationTest(unittest.TestCase):
    def test_each_additive_column_and_index_has_an_independent_guard(self):
        sql = MIGRATION.read_text(encoding="utf-8")
        columns = (
            "is_industry",
            "api_key_provider",
            "api_key_origin",
            "reserved_tokens",
            "started_at",
            "deadline_at",
            "heartbeat_at",
            "case_id",
            "tag_id",
            "normalized_alias",
            "policy_usage_count",
            "case_usage_count",
            "review_status",
            "review_reason",
            "item_type",
            "item_id",
            "previous_status",
            "new_status",
            "admin_username",
        )
        for column in columns:
            self.assertRegex(sql, rf"column_name\s*=\s*'{column}'")
        indexes = (
            "idx_tags_is_industry",
            "idx_ai_runs_running_deadline",
            "uk_case_tags_case_tag",
            "idx_case_tags_case_id",
            "idx_case_tags_tag_id",
            "uk_tag_aliases_normalized",
            "idx_tag_aliases_tag_id",
            "uk_industry_review_tag",
            "idx_industry_review_status",
            "idx_evidence_reviews_item",
            "idx_evidence_reviews_created_at",
        )
        for index in indexes:
            self.assertRegex(sql, rf"index_name\s*=\s*'{index}'")

        guarded_alters = re.findall(r"'ALTER TABLE[^']+'", sql, flags=re.IGNORECASE)
        for statement in guarded_alters:
            self.assertLessEqual(
                statement.upper().count("ADD COLUMN"),
                1,
                "A half-run migration must not skip sibling columns",
            )

    def test_source_status_and_policy_industry_backfills_are_safe_and_repeatable(self):
        sql = MIGRATION.read_text(encoding="utf-8")
        self.assertIn("UPDATE sources SET status = 'published' WHERE status = 'active'", sql)
        self.assertIn("industry_tag_review_candidates", sql)
        self.assertIn("policy_tags", sql)
        self.assertIn("INSERT IGNORE", sql)
        self.assertIn("JOIN tag_aliases AS alias", sql)
        self.assertIn("alias.normalized_alias", sql)

    def test_postcheck_covers_repairable_tables_constraints_and_alias_backfill(self):
        sql = POSTCHECK.read_text(encoding="utf-8")
        for table in (
            "case_tags",
            "tag_aliases",
            "industry_tag_review_candidates",
            "ai_evidence_reviews",
        ):
            self.assertIn(table, sql)
        for index in (
            "uk_case_tags_case_tag",
            "uk_tag_aliases_normalized",
            "uk_industry_review_tag",
            "idx_evidence_reviews_item",
        ):
            self.assertIn(index, sql)
        self.assertIn("constraint_type = 'PRIMARY KEY'", sql)
        self.assertIn("relation.id IS NULL", sql)

    def test_precheck_postcheck_and_deployment_wiring_exist(self):
        self.assertTrue(PRECHECK.exists())
        self.assertTrue(POSTCHECK.exists())
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        self.assertIn("AI_STABILIZATION_PRECHECK", deploy)
        self.assertIn("AI_STABILIZATION_POSTCHECK", deploy)

    def test_evidence_workbench_audit_migration_is_independently_guarded_and_wired(self):
        sql = EVIDENCE_WORKBENCH.read_text(encoding="utf-8")
        for table in ("case_items", "policies", "sources"):
            self.assertRegex(sql, rf"table_name\s*=\s*'{table}'\s+AND column_name\s*=\s*'evidence_revision'")
        self.assertEqual(sql.count("MODIFY COLUMN evidence_revision BIGINT NOT NULL DEFAULT 0"), 3)
        for column in ("action_type", "reason", "operation_id"):
            self.assertRegex(sql, rf"column_name\s*=\s*'{column}'")
        self.assertRegex(sql, r"index_name\s*=\s*'idx_evidence_reviews_operation'")
        self.assertIn("WHERE action_type IS NULL OR action_type = ''", sql)
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        self.assertIn("EVIDENCE_WORKBENCH_MIGRATION", deploy)
        self.assertIn("evidence-workbench.sql", deploy)

    def test_phase_one_finalization_checks_orphans_and_guards_each_constraint(self):
        sql = PHASE_ONE_FINALIZATION.read_text(encoding="utf-8")
        self.assertIn("case_items contains orphaned source_id rows", sql)
        self.assertIn("policies contains orphaned source_id rows", sql)
        for index in ("idx_case_items_source_id", "idx_policies_source_id"):
            self.assertRegex(sql, rf"index_name\s*=\s*'{index}'")
        for constraint in ("fk_case_items_source", "fk_policies_source"):
            self.assertRegex(sql, rf"constraint_name\s*=\s*'{constraint}'")
        self.assertEqual(6, len(re.findall(r"(?m)^PREPARE phase_one_", sql)))
        self.assertIn("ON DELETE RESTRICT", sql)
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        self.assertIn("PHASE_ONE_FINALIZATION_MIGRATION", deploy)
        self.assertIn("phase-one-finalization.sql", deploy)

    def test_ai_response_diagnostics_are_independently_guarded_and_deployed(self):
        sql = AI_RESPONSE_DIAGNOSTICS.read_text(encoding="utf-8")
        for column in ("finish_reason", "response_hash", "diagnostic_code"):
            self.assertRegex(
                sql,
                rf"table_name\s*=\s*'ai_analysis_runs'\s+AND column_name\s*=\s*'{column}'",
            )
            self.assertEqual(1, len(re.findall(rf"ADD COLUMN {column}\b", sql)))
        self.assertNotRegex(sql, r"(?im)^\s*(DROP|TRUNCATE|DELETE)\b")
        self.assertIn("result_json = NULL", sql)
        self.assertIn("SHA2(CAST(result_json AS CHAR), 256)", sql)
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        self.assertIn("AI_RESPONSE_DIAGNOSTICS_MIGRATION", deploy)
        self.assertIn("ai-response-diagnostics.sql", deploy)

    def test_assistant_workspace_stabilization_recovers_interrupted_title_backfill(self):
        sql = ASSISTANT_WORKSPACE_STABILIZATION.read_text(encoding="utf-8")

        self.assertIn("@assistant_workspace_backfill_cutoff", sql)
        self.assertRegex(
            sql,
            r"UPDATE\s+ai_agent_sessions\s+SET\s+title_mode\s*=\s*'manual'\s+"
            r"WHERE\s+title_mode\s*=\s*'auto'\s+AND\s+created_at\s*<\s*"
            r"@assistant_workspace_backfill_cutoff",
        )

    def test_assistant_workspace_stabilization_repairs_exact_index_definitions(self):
        sql = ASSISTANT_WORKSPACE_STABILIZATION.read_text(encoding="utf-8")

        expected = {
            "idx_agent_sessions_history_active": "user_id,deleted_at,pinned_at,last_message_at,id",
            "idx_agent_sessions_history_archived": "user_id,archived_at,last_message_at,id",
            "idx_agent_sessions_purge_due": "purge_after,purged_at,id",
            "uk_agent_message_sequence": "session_id,sequence_no",
            "idx_agent_messages_session_created": "session_id,created_at,id",
        }
        for index_name, columns in expected.items():
            self.assertIn(index_name, sql)
            self.assertIn(columns, sql)
        self.assertIn("non_unique", sql)
        self.assertIn("seq_in_index", sql)
        self.assertIn("ADD UNIQUE INDEX uk_agent_message_sequence", sql)
        self.assertLess(
            sql.index("ADD INDEX idx_agent_messages_session_created"),
            sql.index("DROP INDEX uk_agent_message_sequence"),
        )

    def test_assistant_workspace_stabilization_adds_start_and_purge_barrier_columns(self):
        sql = ASSISTANT_WORKSPACE_STABILIZATION.read_text(encoding="utf-8")
        schema = SCHEMA.read_text(encoding="utf-8")
        expected = {
            "ai_agent_sessions": {
                "content_generation": "BIGINT NOT NULL DEFAULT 0",
            },
            "ai_analysis_runs": {
                "submission_kind": "VARCHAR(20) NOT NULL DEFAULT 'message'",
                "request_content_hash": "CHAR(64) NULL",
                "start_profile_hash": "CHAR(64) NULL",
                "session_content_generation": "BIGINT NOT NULL DEFAULT 0",
            },
        }

        for table_name, columns in expected.items():
            for column_name, definition in columns.items():
                self.assertRegex(
                    sql,
                    rf"table_name\s*=\s*'{table_name}'\s+AND\s+column_name\s*=\s*'{column_name}'",
                )
                dynamic_definition = definition.replace("'", "''")
                self.assertIn(f"ADD COLUMN {column_name} {dynamic_definition}", sql)
                self.assertIn(f"{column_name} {definition}", schema)

    def test_assistant_workspace_postcheck_reports_exact_index_and_backfill_failures(self):
        sql = ASSISTANT_WORKSPACE_POSTCHECK.read_text(encoding="utf-8")

        for token in (
            "seq_in_index",
            "non_unique",
            "invalid_index_definitions",
            "historic_auto_titles",
            "stability_columns",
            "rollout_boundary_settings",
            "assistant_workspace_backfill_cutoff",
        ):
            self.assertIn(token, sql)

    def test_assistant_workspace_purge_audit_table_is_independent_and_idempotent(self):
        expected_fragments = (
            "id BIGINT PRIMARY KEY AUTO_INCREMENT",
            "operation VARCHAR(40) NOT NULL",
            "session_id BIGINT NOT NULL",
            "user_id BIGINT NULL",
            "operator_type VARCHAR(20) NOT NULL",
            "operator_id BIGINT NULL",
            "result VARCHAR(20) NOT NULL",
            "diagnostic_code VARCHAR(80) NULL",
            "created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
            "idx_agent_purge_audits_session_created (session_id,created_at)",
            "idx_agent_purge_audits_user_created (user_id,created_at)",
        )

        for source_path in (ASSISTANT_WORKSPACE_STABILIZATION, SCHEMA):
            source = source_path.read_text(encoding="utf-8")
            start = source.index("CREATE TABLE IF NOT EXISTS ai_agent_content_purge_audits")
            table = source[start : source.index(";", start) + 1]
            for fragment in expected_fragments:
                self.assertIn(fragment, table)
            self.assertNotIn("FOREIGN KEY", table)

    def test_assistant_workspace_postcheck_requires_purge_audit_schema(self):
        sql = ASSISTANT_WORKSPACE_POSTCHECK.read_text(encoding="utf-8")

        for token in (
            "purge_audit_tables",
            "purge_audit_columns",
            "purge_audit_indexes",
            "purge_audit_foreign_keys",
            "idx_agent_purge_audits_session_created",
            "idx_agent_purge_audits_user_created",
        ):
            self.assertIn(token, sql)

    def test_assistant_workspace_precheck_reports_existing_purge_audit_table(self):
        sql = ASSISTANT_WORKSPACE_PRECHECK.read_text(encoding="utf-8")

        self.assertIn("ai_agent_content_purge_audits", sql)
        self.assertIn("existing_purge_audit_tables", sql)


if __name__ == "__main__":
    unittest.main()
