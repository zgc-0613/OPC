import os
import subprocess
import time
import unittest
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKSPACE_MIGRATION = ROOT / "deploy" / "sql" / "20260725_assistant_workspace.sql"
STABILIZATION_MIGRATION = (
    ROOT / "deploy" / "sql" / "20260725_assistant_workspace_stabilization.sql"
)
PRECHECK = ROOT / "deploy" / "sql" / "20260725_assistant_workspace_precheck.sql"
POSTCHECK = ROOT / "deploy" / "sql" / "20260725_assistant_workspace_postcheck.sql"
HISTORY_REVISION_PRECHECK = (
    ROOT / "deploy" / "sql" / "20260726_assistant_history_revision_precheck.sql"
)
HISTORY_REVISION_MIGRATION = (
    ROOT / "deploy" / "sql" / "20260726_assistant_history_revision.sql"
)
HISTORY_REVISION_POSTCHECK = (
    ROOT / "deploy" / "sql" / "20260726_assistant_history_revision_postcheck.sql"
)
RUN_MYSQL_TESTS = os.environ.get("OPC_RUN_MYSQL_TESTS") == "1"
EXPECTED_POSTCHECK_FIELDS = [
    "6", "5", "3", "2", "1", "9", "2", "0", "1", "0", "0", "", "", "0"
]


@unittest.skipUnless(RUN_MYSQL_TESTS, "set OPC_RUN_MYSQL_TESTS=1 to run MySQL 8.4 tests")
class AssistantWorkspaceMySqlTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container_name = f"opc-assistant-workspace-mysql-{uuid.uuid4().hex[:12]}"
        cls._docker(
            "run",
            "--detach",
            "--rm",
            "--name",
            cls.container_name,
            "-e",
            "MYSQL_ROOT_PASSWORD=opc_test",
            "-e",
            "MYSQL_DATABASE=opc_workspace_test",
            "mysql:8.4",
            "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_unicode_ci",
        )
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            ping = subprocess.run(
                [
                    "docker",
                    "exec",
                    cls.container_name,
                    "mysqladmin",
                    "ping",
                    "-h",
                    "127.0.0.1",
                    "-uroot",
                    "-popc_test",
                    "--silent",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            if ping.returncode == 0:
                return
            time.sleep(1)
        logs = cls._docker("logs", cls.container_name, check=False)
        raise RuntimeError(f"MySQL 8.4 did not become ready:\n{logs.stderr}\n{logs.stdout}")

    @classmethod
    def tearDownClass(cls):
        if not cls.container_name.startswith("opc-assistant-workspace-mysql-"):
            raise RuntimeError("Refusing to remove an unexpected container")
        cls._docker("rm", "--force", cls.container_name, check=False)

    @classmethod
    def _docker(cls, *arguments, check=True):
        return subprocess.run(
            ["docker", *arguments],
            capture_output=True,
            text=True,
            check=check,
        )

    def setUp(self):
        self.mysql(
            "DROP DATABASE IF EXISTS opc_workspace_test;"
            "CREATE DATABASE opc_workspace_test "
            "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
        )
        self.mysql(
            """
            CREATE TABLE platform_users (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(100) NOT NULL
            ) ENGINE=InnoDB;
            CREATE TABLE app_settings (
                setting_key VARCHAR(191) PRIMARY KEY,
                setting_value TEXT NULL,
                `sensitive` TINYINT(1) NOT NULL DEFAULT 0
            ) ENGINE=InnoDB;
            CREATE TABLE ai_agent_sessions (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                user_id BIGINT NOT NULL,
                title VARCHAR(120) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'active',
                profile_json JSON NULL,
                version BIGINT NOT NULL DEFAULT 0,
                created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                    ON UPDATE CURRENT_TIMESTAMP(6),
                last_message_at DATETIME(6) NULL,
                CONSTRAINT fk_agent_sessions_user FOREIGN KEY (user_id)
                    REFERENCES platform_users(id),
                INDEX idx_agent_sessions_user_activity
                    (user_id,status,last_message_at,id)
            ) ENGINE=InnoDB;
            CREATE TABLE ai_agent_messages (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                session_id BIGINT NOT NULL,
                role VARCHAR(20) NOT NULL,
                content TEXT NOT NULL,
                status VARCHAR(20) NOT NULL,
                sequence_no INT NOT NULL,
                created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                CONSTRAINT fk_agent_messages_session FOREIGN KEY (session_id)
                    REFERENCES ai_agent_sessions(id) ON DELETE CASCADE,
                UNIQUE KEY uk_agent_message_sequence (session_id,sequence_no),
                INDEX idx_agent_messages_session_created (session_id,created_at,id)
            ) ENGINE=InnoDB;
            CREATE TABLE ai_analysis_runs (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                user_id BIGINT NOT NULL,
                session_id BIGINT NULL,
                task_type VARCHAR(40) NOT NULL,
                idempotency_key VARCHAR(64) NULL
            ) ENGINE=InnoDB;
            """
        )

    def mysql(self, sql, *, batch=False):
        command = [
            "docker",
            "exec",
            "--interactive",
            self.container_name,
            "mysql",
            "--default-character-set=utf8mb4",
            "-uroot",
            "-popc_test",
        ]
        if batch:
            command.extend(("--batch", "--skip-column-names"))
        command.append("opc_workspace_test")
        result = subprocess.run(
            command,
            input=sql,
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            self.fail(f"MySQL command failed:\n{result.stderr}\nSQL:\n{sql}")
        return result.stdout

    def run_script(self, path):
        return self.mysql(path.read_text(encoding="utf-8"))

    def postcheck_fields(self):
        output = self.mysql(POSTCHECK.read_text(encoding="utf-8"), batch=True)
        return output.rstrip("\r\n").split("\t")

    def test_first_run_applies_schema_backfill_indexes_and_boundary(self):
        self.mysql(
            """
            INSERT INTO platform_users (id,username) VALUES (1,'owner');
            INSERT INTO app_settings (setting_key,setting_value,`sensitive`)
            VALUES ('migration.assistant_workspace_rollout_at',
                    '2026-07-25 12:00:00.000000',0);
            INSERT INTO ai_agent_sessions
                (id,user_id,title,status,created_at,updated_at)
            VALUES (10,1,'Historic title','active',
                    '2026-07-25 11:00:00.000000','2026-07-25 11:00:00.000000');
            """
        )

        self.run_script(WORKSPACE_MIGRATION)
        self.mysql(
            """
            INSERT INTO ai_agent_sessions
                (id,user_id,title,status,created_at,updated_at)
            VALUES (11,1,'New title','active',
                    '2026-07-25 13:00:00.000000','2026-07-25 13:00:00.000000');
            """
        )
        self.run_script(STABILIZATION_MIGRATION)

        self.assertEqual(
            EXPECTED_POSTCHECK_FIELDS,
            self.postcheck_fields(),
        )
        self.assertEqual(
            "10\tmanual\n11\tauto\n",
            self.mysql(
                "SELECT id,title_mode FROM ai_agent_sessions ORDER BY id;",
                batch=True,
            ).replace("\r\n", "\n"),
        )

    def test_history_revision_migration_is_prechecked_repeatable_and_postchecked(self):
        self.mysql("INSERT INTO platform_users (id,username) VALUES (1,'owner');")
        precheck = self.mysql(
            HISTORY_REVISION_PRECHECK.read_text(encoding="utf-8"), batch=True
        ).strip().split("\t")
        self.assertEqual(["1", "0"], precheck)

        self.run_script(HISTORY_REVISION_MIGRATION)
        self.run_script(HISTORY_REVISION_MIGRATION)

        postcheck = self.mysql(
            HISTORY_REVISION_POSTCHECK.read_text(encoding="utf-8"), batch=True
        ).strip().split("\t")
        self.assertEqual(["1", "1", "0"], postcheck)
        self.assertEqual(
            "0",
            self.mysql(
                "SELECT assistant_history_revision FROM platform_users WHERE id=1;",
                batch=True,
            ).strip(),
        )
    def test_repeated_run_preserves_boundary_and_post_rollout_auto_title(self):
        self.mysql(
            """
            INSERT INTO platform_users (id,username) VALUES (1,'owner');
            INSERT INTO app_settings (setting_key,setting_value,`sensitive`)
            VALUES ('migration.assistant_workspace_rollout_at',
                    '2026-07-25 12:00:00.000000',0);
            INSERT INTO ai_agent_sessions
                (id,user_id,title,status,created_at,updated_at)
            VALUES (10,1,'Historic title','active',
                    '2026-07-25 11:00:00.000000','2026-07-25 11:00:00.000000');
            """
        )
        self.run_script(WORKSPACE_MIGRATION)
        self.run_script(STABILIZATION_MIGRATION)
        self.mysql(
            """
            INSERT INTO ai_agent_sessions
                (id,user_id,title,status,created_at,updated_at)
            VALUES (11,1,'New title','active',
                    '2026-07-25 13:00:00.000000','2026-07-25 13:00:00.000000');
            """
        )

        self.run_script(WORKSPACE_MIGRATION)
        self.run_script(STABILIZATION_MIGRATION)

        self.assertEqual(
            EXPECTED_POSTCHECK_FIELDS,
            self.postcheck_fields(),
        )
        self.assertEqual(
            "2026-07-25 12:00:00.000000\tmanual\tauto\n",
            self.mysql(
                """
                SELECT
                    (SELECT setting_value FROM app_settings
                     WHERE setting_key='migration.assistant_workspace_rollout_at'),
                    (SELECT title_mode FROM ai_agent_sessions WHERE id=10),
                    (SELECT title_mode FROM ai_agent_sessions WHERE id=11);
                """,
                batch=True,
            ).replace("\r\n", "\n"),
        )

    def test_interrupted_title_backfill_repairs_only_pre_rollout_sessions(self):
        self.mysql(
            """
            INSERT INTO platform_users (id,username) VALUES (1,'owner');
            INSERT INTO app_settings (setting_key,setting_value,`sensitive`)
            VALUES ('migration.assistant_workspace_rollout_at',
                    '2026-07-25 12:00:00.000000',0);
            INSERT INTO ai_agent_sessions
                (id,user_id,title,status,created_at,updated_at)
            VALUES
                (10,1,'Historic title','active',
                 '2026-07-25 11:00:00.000000','2026-07-25 11:00:00.000000'),
                (11,1,'New title','active',
                 '2026-07-25 13:00:00.000000','2026-07-25 13:00:00.000000');
            ALTER TABLE ai_agent_sessions
                ADD COLUMN title_mode VARCHAR(10) NOT NULL DEFAULT 'auto' AFTER title;
            """
        )

        self.run_script(WORKSPACE_MIGRATION)
        self.assertEqual(
            "10\tauto\n11\tauto\n",
            self.mysql(
                "SELECT id,title_mode FROM ai_agent_sessions ORDER BY id;",
                batch=True,
            ).replace("\r\n", "\n"),
        )
        self.run_script(STABILIZATION_MIGRATION)

        self.assertEqual(
            "10\tmanual\n11\tauto\n",
            self.mysql(
                "SELECT id,title_mode FROM ai_agent_sessions ORDER BY id;",
                batch=True,
            ).replace("\r\n", "\n"),
        )
        self.assertEqual("0", self.postcheck_fields()[-1])

    def test_partial_schema_run_adds_every_missing_sibling_column(self):
        self.mysql(
            """
            INSERT INTO platform_users (id,username) VALUES (1,'owner');
            INSERT INTO app_settings (setting_key,setting_value,`sensitive`)
            VALUES ('migration.assistant_workspace_rollout_at',
                    '2026-07-25 12:00:00.000000',0);
            ALTER TABLE ai_agent_sessions
                ADD COLUMN title_mode VARCHAR(10) NOT NULL DEFAULT 'auto' AFTER title;
            ALTER TABLE ai_agent_sessions
                ADD COLUMN pinned_at DATETIME(6) NULL AFTER last_message_at;
            ALTER TABLE ai_analysis_runs
                ADD COLUMN submission_kind VARCHAR(20) NOT NULL DEFAULT 'message'
                AFTER task_type;
            """
        )

        self.run_script(WORKSPACE_MIGRATION)
        self.run_script(STABILIZATION_MIGRATION)

        self.assertEqual(
            EXPECTED_POSTCHECK_FIELDS,
            self.postcheck_fields(),
        )

    def test_same_name_wrong_order_indexes_are_repaired_with_foreign_key_intact(self):
        self.mysql(
            """
            INSERT INTO platform_users (id,username) VALUES (1,'owner');
            INSERT INTO app_settings (setting_key,setting_value,`sensitive`)
            VALUES ('migration.assistant_workspace_rollout_at',
                    '2026-07-25 12:00:00.000000',0);
            """
        )
        self.run_script(WORKSPACE_MIGRATION)
        self.mysql(
            """
            ALTER TABLE ai_agent_sessions
                DROP INDEX idx_agent_sessions_history_active,
                ADD INDEX idx_agent_sessions_history_active
                    (deleted_at,user_id,pinned_at,last_message_at,id);
            ALTER TABLE ai_agent_sessions
                DROP INDEX idx_agent_sessions_history_archived,
                ADD UNIQUE INDEX idx_agent_sessions_history_archived
                    (user_id,archived_at,last_message_at,id);
            ALTER TABLE ai_agent_sessions
                DROP INDEX idx_agent_sessions_purge_due,
                ADD INDEX idx_agent_sessions_purge_due
                    (purged_at,purge_after,id);

            ALTER TABLE ai_agent_messages
                ADD INDEX idx_agent_messages_fk_repair (session_id);
            ALTER TABLE ai_agent_messages
                DROP INDEX idx_agent_messages_session_created;
            ALTER TABLE ai_agent_messages
                DROP INDEX uk_agent_message_sequence;
            ALTER TABLE ai_agent_messages
                ADD UNIQUE INDEX uk_agent_message_sequence
                    (session_id,sequence_no,id);
            ALTER TABLE ai_agent_messages
                ADD INDEX idx_agent_messages_session_created
                    (created_at,session_id,id);
            ALTER TABLE ai_agent_messages
                DROP INDEX idx_agent_messages_fk_repair;
            """
        )

        self.run_script(STABILIZATION_MIGRATION)

        self.assertEqual(
            EXPECTED_POSTCHECK_FIELDS,
            self.postcheck_fields(),
        )
        self.assertEqual(
            "fk_agent_messages_session\n",
            self.mysql(
                """
                SELECT constraint_name
                FROM information_schema.referential_constraints
                WHERE constraint_schema=DATABASE()
                  AND constraint_name='fk_agent_messages_session';
                """,
                batch=True,
            ).replace("\r\n", "\n"),
        )

    def test_purge_audit_table_is_repeatable_and_accepts_independent_failure_audit(self):
        self.assertEqual(
            "3\t5\t3\t0\t0\n",
            self.mysql(PRECHECK.read_text(encoding="utf-8"), batch=True).replace(
                "\r\n", "\n"
            ),
        )
        self.run_script(WORKSPACE_MIGRATION)
        self.run_script(STABILIZATION_MIGRATION)
        self.mysql(
            """
            INSERT INTO ai_agent_content_purge_audits
                (operation,session_id,user_id,operator_type,operator_id,result,diagnostic_code)
            VALUES ('manual_purge',999,888,'user',888,'rejected','SESSION_NOT_TRASHED');
            """
        )

        self.run_script(STABILIZATION_MIGRATION)

        self.assertEqual(EXPECTED_POSTCHECK_FIELDS, self.postcheck_fields())
        self.assertEqual(
            "3\t5\t3\t6\t1\n",
            self.mysql(PRECHECK.read_text(encoding="utf-8"), batch=True).replace(
                "\r\n", "\n"
            ),
        )
        self.assertEqual(
            "1\t0\n",
            self.mysql(
                """
                SELECT
                    (SELECT COUNT(*) FROM ai_agent_content_purge_audits
                     WHERE session_id=999 AND result='rejected'),
                    (SELECT COUNT(*) FROM information_schema.key_column_usage
                     WHERE table_schema=DATABASE()
                       AND table_name='ai_agent_content_purge_audits'
                       AND referenced_table_name IS NOT NULL);
                """,
                batch=True,
            ).replace("\r\n", "\n"),
        )


if __name__ == "__main__":
    unittest.main()
