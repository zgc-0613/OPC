package com.opc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.CaseAnalysisRequestDTO;
import com.opc.platform.ai.dto.AgentMessageCreateDTO;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.dto.AgentSessionStartDTO;
import com.opc.platform.ai.dto.AgentSessionUpdateDTO;
import com.opc.platform.ai.dto.EvidenceReviewBatchItemDTO;
import com.opc.platform.ai.dto.EvidenceReviewBatchUpdateDTO;
import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import com.opc.platform.ai.dto.EvidenceReviewUpdateDTO;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.entity.AiAgentProviderCall;
import com.opc.platform.ai.exception.AgentHistoryCursorStaleException;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.mapper.EvidenceReviewQueueMapper;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.service.AiTaskExecutionService;
import com.opc.platform.ai.service.AgentSessionService;
import com.opc.platform.ai.service.AgentSessionHistoryService;
import com.opc.platform.ai.service.AgentResearchService;
import com.opc.platform.ai.service.AgentResearchQueryService;
import com.opc.platform.ai.service.AgentOrchestrator;
import com.opc.platform.ai.service.AgentResearchWorker;
import com.opc.platform.ai.service.AgentRunQueueService;
import com.opc.platform.ai.service.AgentClarificationPolicy;
import com.opc.platform.ai.service.AgentRunFinalizer;
import com.opc.platform.ai.service.AgentRunLifecycleService;
import com.opc.platform.ai.service.AgentProviderSettlementService;
import com.opc.platform.ai.service.AgentRunEvidenceService;
import com.opc.platform.ai.service.AnalyticsOverviewService;
import com.opc.platform.ai.service.AnalyticsSnapshotMaterial;
import com.opc.platform.ai.service.AnalyticsResearchStartService;
import com.opc.platform.ai.mapper.AiAgentProviderCallMapper;
import com.opc.platform.ai.service.CaseAnalysisService;
import com.opc.platform.ai.service.EvidenceReviewService;
import com.opc.platform.ai.service.EntrepreneurshipEvidenceService;
import com.opc.platform.ai.tool.AgentToolContext;
import com.opc.platform.ai.tool.AgentToolException;
import com.opc.platform.ai.tool.AgentToolRegistry;
import com.opc.platform.caseitem.dto.CaseItemCreateDTO;
import com.opc.platform.caseitem.dto.CaseItemQueryDTO;
import com.opc.platform.caseitem.dto.CaseItemUpdateDTO;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.caseitem.service.CaseItemService;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.dto.PolicyCreateDTO;
import com.opc.platform.policy.dto.PolicyApplicabilityBatchDTO;
import com.opc.platform.policy.dto.PolicyApplicabilityBatchItemDTO;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policy.service.PolicyService;
import com.opc.platform.source.dto.SourceUpdateDTO;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.source.service.SourceService;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayDeque;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "opc.ai.agent.worker-enabled=false",
        "opc.ai.agent.history-purge-enabled=false",
        "opc.ai.agent.history-cursor-secret=phase-one-history-cursor-secret-1234567890"
})
@Testcontainers(disabledWithoutDocker = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhaseOneMySqlIntegrationTest {

    private static final LocalDateTime SNAPSHOT_TIME = LocalDateTime.of(2026, 7, 25, 3, 0);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("opc_phase_one_test")
            .withUsername("opc_test")
            .withPassword("opc_test")
            .withCommand("--transaction-isolation=READ-COMMITTED", "--innodb-lock-wait-timeout=5");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private javax.sql.DataSource dataSource;
    @Autowired private TransactionTemplate transactions;
    @Autowired private CaseItemService caseItemService;
    @Autowired private PolicyService policyService;
    @Autowired private SourceService sourceService;
    @Autowired private EvidenceReviewService evidenceReviewService;
    @Autowired private EntrepreneurshipEvidenceService entrepreneurshipEvidenceService;
    @Autowired private EvidenceReviewQueueMapper queueMapper;
    @Autowired private AiAnalysisRunMapper runMapper;
    @Autowired private CaseItemMapper caseItemMapper;
    @Autowired private PolicyMapper policyMapper;
    @Autowired private SourceMapper sourceMapper;
    @Autowired private IndustryTagService industryTagService;
    @Autowired private AgentSessionService agentSessionService;
    @Autowired private AgentSessionHistoryService agentSessionHistoryService;
    @Autowired private AgentToolRegistry agentToolRegistry;
    @Autowired private AgentResearchService agentResearchService;
    @Autowired private AgentResearchQueryService agentResearchQueryService;
    @Autowired private AgentOrchestrator agentOrchestrator;
    @Autowired private AiAgentToolCallMapper agentToolCallMapper;
    @Autowired private AiAgentProviderCallMapper agentProviderCallMapper;
    @Autowired private AgentRunQueueService agentRunQueueService;
    @Autowired private AgentClarificationPolicy agentClarificationPolicy;
    @Autowired private AgentRunLifecycleService agentRunLifecycleService;
    @Autowired private AgentProviderSettlementService agentProviderSettlementService;
    @Autowired private AgentRunEvidenceService agentRunEvidenceService;
    @Autowired private AnalyticsOverviewService analyticsOverviewService;
    @Autowired private AnalyticsResearchStartService analyticsResearchStartService;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            for (String table : List.of("ai_analytics_snapshots", "ai_agent_content_purge_audits", "ai_agent_provider_calls", "ai_agent_tool_calls", "ai_agent_messages", "ai_agent_sessions", "platform_users",
                    "policy_industry_tags", "case_tags", "policy_tags", "tag_aliases",
                    "ai_evidence_reviews", "ai_analysis_runs", "ai_model_settings",
                    "case_items", "policies", "tags", "regions", "sources")) {
                jdbc.execute("DROP TABLE IF EXISTS " + table);
            }
        } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        }
        createBaseSchema();
    }

    @Test
    void agentRuntimeMigrationIsRepeatableAndEnforcesCoreRelations() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);

        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        runAgentRuntimeMigration();

        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE()
                  AND table_name IN ('ai_agent_sessions','ai_agent_messages','ai_agent_tool_calls')
                """, Integer.class));
        assertEquals(10, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
                  AND column_name IN ('session_id','user_message_id','idempotency_key','step_count',
                    'tool_call_count','current_stage','visible_progress','cancelled_at','completed_at','session_active_guard')
                """, Integer.class));

        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (1,'owner','active'),(2,'other','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES (10,1,'Research','active')");
        jdbc.update("INSERT INTO ai_agent_messages (session_id,role,content,status,sequence_no) VALUES (10,'user','Question','completed',1)");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO ai_agent_messages (session_id,role,content,status,sequence_no) VALUES (10,'assistant','Answer','completed',1)"));

        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,status,provider,model_id,prompt_version,evidence_hash,session_id,idempotency_key)
                VALUES (31,1,'agent_research','running','fake','fake','agent-v1',REPEAT('a',64),10,'idem-1')
                """);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,status,provider,model_id,prompt_version,evidence_hash,session_id,idempotency_key)
                VALUES (32,2,'agent_research','running','fake','fake','agent-v1',REPEAT('b',64),10,'idem-2')
                """));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO ai_agent_tool_calls
                    (analysis_run_id,step_no,tool_name,arguments_json,status)
                VALUES (999,1,'search_cases','{}','pending')
                """));
    }

    @Test
    void assistantWorkspaceMigrationIsRepeatableAndBackfillsArchivedSessions() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (1,'owner','active')");
        jdbc.update("""
                INSERT INTO ai_agent_sessions (id,user_id,title,status,created_at)
                VALUES (10,1,'Archived research','archived','2026-07-25 12:00:00')
                """);

        jdbc.execute("ALTER TABLE ai_agent_sessions ADD COLUMN title_mode VARCHAR(20) NOT NULL DEFAULT 'auto' AFTER title");
        runAssistantWorkspaceMigration();
        runAssistantWorkspaceStabilizationMigration();
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status,created_at) VALUES (11,1,'New automatic research','active','2026-07-25 22:00:00')");
        jdbc.execute("ALTER TABLE ai_agent_sessions DROP INDEX idx_agent_sessions_history_active");
        jdbc.execute("ALTER TABLE ai_agent_sessions ADD INDEX idx_agent_sessions_history_active (id,last_message_at,pinned_at,deleted_at,user_id)");
        runAssistantWorkspaceStabilizationMigration();

        Map<String, Object> result = jdbc.queryForMap(Files.readString(
                Path.of("..", "deploy", "sql", "20260725_assistant_workspace_postcheck.sql")));
        assertEquals(6, ((Number) result.get("workspace_columns")).intValue());
        assertEquals(3, ((Number) result.get("workspace_indexes")).intValue());
        assertEquals(2, ((Number) result.get("message_order_index_columns")).intValue());
        assertEquals(0, ((Number) result.get("invalid_title_modes")).intValue());
        assertEquals(0, ((Number) result.get("missing_archived_timestamps")).intValue());
        assertEquals("", result.get("missing_indexes"));
        assertEquals("manual", jdbc.queryForObject(
                "SELECT title_mode FROM ai_agent_sessions WHERE id=10", String.class));
        assertEquals("auto", jdbc.queryForObject(
                "SELECT title_mode FROM ai_agent_sessions WHERE id=11", String.class));
        assertNotNull(jdbc.queryForObject(
                "SELECT archived_at FROM ai_agent_sessions WHERE id=10", LocalDateTime.class));
    }

    @Test
    void phaseThreeTaskContextMigrationIsRepeatableAndPostchecked() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        runAssistantWorkspaceMigration();
        runAssistantWorkspaceStabilizationMigration();
        runAssistantHistoryRevisionMigration();
        runAgentMultiroundBudgetMigration();
        jdbc.execute("ALTER TABLE ai_analysis_runs DROP COLUMN task_context_hash");
        jdbc.execute("ALTER TABLE ai_analysis_runs DROP COLUMN task_context_json");
        jdbc.execute("ALTER TABLE ai_analysis_runs DROP COLUMN task_context_version");

        Map<String, Object> precheck = jdbc.queryForMap(Files.readString(
                Path.of("..", "deploy", "sql", "20260801_phase_three_task_context_precheck.sql")));
        assertEquals(2, ((Number) precheck.get("required_tables")).intValue());
        assertEquals(0, ((Number) precheck.get("existing_task_context_columns")).intValue());

        runPhaseThreeTaskContextMigration();
        runPhaseThreeTaskContextMigration();

        Map<String, Object> postcheck = jdbc.queryForMap(Files.readString(
                Path.of("..", "deploy", "sql", "20260801_phase_three_task_context_postcheck.sql")));
        assertEquals(3, ((Number) postcheck.get("session_context_columns")).intValue());
        assertEquals(0, ((Number) postcheck.get("run_context_columns")).intValue());
        assertEquals(1, ((Number) postcheck.get("task_context_indexes")).intValue());
        assertEquals(0, ((Number) postcheck.get("incomplete_session_contexts")).intValue());
    }

    @Test
    void phaseThreeAnalyticsSnapshotMigrationIsRepeatableAndPreservesOwnerIdempotency() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        runAssistantWorkspaceMigration();
        runAssistantWorkspaceStabilizationMigration();
        runAssistantHistoryRevisionMigration();
        runAgentMultiroundBudgetMigration();
        runPhaseThreeTaskContextMigration();

        Map<String, Object> precheck = jdbc.queryForMap(Files.readString(
                Path.of("..", "deploy", "sql", "20260801_phase_three_analytics_snapshots_precheck.sql")));
        assertEquals(2, ((Number) precheck.get("required_tables")).intValue());
        assertEquals(0, ((Number) precheck.get("existing_snapshot_table")).intValue());
        assertEquals(0, ((Number) precheck.get("existing_snapshot_columns")).intValue());
        assertEquals(0, ((Number) precheck.get("existing_run_snapshot_columns")).intValue());

        runPhaseThreeAnalyticsSnapshotMigration();
        runPhaseThreeAnalyticsSnapshotMigration();

        Map<String, Object> postcheck = jdbc.queryForMap(Files.readString(
                Path.of("..", "deploy", "sql", "20260801_phase_three_analytics_snapshots_postcheck.sql")));
        assertEquals(1, ((Number) postcheck.get("snapshot_table")).intValue());
        assertEquals(15, ((Number) postcheck.get("snapshot_columns")).intValue());
        assertEquals(4, ((Number) postcheck.get("snapshot_indexes")).intValue());
        assertEquals(5, ((Number) postcheck.get("run_snapshot_columns")).intValue());
        assertEquals(2, ((Number) postcheck.get("run_snapshot_indexes")).intValue());

        jdbc.update("""
                INSERT INTO ai_analytics_snapshots
                    (user_id,metric_id,normalized_filters_json,selected_bucket_ids_json,data_version,
                     snapshot_json,snapshot_hash,idempotency_key,request_hash,expires_at)
                VALUES (42,'overview.verified_cases',JSON_OBJECT(),JSON_ARRAY('overview.verified_cases'),
                        'analytics-v1:current',JSON_OBJECT(),REPEAT('a',64),'analytics-snapshot-1',
                        REPEAT('b',64),DATE_ADD(NOW(6), INTERVAL 30 MINUTE))
                """);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO ai_analytics_snapshots
                    (user_id,metric_id,normalized_filters_json,selected_bucket_ids_json,data_version,
                     snapshot_json,snapshot_hash,idempotency_key,request_hash,expires_at)
                VALUES (42,'overview.verified_cases',JSON_OBJECT(),JSON_ARRAY('overview.verified_cases'),
                        'analytics-v1:current',JSON_OBJECT(),REPEAT('c',64),'analytics-snapshot-1',
                        REPEAT('d',64),DATE_ADD(NOW(6), INTERVAL 30 MINUTE))
                """));
    }

    @Test
    void analyticsResearchStartRollsBackTheSnapshotAndRunWhenTheFinalLinkCannotPersist() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        runAssistantWorkspaceMigration();
        runAssistantWorkspaceStabilizationMigration();
        runAssistantHistoryRevisionMigration();
        runAgentMultiroundBudgetMigration();
        runPhaseThreeTaskContextMigration();
        runPhaseThreeAnalyticsSnapshotMigration();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        jdbc.update("""
                UPDATE ai_model_settings
                SET enabled=1, agent_enabled=1, agent_rollout_state='explicitly_enabled',
                    agent_rollout_changed_at=NOW(6), agent_rollout_changed_by_admin_id=1
                WHERE id=1
                """);
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        ObjectMapper objectMapper = new ObjectMapper();
        String dataVersion = analyticsOverviewService.overview(owner).dataVersion();
        var request = objectMapper.createObjectNode();
        request.put("metricId", "overview.verified_cases");
        request.set("filters", objectMapper.createObjectNode());
        request.putArray("selectedBucketIds");
        request.put("dataVersion", dataVersion);
        request.put("userQuestion", "Use the verified cases overview as a bounded research condition.");
        request.put("idempotencyKey", "analytics-atomic-1");
        jdbc.execute("ALTER TABLE ai_analytics_snapshots DROP COLUMN run_id");

        assertThrows(DataAccessException.class, () -> analyticsResearchStartService.start(owner, request));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_analytics_snapshots", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_agent_sessions", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_runs", Integer.class));
    }

    @Test
    void industryAnalyticsUsesOnlyEligibleSourceChainsAndPreservesMultiLabelBuckets() throws Exception {
        insertSource(1L, "Eligible source one", "published", "verified", 2L);
        insertSource(2L, "Eligible source two", "published", "verified", 1L);
        insertSource(3L, "Eligible source three", "published", "verified", 1L);
        insertSource(4L, "Unverified source", "published", "legacy_unverified", 0L);
        insertCase(11L, 1L, "AI service case", "verified", 2L);
        insertCase(12L, 2L, "Enterprise service case", "verified", 1L);
        insertCase(13L, 3L, "Unclassified eligible case", "verified", 1L);
        insertCase(14L, 4L, "Ineligible source case", "verified", 1L);
        jdbc.update("INSERT INTO tags (id,name,tag_type,is_industry,sort_order) VALUES (701,'人工智能服务','case',1,1),(702,'企业服务','case',1,2)");
        jdbc.update("INSERT INTO case_tags (case_id,tag_id) VALUES (11,701),(11,702),(12,702),(14,701)");

        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var response = analyticsOverviewService.industries(owner, "industry.case_count", List.of());

        assertEquals("partial", response.status());
        assertEquals(2L, response.sampleSize());
        assertEquals(1L, response.missingCount());
        assertEquals(3L, response.totalEligible());
        assertEquals("industry:702", response.buckets().get(0).bucketId());
        assertEquals(2L, response.buckets().get(0).value());
        assertEquals("industry:701", response.buckets().get(1).bucketId());
        assertEquals(1L, response.buckets().get(1).value());

        AnalyticsSnapshotMaterial material = analyticsOverviewService.rebuildSnapshot(
                "industry.case_count",
                new ObjectMapper().createObjectNode().put("industryTagId", 701L),
                List.of("industry:701"));
        JsonNode payload = new ObjectMapper().readTree(material.payloadJson());
        assertEquals("industry:701", payload.path("buckets").get(0).path("bucketId").asText());
        assertEquals(response.dataVersion(), material.dataVersion());
    }

    @Test
    void publicCaseListFiltersByTheExactIndustryTagRelation() {
        insertSource(1L, "AI source", "published", "verified", 1L);
        insertSource(2L, "Enterprise source", "published", "verified", 1L);
        insertCase(11L, 1L, "AI service case", "verified", 1L);
        insertCase(12L, 2L, "Enterprise service case", "verified", 1L);
        jdbc.update("INSERT INTO tags (id,name,tag_type,is_industry,sort_order) VALUES (701,'人工智能服务','case',1,1),(702,'企业服务','case',1,2)");
        jdbc.update("INSERT INTO case_tags (case_id,tag_id) VALUES (11,701),(12,702)");
        CaseItemQueryDTO query = new CaseItemQueryDTO();
        query.setIndustryTagId(701L);

        var result = caseItemService.listPublicCaseItems(query);

        assertEquals(List.of(11L), result.stream().map(item -> item.getId()).toList());
    }

    @Test
    void historyCursorFreezesMessageActivityAndIsBoundToTheRequest() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active'),(43,'other','other@example.com','active')");
        jdbc.update("""
                INSERT INTO ai_agent_sessions (id,user_id,title,title_mode,status,created_at,last_message_at)
                VALUES (10,42,'A','manual','active','2026-07-25 08:00:00','2026-07-25 12:00:00'),
                       (11,42,'B','manual','active','2026-07-25 08:00:00','2026-07-25 11:00:00'),
                       (12,42,'C','manual','active','2026-07-25 08:00:00','2026-07-25 10:00:00'),
                       (13,42,'D','manual','active','2026-07-25 08:00:00','2026-07-25 09:00:00')
                """);
        jdbc.update("""
                INSERT INTO ai_agent_messages (session_id,role,content,status,sequence_no,created_at)
                VALUES (10,'user','A question','completed',1,'2026-07-25 12:00:00'),
                       (11,'user','B question','completed',1,'2026-07-25 11:00:00'),
                       (12,'user','C question','completed',1,'2026-07-25 10:00:00'),
                       (13,'user','D question','completed',1,'2026-07-25 09:00:00')
                """);
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        AuthenticatedUser other = new AuthenticatedUser(43L, "other", "other@example.com");

        var first = agentSessionHistoryService.history(owner, "active", "", null, 2);
        assertEquals(List.of(10L, 11L), first.items().stream().map(item -> item.sessionId()).toList());
        jdbc.update("INSERT INTO ai_agent_messages (session_id,role,content,status,sequence_no,created_at) VALUES (12,'user','Later activity','completed',2,NOW(6))");
        jdbc.update("UPDATE ai_agent_sessions SET last_message_at=NOW(6) WHERE id=12");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,title_mode,status) VALUES (14,42,'New after snapshot','manual','active')");

        var second = agentSessionHistoryService.history(owner, "active", "", first.nextCursor(), 2);
        assertEquals(List.of(12L, 13L), second.items().stream().map(item -> item.sessionId()).toList());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> agentSessionHistoryService.history(owner, "archived", "", first.nextCursor(), 2)).getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> agentSessionHistoryService.history(owner, "active", "different", first.nextCursor(), 2)).getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> agentSessionHistoryService.history(other, "active", "", first.nextCursor(), 2)).getErrorCode());
    }

    @Test
    void assistantHistoryRevisionMigrationIsRepeatableAndBackfillsExistingUsers() throws Exception {
        createAgentUserTable();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");

        Map<String, Object> precheck = jdbc.queryForMap(Files.readString(
                Path.of("..", "deploy", "sql", "20260726_assistant_history_revision_precheck.sql")));
        assertEquals(1, ((Number) precheck.get("required_user_tables")).intValue());
        assertEquals(0, ((Number) precheck.get("existing_revision_columns")).intValue());

        runAssistantHistoryRevisionMigration();
        runAssistantHistoryRevisionMigration();

        Map<String, Object> postcheck = jdbc.queryForMap(Files.readString(
                Path.of("..", "deploy", "sql", "20260726_assistant_history_revision_postcheck.sql")));
        assertEquals(1, ((Number) postcheck.get("revision_columns")).intValue());
        assertEquals(1, ((Number) postcheck.get("valid_revision_definitions")).intValue());
        assertEquals(0, ((Number) postcheck.get("invalid_revision_values")).intValue());

        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='platform_users'
                  AND column_name='assistant_history_revision'
                  AND column_type='bigint' AND is_nullable='NO' AND column_default='0'
                """, Integer.class));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT assistant_history_revision FROM platform_users WHERE id=42", Long.class));
    }

    @Test
    void historyMetadataChangesExpireOnlyTheOwnersCursor() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES " +
                "(42,'owner','owner@example.com','active'),(43,'other','other@example.com','active')");
        jdbc.update("""
                INSERT INTO ai_agent_sessions
                    (id,user_id,title,title_mode,status,pinned_at,archived_at,deleted_at,purge_after,created_at)
                VALUES
                    (10,42,'Rename me','manual','active',NULL,NULL,NULL,NULL,'2026-07-25 12:00:00'),
                    (11,42,'Pin me','manual','active',NULL,NULL,NULL,NULL,'2026-07-25 11:00:00'),
                    (12,42,'Archive me','manual','active',NULL,NULL,NULL,NULL,'2026-07-25 10:00:00'),
                    (13,42,'Restore archive','manual','archived',NULL,NOW(6),NULL,NULL,'2026-07-25 09:00:00'),
                    (16,42,'Archived 2','manual','archived',NULL,NOW(6),NULL,NULL,'2026-07-25 08:00:00'),
                    (17,42,'Archived 3','manual','archived',NULL,NOW(6),NULL,NULL,'2026-07-25 07:00:00'),
                    (14,42,'Trash me','manual','active',NULL,NULL,NULL,NULL,'2026-07-25 06:00:00'),
                    (15,42,'Restore trash','manual','active',NULL,NULL,NOW(6),DATE_ADD(NOW(6),INTERVAL 30 DAY),'2026-07-25 05:00:00'),
                    (18,42,'Trash 2','manual','active',NULL,NULL,NOW(6),DATE_ADD(NOW(6),INTERVAL 30 DAY),'2026-07-25 04:00:00'),
                    (19,42,'Trash 3','manual','active',NULL,NULL,NOW(6),DATE_ADD(NOW(6),INTERVAL 30 DAY),'2026-07-25 03:00:00'),
                    (30,43,'Other A','manual','active',NULL,NULL,NULL,NULL,'2026-07-25 12:00:00'),
                    (31,43,'Other B','manual','active',NULL,NULL,NULL,NULL,'2026-07-25 11:00:00'),
                    (32,43,'Other C','manual','active',NULL,NULL,NULL,NULL,'2026-07-25 10:00:00')
                """);
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        AuthenticatedUser other = new AuthenticatedUser(43L, "other", "other@example.com");

        String renameCursor = agentSessionHistoryService.history(owner, "active", "", null, 2).nextCursor();
        AgentSessionUpdateDTO rename = new AgentSessionUpdateDTO();
        rename.setTitle("Renamed exactly once");
        agentSessionHistoryService.update(owner, 10L, rename);
        assertThrows(AgentHistoryCursorStaleException.class,
                () -> agentSessionHistoryService.history(owner, "active", "", renameCursor, 2));

        String pinCursor = agentSessionHistoryService.history(owner, "active", "", null, 2).nextCursor();
        AgentSessionUpdateDTO pin = new AgentSessionUpdateDTO();
        pin.setPinned(true);
        agentSessionHistoryService.update(owner, 11L, pin);
        assertThrows(AgentHistoryCursorStaleException.class,
                () -> agentSessionHistoryService.history(owner, "active", "", pinCursor, 2));

        String archiveCursor = agentSessionHistoryService.history(owner, "active", "", null, 2).nextCursor();
        agentSessionHistoryService.archive(owner, 12L);
        assertThrows(AgentHistoryCursorStaleException.class,
                () -> agentSessionHistoryService.history(owner, "active", "", archiveCursor, 2));

        String unarchiveCursor = agentSessionHistoryService.history(owner, "archived", "", null, 1).nextCursor();
        agentSessionHistoryService.unarchive(owner, 13L);
        assertThrows(AgentHistoryCursorStaleException.class,
                () -> agentSessionHistoryService.history(owner, "archived", "", unarchiveCursor, 1));

        String trashCursor = agentSessionHistoryService.history(owner, "active", "", null, 2).nextCursor();
        agentSessionHistoryService.trash(owner, 14L);
        assertThrows(AgentHistoryCursorStaleException.class,
                () -> agentSessionHistoryService.history(owner, "active", "", trashCursor, 2));

        String restoreCursor = agentSessionHistoryService.history(owner, "trash", "", null, 1).nextCursor();
        agentSessionHistoryService.restore(owner, 15L);
        assertThrows(AgentHistoryCursorStaleException.class,
                () -> agentSessionHistoryService.history(owner, "trash", "", restoreCursor, 1));

        String otherCursor = agentSessionHistoryService.history(other, "active", "", null, 1).nextCursor();
        AgentSessionUpdateDTO ownerRename = new AgentSessionUpdateDTO();
        ownerRename.setTitle("Owner changed again");
        agentSessionHistoryService.update(owner, 10L, ownerRename);
        assertEquals(List.of(31L), agentSessionHistoryService
                .history(other, "active", "", otherCursor, 1).items().stream()
                .map(item -> item.sessionId()).toList());
    }

    @Test
    void permanentPurgeRejectsStaleToolWritesAndScrubsRequestFingerprints() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        jdbc.update("""
                INSERT INTO ai_agent_sessions
                    (id,user_id,title,title_mode,status,deleted_at,purge_after,content_generation)
                VALUES (10,42,'Private','manual','active',NOW(6),DATE_ADD(NOW(6),INTERVAL 30 DAY),0)
                """);
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) VALUES (20,10,'user','Sensitive question','completed',1)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,session_id,user_message_id,idempotency_key,submission_kind,
                     request_content_hash,start_profile_hash,session_content_generation,status,provider,
                     model_id,prompt_version,evidence_hash,lease_owner)
                VALUES (30,42,'agent_research',10,20,'purge-stale-write','session_start',REPEAT('c',64),
                        REPEAT('d',64),0,'failed','fake','fake','agent-v1',REPEAT('a',64),'worker-a')
                """);
        jdbc.update("""
                INSERT INTO ai_agent_tool_calls
                    (id,analysis_run_id,step_no,tool_name,arguments_json,result_summary_json,status,
                     evidence_hash,evidence_count,latency_ms)
                VALUES (40,30,1,'search_cases',JSON_OBJECT('query','secret'),JSON_OBJECT('answer','secret'),
                        'completed',REPEAT('e',64),1,5)
                """);
        AiAgentToolCall stale = agentToolCallMapper.selectById(40L);
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

        agentSessionHistoryService.purge(owner, 10L);
        stale.setArgumentsJson("{\"query\":\"restored\"}");
        stale.setResultSummaryJson("{\"answer\":\"restored\"}");
        stale.setStatus("completed");

        assertEquals(0, agentToolCallMapper.updateGuarded(stale, "worker-a"));
        AiAgentToolCall late = new AiAgentToolCall();
        late.setAnalysisRunId(30L);
        late.setStepNo(2);
        late.setToolName("search_cases");
        late.setArgumentsJson("{\"query\":\"late\"}");
        late.setStatus("pending");
        late.setEvidenceCount(0);
        late.setLatencyMs(0L);
        assertEquals(0, agentToolCallMapper.insertGuarded(late, "worker-a"));
        Map<String, Object> tool = jdbc.queryForMap(
                "SELECT arguments_json,result_summary_json,evidence_hash FROM ai_agent_tool_calls WHERE id=40");
        assertEquals("{}", String.valueOf(tool.get("arguments_json")));
        assertEquals("{}", String.valueOf(tool.get("result_summary_json")));
        assertEquals(null, tool.get("evidence_hash"));
        Map<String, Object> run = jdbc.queryForMap(
                "SELECT result_json,request_content_hash,start_profile_hash FROM ai_analysis_runs WHERE id=30");
        assertEquals(null, run.get("request_content_hash"));
        assertEquals(null, run.get("start_profile_hash"));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT content_generation FROM ai_agent_sessions WHERE id=10", Long.class));
        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT operation,session_id,user_id,operator_type,operator_id,result,diagnostic_code
                FROM ai_agent_content_purge_audits WHERE session_id=10
                """);
        assertEquals("manual_purge", audit.get("operation"));
        assertEquals("user", audit.get("operator_type"));
        assertEquals("success", audit.get("result"));
        assertEquals(null, audit.get("diagnostic_code"));
    }

    @Test
    void concurrentPurgeIsRejectedWhileAnActiveToolCallbackStillOwnsTheRun() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES " +
                "(42,'owner','owner@example.com','active')");
        jdbc.update("""
                INSERT INTO ai_agent_sessions
                    (id,user_id,title,title_mode,status,deleted_at,purge_after,content_generation)
                VALUES (10,42,'Active private run','manual','active',NOW(6),
                        DATE_ADD(NOW(6),INTERVAL 30 DAY),0)
                """);
        jdbc.update("INSERT INTO ai_agent_messages " +
                "(id,session_id,role,content,status,sequence_no,citations_json) VALUES " +
                "(20,10,'user','active sensitive question','completed',1,JSON_ARRAY())");
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,session_id,user_message_id,idempotency_key,
                     session_content_generation,status,provider,model_id,prompt_version,evidence_hash,
                     lease_owner,settlement_status,settlement_version)
                VALUES (30,42,'agent_research',10,20,'active-purge-race',0,'running','fake','fake',
                        'agent-v1',REPEAT('a',64),'worker-a','reserved',0)
                """);
        jdbc.update("""
                INSERT INTO ai_agent_tool_calls
                    (id,analysis_run_id,step_no,tool_name,arguments_json,status,evidence_count,latency_ms)
                VALUES (40,30,1,'search_cases',JSON_OBJECT('query','active secret'),'pending',0,0)
                """);
        AiAgentToolCall callback = agentToolCallMapper.selectById(40L);
        callback.setResultSummaryJson("{\"answer\":\"completed before purge\"}");
        callback.setStatus("completed");
        callback.setEvidenceCount(1);
        CountDownLatch callbackReady = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> callbackResult = executor.submit(() -> {
                callbackReady.countDown();
                releaseCallback.await(10, TimeUnit.SECONDS);
                return agentToolCallMapper.updateGuarded(callback, "worker-a");
            });
            assertTrue(callbackReady.await(10, TimeUnit.SECONDS));

            BusinessException rejection = assertThrows(BusinessException.class,
                    () -> agentSessionHistoryService.purge(
                            new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L));
            assertEquals(ErrorCode.CONFLICT, rejection.getErrorCode());
            assertEquals(null, jdbc.queryForObject(
                    "SELECT purged_at FROM ai_agent_sessions WHERE id=10", LocalDateTime.class));

            releaseCallback.countDown();
            assertEquals(1, callbackResult.get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM ai_agent_content_purge_audits
                    WHERE session_id=10 AND result='rejected'
                    """, Integer.class));
            assertEquals(0L, jdbc.queryForObject(
                    "SELECT settlement_version FROM ai_analysis_runs WHERE id=30", Long.class));
        } finally {
            releaseCallback.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentLateToolCallbackCannotRestoreContentAfterTerminalPurgeBoundary() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES " +
                "(42,'owner','owner@example.com','active')");
        jdbc.update("""
                INSERT INTO ai_agent_sessions
                    (id,user_id,title,title_mode,status,profile_json,deleted_at,purge_after,content_generation)
                VALUES (10,42,'Terminal private run','manual','active',JSON_OBJECT('region','Hubei'),
                        NOW(6),DATE_ADD(NOW(6),INTERVAL 30 DAY),0)
                """);
        jdbc.update("""
                INSERT INTO ai_agent_messages
                    (id,session_id,role,content,status,sequence_no,citations_json)
                VALUES (20,10,'user','terminal sensitive question','completed',1,
                        JSON_ARRAY(JSON_OBJECT('sourceId',1,'claim','sensitive claim')))
                """);
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,session_id,user_message_id,idempotency_key,
                     session_content_generation,status,provider,model_id,prompt_version,evidence_hash,
                     lease_owner,settlement_status,prompt_tokens,completion_tokens,total_tokens,
                     reserved_tokens,settlement_version,result_json,completed_at)
                VALUES (30,42,'agent_research',10,20,'terminal-purge-race',0,'cancelled','fake','fake',
                        'agent-v1',REPEAT('a',64),'worker-a','settled_actual',5,3,8,0,1,
                        JSON_OBJECT('answer','terminal sensitive answer'),NOW(6))
                """);
        jdbc.update("""
                INSERT INTO ai_agent_tool_calls
                    (id,analysis_run_id,step_no,tool_name,arguments_json,result_summary_json,status,
                     evidence_hash,evidence_count,latency_ms)
                VALUES (40,30,1,'search_cases',JSON_OBJECT('query','terminal secret'),
                        JSON_OBJECT('answer','terminal secret'),'pending',REPEAT('e',64),1,0)
                """);
        AiAgentToolCall callback = agentToolCallMapper.selectById(40L);
        callback.setArgumentsJson("{\"query\":\"late restored query\"}");
        callback.setResultSummaryJson("{\"answer\":\"late restored answer\"}");
        callback.setStatus("completed");
        CountDownLatch callbackReady = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> callbackResult = executor.submit(() -> {
                callbackReady.countDown();
                releaseCallback.await(10, TimeUnit.SECONDS);
                return agentToolCallMapper.updateGuarded(callback, "worker-a");
            });
            assertTrue(callbackReady.await(10, TimeUnit.SECONDS));

            agentSessionHistoryService.purge(
                    new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L);
            releaseCallback.countDown();
            assertEquals(0, callbackResult.get(10, TimeUnit.SECONDS));

            Map<String, Object> message = jdbc.queryForMap(
                    "SELECT content,citations_json FROM ai_agent_messages WHERE id=20");
            assertEquals("[已删除]", message.get("content"));
            assertEquals("[]", String.valueOf(message.get("citations_json")));
            Map<String, Object> tool = jdbc.queryForMap("""
                    SELECT arguments_json,result_summary_json,evidence_hash
                    FROM ai_agent_tool_calls WHERE id=40
                    """);
            assertEquals("{}", String.valueOf(tool.get("arguments_json")));
            assertEquals("{}", String.valueOf(tool.get("result_summary_json")));
            assertEquals(null, tool.get("evidence_hash"));
            Map<String, Object> run = jdbc.queryForMap("""
                    SELECT result_json,prompt_tokens,completion_tokens,total_tokens,
                           settlement_status,settlement_version
                    FROM ai_analysis_runs WHERE id=30
                    """);
            assertEquals(null, run.get("result_json"));
            assertEquals(5, ((Number) run.get("prompt_tokens")).intValue());
            assertEquals(3, ((Number) run.get("completion_tokens")).intValue());
            assertEquals(8, ((Number) run.get("total_tokens")).intValue());
            assertEquals("settled_actual", run.get("settlement_status"));
            assertEquals(1L, ((Number) run.get("settlement_version")).longValue());
            assertEquals(1, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM ai_agent_content_purge_audits
                    WHERE session_id=10 AND result='success'
                    """, Integer.class));
            assertEquals(0, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM ai_agent_content_purge_audits
                    WHERE session_id=10 AND (
                      operation LIKE '%terminal sensitive%' OR
                      COALESCE(diagnostic_code,'') LIKE '%terminal sensitive%'
                    )
                    """, Integer.class));
        } finally {
            releaseCallback.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void assistantWorkspaceScheduledPurgeUsesMySqlLockingAndScrubsReadableContent() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (1,'owner','active')");
        jdbc.update("""
                INSERT INTO ai_agent_sessions
                    (id,user_id,title,title_mode,status,profile_json,deleted_at,purge_after)
                VALUES (10,1,'Private research','manual','active',JSON_OBJECT('region','Hubei'),
                        DATE_SUB(NOW(6),INTERVAL 31 DAY),DATE_SUB(NOW(6),INTERVAL 1 DAY))
                """);
        jdbc.update("""
                INSERT INTO ai_agent_messages
                    (session_id,role,content,status,sequence_no,citations_json)
                VALUES (10,'user','Sensitive question','completed',1,JSON_ARRAY(JSON_OBJECT('title','Private source')))
                """);

        assertEquals(1, agentSessionHistoryService.purgeDue());

        Map<String, Object> session = jdbc.queryForMap("""
                SELECT title,title_mode,profile_json,purged_at
                FROM ai_agent_sessions WHERE id=10
                """);
        assertEquals("[已删除]", session.get("title"));
        assertEquals("manual", session.get("title_mode"));
        assertEquals(null, session.get("profile_json"));
        assertNotNull(session.get("purged_at"));
        Map<String, Object> message = jdbc.queryForMap(
                "SELECT content,JSON_LENGTH(citations_json) AS citation_count FROM ai_agent_messages WHERE session_id=10");
        assertEquals("[已删除]", message.get("content"));
        assertEquals(0, ((Number) message.get("citation_count")).intValue());
    }

    @Test
    void legacySessionListHidesTrashAndPurgedSessionsAndPurgedDetailsAreUnreadable() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES (10,42,'Visible','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status,deleted_at,purge_after) VALUES (11,42,'Trash','active',NOW(6),DATE_ADD(NOW(6),INTERVAL 30 DAY))");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status,deleted_at,purge_after,purged_at) VALUES (12,42,'[已删除]','active',DATE_SUB(NOW(6),INTERVAL 31 DAY),DATE_SUB(NOW(6),INTERVAL 1 DAY),NOW(6))");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

        assertEquals(List.of(10L), agentSessionService.list(owner).stream().map(session -> session.getId()).toList());
        assertThrows(BusinessException.class, () -> agentSessionService.requireOwned(owner, 12L));
    }

    @Test
    void assistantUsageProjectionReadsOnlyTodaysAgentTokensFromMySql() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES (10,42,'Usage','active')");
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) VALUES (20,10,'user','Question','completed',1)");
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) VALUES (21,10,'user','Queued question','completed',2)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,session_id,user_message_id,idempotency_key,status,
                     provider,model_id,prompt_version,evidence_hash,prompt_tokens,completion_tokens,total_tokens)
                VALUES (30,42,'agent_research',10,20,'usage-today','completed',
                        'fake','fake','agent-v1',REPEAT('a',64),10,5,15)
                """);
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,session_id,user_message_id,idempotency_key,status,
                     provider,model_id,prompt_version,evidence_hash,reserved_tokens)
                VALUES (31,42,'agent_research',10,21,'usage-reserved','received',
                        'fake','fake','agent-v1',REPEAT('b',64),8000)
                """);
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,status,provider,model_id,prompt_version,evidence_hash,total_tokens)
                VALUES (32,42,'case_analysis','completed','fake','fake','case-v1',REPEAT('c',64),5)
                """);
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

        var usage = agentSessionHistoryService.usage(owner);
        assertEquals(20L, usage.usedTokens());
        assertEquals(8_000L, usage.reservedTokens());
        assertEquals(100_000L, usage.dailyLimit());
        assertEquals(100_000L, usage.limitTokens());
        assertEquals(91_980L, usage.remainingTokens());
    }

    @Test
    void legacyAiTaskReservationUsesDefaultsForAgentSubmissionColumns() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AiAnalysisRun run = new AiAnalysisRun();
        run.setUserId(42L);
        run.setTaskType("entrepreneurship_advice");
        run.setStatus("running");
        run.setProvider("fake");
        run.setModelId("fake-model");
        run.setPromptVersion("entrepreneurship-advisor-v2");
        run.setEvidenceHash("a".repeat(64));

        int reserved = runMapper.reserve(run, 100_000L, 1_200);

        assertEquals(1, reserved);
        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT submission_kind,session_content_generation FROM ai_analysis_runs WHERE id=?",
                run.getId());
        assertEquals("message", stored.get("submission_kind"));
        assertEquals(0L, ((Number) stored.get("session_content_generation")).longValue());
    }

    @Test
    void agentRuntimePostcheckCountsCompositeIndexesByName() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();

        Map<String, Object> result = jdbc.queryForMap(Files.readString(
                Path.of("..", "deploy", "sql", "20260725_agent_runtime_postcheck.sql")));

        assertEquals(8, ((Number) result.get("agent_unique_indexes")).intValue());
    }

    @Test
    void agentRuntimePostcheckReportsMissingAndUnexpectedIndexNames() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.execute("ALTER TABLE ai_agent_messages DROP INDEX idx_agent_messages_session_created");
        jdbc.execute("ALTER TABLE ai_agent_messages ADD INDEX idx_agent_messages_unexpected (status)");

        Map<String, Object> result = jdbc.queryForMap(Files.readString(
                Path.of("..", "deploy", "sql", "20260725_agent_runtime_postcheck.sql")));

        assertEquals("ai_agent_messages.idx_agent_messages_session_created", result.get("missing_indexes"));
        assertEquals("ai_agent_messages.idx_agent_messages_unexpected", result.get("unexpected_indexes"));
    }

    @Test
    void agentRuntimeDefaultsOffEvenWhenProviderIsEnabled() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        jdbc.execute("ALTER TABLE ai_model_settings DROP COLUMN agent_enabled");
        jdbc.update("UPDATE ai_model_settings SET enabled=1 WHERE id=1");

        runAgentRuntimeMigration();

        assertEquals(0, jdbc.queryForObject(
                "SELECT agent_enabled FROM ai_model_settings WHERE id=1", Integer.class));
    }

    @Test
    void agentRuntimeStabilizationMigrationIsRepeatableAndPreservesExplicitRollout() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        jdbc.execute("ALTER TABLE ai_model_settings DROP COLUMN agent_rollout_changed_by_admin_id");
        jdbc.execute("ALTER TABLE ai_model_settings DROP COLUMN agent_rollout_changed_at");
        jdbc.execute("ALTER TABLE ai_model_settings DROP COLUMN agent_rollout_state");
        runAgentRuntimeMigration();
        jdbc.update("UPDATE ai_model_settings SET agent_enabled=1 WHERE id=1");

        runAgentRuntimeStabilizationMigration();
        runAgentRuntimeStabilizationMigration();

        assertEquals("explicitly_enabled", jdbc.queryForObject(
                "SELECT agent_rollout_state FROM ai_model_settings WHERE id=1", String.class));
        assertEquals(12, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND (
                  (table_name='ai_analysis_runs' AND column_name IN (
                    'lease_owner','lease_expires_at','execution_attempts','next_attempt_at',
                    'last_recovery_reason','settlement_status','provider_dispatched_at',
                    'settled_at','settlement_version','session_nonterminal_guard','user_agent_nonterminal_guard'
                  )) OR (table_name='ai_agent_sessions' AND column_name='research_context_json')
                )
                """, Integer.class));
    }

    @Test
    void twoWorkersCanClaimReceivedRunOnlyOnce() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES (10,42,'Lease test','active')");
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) " +
                "VALUES (20,10,'user','Research Hubei AI','completed',1)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  id,user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,reserved_tokens,deadline_at,
                  current_stage,visible_progress,settlement_status
                ) VALUES (
                  30,42,'agent_research',10,20,'idem-lease-123','received',
                  'fake','fake-agent','agent-research-v1',REPEAT('a',64),8000,
                  ?,'received','queued','reserved'
                )
                """, LocalDateTime.now().plusMinutes(2));
        AtomicInteger claims = new AtomicInteger();

        List<Throwable> failures = runTogether(
                () -> {
                    if (agentRunQueueService.claimNext("worker-a") != null) claims.incrementAndGet();
                    return null;
                },
                () -> {
                    if (agentRunQueueService.claimNext("worker-b") != null) claims.incrementAndGet();
                    return null;
                }
        );

        assertTrue(failures.isEmpty());
        assertEquals(1, claims.get());
        Map<String, Object> run = jdbc.queryForMap(
                "SELECT status,lease_owner,execution_attempts FROM ai_analysis_runs WHERE id=30");
        assertEquals("running", run.get("status"));
        assertTrue(Set.of("worker-a", "worker-b").contains(run.get("lease_owner")));
        assertEquals(1, ((Number) run.get("execution_attempts")).intValue());
    }

    @Test
    void reclaimingWithTheSameOwnerRejectsThePreviousAttemptStageWrite() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES (10,42,'Lease generation','active')");
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) "
                + "VALUES (20,10,'user','Research','completed',1)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  id,user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,reserved_tokens,deadline_at,
                  current_stage,visible_progress,settlement_status
                ) VALUES (
                  30,42,'agent_research',10,20,'idem-generation-123','received',
                  'fake','fake-agent','agent-research-v1',REPEAT('a',64),8000,
                  ?,'received','queued','reserved'
                )
                """, LocalDateTime.now().plusMinutes(2));

        AiAnalysisRun first = agentRunQueueService.claimNext("worker-a");
        assertNotNull(first);
        jdbc.update("""
                INSERT INTO ai_agent_provider_calls (
                  id,analysis_run_id,round_no,internal_request_id,settlement_status,reserved_tokens,
                  prompt_tokens,completion_tokens,total_tokens,latency_ms,dispatched_at
                ) VALUES (301,30,1,'first-attempt-provider-call','provider_dispatched',100,0,0,0,0,?)
                """, LocalDateTime.now());
        jdbc.update("UPDATE ai_analysis_runs SET lease_expires_at=DATE_SUB(NOW(6),INTERVAL 1 SECOND) WHERE id=30");
        AiAnalysisRun second = agentRunQueueService.claimNext("worker-a");
        assertNotNull(second);
        assertEquals(2, second.getExecutionAttempts());

        LocalDateTime staleAt = LocalDateTime.now();
        int staleWrite = runMapper.updateAgentStageFenced(
                first.getId(), "worker-a", first.getExecutionAttempts(),
                "stale_stage", "stale", 99, 99, staleAt);
        int staleUsage = runMapper.recordAgentUsageFenced(
                first.getId(), "worker-a", first.getExecutionAttempts(),
                10, 5, 15, 20, "late-response", "stop", staleAt);
        int staleComplete = runMapper.settleAgentCompletedFenced(
                first.getId(), "worker-a", first.getExecutionAttempts(), "completed",
                10, 5, 15, 20, "late-response", "stop", 2, 1,
                "{\"late\":true}", null, staleAt);
        int staleFailure = runMapper.settleAgentFailedFenced(
                first.getId(), "worker-a", first.getExecutionAttempts(), "failed", "stale",
                "UPSTREAM_ERROR", "LATE", 2, 1, staleAt);
        AiAgentProviderCall staleProviderCall = new AiAgentProviderCall();
        staleProviderCall.setAnalysisRunId(first.getId());
        staleProviderCall.setRoundNo(1);
        staleProviderCall.setInternalRequestId("old-attempt-provider-call");
        staleProviderCall.setSettlementStatus("provider_dispatched");
        staleProviderCall.setReservedTokens(100);
        staleProviderCall.setPromptTokens(0);
        staleProviderCall.setCompletionTokens(0);
        staleProviderCall.setTotalTokens(0);
        staleProviderCall.setLatencyMs(0L);
        staleProviderCall.setDispatchedAt(staleAt);
        int staleProviderInsert = agentProviderCallMapper.insertGuardedFenced(
                staleProviderCall, "worker-a", first.getExecutionAttempts(), staleAt);
        int staleProviderSettlement = agentProviderCallMapper.settleActualFenced(
                301L, "worker-a", first.getExecutionAttempts(), 10, 5, 15, 20,
                "late-response", "stop", staleAt);
        int staleProviderEstimate = agentProviderCallMapper.markDispatchedEstimatedFenced(
                first.getId(), "worker-a", first.getExecutionAttempts(), staleAt);

        assertEquals(0, staleWrite);
        assertEquals(0, staleUsage);
        assertEquals(0, staleComplete);
        assertEquals(0, staleFailure);
        assertEquals(0, staleProviderInsert);
        assertEquals(0, staleProviderSettlement);
        assertEquals(0, staleProviderEstimate);
        assertEquals("planning", jdbc.queryForObject(
                "SELECT current_stage FROM ai_analysis_runs WHERE id=30", String.class));
    }

    @Test
    void hubeiClarificationResolvesRealRegionAndDoesNotAskAgain() throws Exception {
        jdbc.update("DELETE FROM regions");
        jdbc.update("INSERT INTO regions (id,name,level,parent_id) VALUES " +
                "(1,'中国','country',NULL),(2,'湖北省','province',1)");
        jdbc.update("INSERT INTO tags (id,name,tag_type,is_industry) VALUES (701,'人工智能','industry',1)");

        var first = agentClarificationPolicy.evaluate(
                "{\"industryTagId\":701,\"industry\":\"人工智能\"}",
                null,
                "研究人工智能创业政策支持"
        );
        assertTrue(first.question().contains("地区"));
        assertTrue(first.contextJson().contains("region"));

        var resolved = agentClarificationPolicy.evaluate(
                "{\"industryTagId\":701,\"industry\":\"人工智能\"}",
                first.contextJson(),
                "湖北省"
        );

        assertEquals(null, resolved.question());
        assertEquals(false, resolved.evidenceInsufficient());
        var context = new ObjectMapper().readTree(resolved.contextJson());
        assertEquals(2L, context.path("resolvedFields").path("regionId").asLong());
        assertEquals("湖北省", context.path("resolvedFields").path("regionName").asText());
        assertTrue(context.path("pendingFields").isEmpty());
    }

    @Test
    void cancellingBeforeProviderDispatchReleasesReservation() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES (10,42,'Cancel test','active')");
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) " +
                "VALUES (20,10,'user','Research','completed',1)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  id,user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,reserved_tokens,deadline_at,
                  current_stage,visible_progress,settlement_status
                ) VALUES (
                  30,42,'agent_research',10,20,'idem-cancel-123','received',
                  'fake','fake-agent','agent-research-v1',REPEAT('a',64),8000,
                  ?,'received','queued','reserved'
                )
                """, LocalDateTime.now().plusMinutes(2));

        agentRunLifecycleService.cancel(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), 30L);

        Map<String, Object> run = jdbc.queryForMap(
                "SELECT status,settlement_status,reserved_tokens,total_tokens FROM ai_analysis_runs WHERE id=30");
        assertEquals("cancelled", run.get("status"));
        assertEquals("released_without_dispatch", run.get("settlement_status"));
        assertEquals(0, ((Number) run.get("reserved_tokens")).intValue());
        assertEquals(0, ((Number) run.get("total_tokens")).intValue());
    }

    @Test
    void failedDispatchedRunSettlesBoundedEstimateOnlyOnce() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES (10,42,'Estimate test','active')");
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) " +
                "VALUES (20,10,'user','Research','completed',1)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  id,user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,reserved_tokens,deadline_at,
                  current_stage,visible_progress,settlement_status,prompt_tokens,completion_tokens,total_tokens
                ) VALUES (
                  30,42,'agent_research',10,20,'idem-estimate-123','running',
                  'fake','fake-agent','agent-research-v1',REPEAT('a',64),8000,
                  ?,'waiting_for_model','waiting','provider_dispatched',700,300,1000
                )
                """, LocalDateTime.now().plusMinutes(2));

        int first = runMapper.settleAgentFailed(
                30L, "failed", "failed", "UPSTREAM_ERROR", "PROVIDER_TIMEOUT",
                1, 0, LocalDateTime.now());
        int duplicate = runMapper.settleAgentFailed(
                30L, "failed", "failed", "UPSTREAM_ERROR", "PROVIDER_TIMEOUT",
                1, 0, LocalDateTime.now());

        assertEquals(1, first);
        assertEquals(0, duplicate);
        Map<String, Object> run = jdbc.queryForMap(
                "SELECT status,settlement_status,reserved_tokens,total_tokens FROM ai_analysis_runs WHERE id=30");
        assertEquals("failed", run.get("status"));
        assertEquals("settled_estimated", run.get("settlement_status"));
        assertEquals(0, ((Number) run.get("reserved_tokens")).intValue());
        assertEquals(8000, ((Number) run.get("total_tokens")).intValue());
    }

    @Test
    void cancelledDispatchedRunStillConsumesDailyQuotaUntilSettlement() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES " +
                "(10,42,'Old','active'),(11,42,'New','active')");
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) VALUES " +
                "(20,10,'user','Old','completed',1),(21,11,'user','New','completed',1)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  id,user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,reserved_tokens,
                  current_stage,visible_progress,settlement_status,total_tokens
                ) VALUES (
                  30,42,'agent_research',10,20,'idem-old-dispatch','cancelled',
                  'fake','fake-agent','agent-research-v1',REPEAT('a',64),8000,
                  'cancelled','cancelled','provider_dispatched',0
                )
                """);
        AiAnalysisRun next = new AiAnalysisRun();
        next.setUserId(42L);
        next.setTaskType("agent_research");
        next.setSessionId(11L);
        next.setUserMessageId(21L);
        next.setIdempotencyKey("idem-new-dispatch");
        next.setStatus("received");
        next.setProvider("fake");
        next.setModelId("fake-agent");
        next.setPromptVersion("agent-research-v1");
        next.setEvidenceHash("b".repeat(64));
        next.setCurrentStage("received");
        next.setVisibleProgress("queued");

        int reserved = runMapper.reserve(next, 8000, 1000);

        assertEquals(0, reserved);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_analysis_runs WHERE user_id=42", Integer.class));
    }

    @Test
    void cancelledDispatchedRunReconcilesBoundedEstimateWithoutChangingTerminalStatus() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES (10,42,'Cancelled','active')");
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) " +
                "VALUES (20,10,'user','Research','completed',1)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  id,user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,reserved_tokens,
                  current_stage,visible_progress,settlement_status
                ) VALUES (
                  30,42,'agent_research',10,20,'idem-cancel-estimate','cancelled',
                  'fake','fake-agent','agent-research-v1',REPEAT('a',64),8000,
                  'cancelled','cancelled','provider_dispatched'
                )
                """);
        jdbc.update("""
                INSERT INTO ai_agent_provider_calls (
                  id,analysis_run_id,round_no,internal_request_id,settlement_status,reserved_tokens,dispatched_at
                ) VALUES (40,30,1,'internal-cancelled','provider_dispatched',8000,NOW(6))
                """);
        LocalDateTime settledAt = LocalDateTime.now();
        assertEquals(1, agentProviderCallMapper.markDispatchedEstimated(30L, settledAt));
        assertEquals(1, runMapper.reconcileAgentProviderUsage(30L, settledAt));

        Map<String, Object> run = jdbc.queryForMap(
                "SELECT status,settlement_status,reserved_tokens,total_tokens FROM ai_analysis_runs WHERE id=30");
        assertEquals("cancelled", run.get("status"));
        assertEquals("settled_estimated", run.get("settlement_status"));
        assertEquals(0, ((Number) run.get("reserved_tokens")).intValue());
        assertEquals(8000, ((Number) run.get("total_tokens")).intValue());
        assertEquals("settled_estimated", jdbc.queryForObject(
                "SELECT settlement_status FROM ai_agent_provider_calls WHERE id=40", String.class));
    }

    @Test
    void exhaustedExpiredLeaseMovesToTerminalAndCannotBeReclaimed() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,status) VALUES (10,42,'Recovery','active')");
        jdbc.update("INSERT INTO ai_agent_messages (id,session_id,role,content,status,sequence_no) " +
                "VALUES (20,10,'user','Research','completed',1)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  id,user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,reserved_tokens,deadline_at,
                  current_stage,visible_progress,settlement_status,execution_attempts,
                  lease_owner,lease_expires_at
                ) VALUES (
                  30,42,'agent_research',10,20,'idem-recovery-max','running',
                  'fake','fake-agent','agent-research-v1',REPEAT('a',64),8000,
                  ?,'waiting_for_model','waiting','reserved',3,'dead-worker',?
                )
                """, LocalDateTime.now().plusMinutes(2), LocalDateTime.now().minusMinutes(1));

        int expired = agentRunQueueService.finalizeUnrecoverable();

        assertEquals(1, expired);
        Map<String, Object> run = jdbc.queryForMap(
                "SELECT status,diagnostic_code,settlement_status,reserved_tokens FROM ai_analysis_runs WHERE id=30");
        assertEquals("failed", run.get("status"));
        assertEquals("AGENT_RECOVERY_EXHAUSTED", run.get("diagnostic_code"));
        assertEquals("released_without_dispatch", run.get("settlement_status"));
        assertEquals(0, ((Number) run.get("reserved_tokens")).intValue());
        assertEquals(null, agentRunQueueService.claimNext("worker-after-terminal"));
    }

    @Test
    void automaticSessionTitleUsesOnlyTheFirstUserQuestion() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,title_mode,status) VALUES (10,42,'New research','auto','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

        agentSessionService.appendMessage(
                owner, 10L, "user", "First Hubei AI opportunity question", "completed", null, null);
        String firstTitle = jdbc.queryForObject(
                "SELECT title FROM ai_agent_sessions WHERE id=10", String.class);
        long firstRevision = jdbc.queryForObject(
                "SELECT assistant_history_revision FROM platform_users WHERE id=42", Long.class);
        agentSessionService.appendMessage(
                owner, 10L, "user", "A completely different follow-up", "completed", null, null);

        assertEquals("First Hubei AI opportunity question", firstTitle);
        assertEquals(firstTitle, jdbc.queryForObject(
                "SELECT title FROM ai_agent_sessions WHERE id=10", String.class));
        assertEquals(1L, firstRevision);
        assertEquals(firstRevision, jdbc.queryForObject(
                "SELECT assistant_history_revision FROM platform_users WHERE id=42", Long.class));
    }

    @Test
    void cancelledProviderRequestSettlesActualUsageBeforePermanentPurge() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Delayed provider settlement", null);
        var userMessage = agentSessionService.appendMessage(
                owner, session.getId(), "user", "Research delayed provider usage", "completed", null, null);
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AiClient delayedClient = new AiClient() {
            @Override
            public AiProviderResponse generate(AiProviderRequest request) {
                providerStarted.countDown();
                try {
                    if (!releaseProvider.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("provider release timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("provider wait interrupted", exception);
                }
                return new AiProviderResponse("late answer", 5, 3, 8, 25, "provider-request-late", "stop");
            }

            @Override
            public AiProviderDescriptor descriptor() {
                return new AiProviderDescriptor("fake", "fake-agent", true);
            }
        };
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "fake", "openai_compatible", "https://api.example.com/v1", "fake-agent", "test-key",
                0.2, 1200, java.time.Duration.ofSeconds(20), 0, true);
        AiRuntimeSettingsProvider runtimeProvider = new AiRuntimeSettingsProvider() {
            public AiRuntimeSettings current() { return runtime; }
            public long dailyTokenQuota() { return 100_000L; }
        };
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, delayedClient, runtimeProvider, agentProviderCallMapper,
                agentProviderSettlementService);
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, java.time.Duration.ofSeconds(120), "json_plan");
        var lease = lifecycle.begin(
                owner, session.getId(), userMessage.getId(), "idem-delayed-cancel", config);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<AiProviderResponse> provider = executor.submit(() -> lifecycle.invoke(lease, null));

        try {
            assertTrue(providerStarted.await(10, TimeUnit.SECONDS));
            lifecycle.cancel(owner, lease.run().getId());
            agentSessionHistoryService.trash(owner, session.getId());

            BusinessException unsettled = assertThrows(
                    BusinessException.class,
                    () -> agentSessionHistoryService.purge(owner, session.getId()));
            assertEquals(ErrorCode.CONFLICT, unsettled.getErrorCode());

            releaseProvider.countDown();
            assertEquals(8, provider.get(10, TimeUnit.SECONDS).totalTokens());
            agentSessionHistoryService.purge(owner, session.getId());

            Map<String, Object> run = jdbc.queryForMap("""
                    SELECT status,settlement_status,prompt_tokens,completion_tokens,total_tokens,reserved_tokens
                    FROM ai_analysis_runs WHERE id=?
                    """, lease.run().getId());
            assertEquals("cancelled", run.get("status"));
            assertEquals("settled_actual", run.get("settlement_status"));
            assertEquals(5, ((Number) run.get("prompt_tokens")).intValue());
            assertEquals(3, ((Number) run.get("completion_tokens")).intValue());
            assertEquals(8, ((Number) run.get("total_tokens")).intValue());
            assertEquals(0, ((Number) run.get("reserved_tokens")).intValue());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_agent_provider_calls WHERE analysis_run_id=? AND settlement_status='settled_actual'",
                    Integer.class, lease.run().getId()));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_agent_messages WHERE session_id=? AND role='assistant'",
                    Integer.class, session.getId()));
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void providerCallSettlementRollsBackWhenRunReconciliationFails() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Atomic provider settlement", null);
        var userMessage = agentSessionService.appendMessage(
                owner, session.getId(), "user", "Verify settlement rollback", "completed", null, null);
        java.util.concurrent.atomic.AtomicLong runId = new java.util.concurrent.atomic.AtomicLong();
        AiClient inconsistentClient = new AiClient() {
            @Override
            public AiProviderResponse generate(AiProviderRequest request) {
                jdbc.update("UPDATE ai_analysis_runs SET settlement_status='reserved' WHERE id=?", runId.get());
                return new AiProviderResponse("unused", 5, 3, 8, 25, "provider-request-rollback", "stop");
            }

            @Override
            public AiProviderDescriptor descriptor() {
                return new AiProviderDescriptor("fake", "fake-agent", true);
            }
        };
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "fake", "openai_compatible", "https://api.example.com/v1", "fake-agent", "test-key",
                0.2, 1200, java.time.Duration.ofSeconds(20), 0, true);
        AiRuntimeSettingsProvider runtimeProvider = new AiRuntimeSettingsProvider() {
            public AiRuntimeSettings current() { return runtime; }
            public long dailyTokenQuota() { return 100_000L; }
        };
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, inconsistentClient, runtimeProvider, agentProviderCallMapper,
                agentProviderSettlementService);
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, java.time.Duration.ofSeconds(120), "json_plan");
        var lease = lifecycle.begin(
                owner, session.getId(), userMessage.getId(), "idem-settlement-rollback", config);
        runId.set(lease.run().getId());

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> lifecycle.invoke(lease, null));

        assertEquals(ErrorCode.CONFLICT, failure.getErrorCode());
        assertEquals("provider_dispatched", jdbc.queryForObject(
                "SELECT settlement_status FROM ai_agent_provider_calls WHERE analysis_run_id=?",
                String.class, lease.run().getId()));
        assertEquals("reserved", jdbc.queryForObject(
                "SELECT settlement_status FROM ai_analysis_runs WHERE id=?",
                String.class, lease.run().getId()));
    }

    @Test
    void contractFailureKeepsActualUsageAndLeavesNoResultOrEvidenceSideEffects() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Contract replay", null);
        var userMessage = agentSessionService.appendMessage(
                owner, session.getId(), "user", "Sanitized contract replay", "completed", null, null);
        AiClient invalidJsonClient = new AiClient() {
            public AiProviderResponse generate(AiProviderRequest request) {
                return new AiProviderResponse("{", 4, 3, 7, 12, "contract-replay-request", "stop");
            }

            public AiProviderDescriptor descriptor() {
                return new AiProviderDescriptor("fake", "fake-agent", true);
            }
        };
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "fake", "openai_compatible", "https://api.example.com/v1", "fake-agent", "test-key",
                0.2, 1200, java.time.Duration.ofSeconds(20), 0, true);
        AiRuntimeSettingsProvider runtimeProvider = new AiRuntimeSettingsProvider() {
            public AiRuntimeSettings current() { return runtime; }
            public long dailyTokenQuota() { return 100_000L; }
        };
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, invalidJsonClient, runtimeProvider, agentProviderCallMapper,
                agentProviderSettlementService);
        var lease = lifecycle.begin(
                owner, session.getId(), userMessage.getId(), "idem-contract-replay",
                new AgentRuntimeConfig(true, 4, 6, 8000, 12,
                        java.time.Duration.ofSeconds(120), "json_plan"));

        AiProviderResponse response = lifecycle.invoke(lease, null);
        assertEquals(7, response.totalTokens());
        lifecycle.updateStage(lease, "waiting_for_model", 1, 0);
        lifecycle.fail(lease, "failed", ErrorCode.UPSTREAM_ERROR, "INVALID_JSON");
        lifecycle.fail(lease, "failed", ErrorCode.UPSTREAM_ERROR, "INVALID_JSON");

        Map<String, Object> run = jdbc.queryForMap("""
                SELECT status,diagnostic_code,settlement_status,prompt_tokens,completion_tokens,
                       total_tokens,reserved_tokens,result_json
                FROM ai_analysis_runs WHERE id=?
                """, lease.run().getId());
        assertEquals("failed", run.get("status"));
        assertEquals("INVALID_JSON", run.get("diagnostic_code"));
        assertEquals("settled_actual", run.get("settlement_status"));
        assertEquals(4, ((Number) run.get("prompt_tokens")).intValue());
        assertEquals(3, ((Number) run.get("completion_tokens")).intValue());
        assertEquals(7, ((Number) run.get("total_tokens")).intValue());
        assertEquals(0, ((Number) run.get("reserved_tokens")).intValue());
        assertEquals(null, run.get("result_json"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_agent_provider_calls WHERE analysis_run_id=? AND settlement_status='settled_actual'",
                Integer.class, lease.run().getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_agent_tool_calls WHERE analysis_run_id=?",
                Integer.class, lease.run().getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_agent_messages WHERE session_id=? AND role='assistant'",
                Integer.class, session.getId()));
    }

    @Test
    void agentSessionsEnforceOwnershipArchiveAndStableMessageOrder() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active'),(43,'other','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        AuthenticatedUser other = new AuthenticatedUser(43L, "other", "other@example.com");

        var session = agentSessionService.create(owner, "Hubei AI research", null);
        var first = agentSessionService.appendMessage(owner, session.getId(), "user", "Question", "completed", null, null);
        var second = agentSessionService.appendMessage(owner, session.getId(), "assistant", "Answer", "completed", null, "[]");

        assertEquals(1, first.getSequenceNo());
        assertEquals(2, second.getSequenceNo());
        assertEquals(List.of(1, 2), agentSessionService.recentMessages(owner, session.getId(), 12).stream()
                .map(message -> message.getSequenceNo()).toList());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> agentSessionService.requireOwned(other, session.getId())).getErrorCode());

        agentSessionService.archive(owner, session.getId());
        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> agentSessionService.appendMessage(owner, session.getId(), "user", "Late", "completed", null, null))
                .getErrorCode());
    }

    @Test
    void receivedRunPreventsArchiveAndRemainsVisibleAfterRefresh() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Durable received run", null);
        var message = agentSessionService.appendMessage(
                owner, session.getId(), "user", "Question", "completed", null, null);
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,current_stage,visible_progress
                ) VALUES (42,'agent_research',?,?,?,'evidence_insufficient','not_called','not_called',
                  'agent-research-v1',REPEAT('b',64),'evidence_insufficient','No verified evidence')
                """, session.getId(), message.getId(), "idem-previous-terminal");
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,current_stage,visible_progress
                ) VALUES (42,'agent_research',?,?,?,'received','fake','fake-agent',
                  'agent-research-v1',REPEAT('a',64),'received','正在分析需求')
                """, session.getId(), message.getId(), "idem-received-refresh");

        BusinessException conflict = assertThrows(
                BusinessException.class, () -> agentSessionService.archive(owner, session.getId()));

        assertEquals(ErrorCode.CONFLICT, conflict.getErrorCode());
        assertEquals("active", jdbc.queryForObject(
                "SELECT status FROM ai_agent_sessions WHERE id=?", String.class, session.getId()));
        var detail = agentResearchQueryService.sessionDetail(owner, session.getId());
        assertNotNull(detail.activeRun());
        assertEquals("received", detail.activeRun().status());
        assertNotNull(detail.latestRun());
        assertEquals("evidence_insufficient", detail.latestRun().status());
        assertEquals("Question", detail.latestRun().retryContent());
        assertEquals(false, detail.activeRun().runId().equals(detail.latestRun().runId()));
        assertEquals("received", detail.session().activeRunStatus());
        var compatibilityList = agentSessionService.list(owner);
        assertEquals(1, compatibilityList.size());
        assertEquals("received", compatibilityList.get(0).getActiveRunStatus());
    }

    @Test
    void completedStructuredResultIsReturnedByInitialAndPaginatedMessageHistory() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Structured history", null);
        var userMessage = agentSessionService.appendMessage(
                owner, session.getId(), "user", "Research question", "completed", null, null);
        jdbc.update("""
                INSERT INTO ai_analysis_runs (
                  user_id,task_type,session_id,user_message_id,idempotency_key,status,
                  provider,model_id,prompt_version,evidence_hash,current_stage,visible_progress,
                  result_json,completed_at
                ) VALUES (42,'agent_research',?,?,?,'completed','deepseek','deepseek-chat',
                  'agent-research-v2',REPEAT('c',64),'completed','Completed',?,CURRENT_TIMESTAMP(6))
                """, session.getId(), userMessage.getId(), "idem-structured-history",
                "{\"structuredResult\":{\"schemaVersion\":\"phase3-structured-result-v1\",\"directAnswer\":\"Frozen history answer\"}}");
        Long runId = jdbc.queryForObject(
                "SELECT id FROM ai_analysis_runs WHERE idempotency_key='idem-structured-history'",
                Long.class);
        agentSessionService.appendMessage(
                owner, session.getId(), "assistant", "Legacy visible answer", "completed", runId, "[]");

        var initial = agentResearchQueryService.sessionDetail(owner, session.getId());
        var paginated = agentSessionHistoryService.messages(owner, session.getId(), null, 50);

        assertEquals("Frozen history answer", initial.messages().get(1).structuredResult()
                .path("directAnswer").asText());
        assertEquals("Frozen history answer", paginated.items().get(1).structuredResult()
                .path("directAnswer").asText());
        assertEquals(null, initial.messages().get(0).structuredResult());
    }

    @Test
    void searchCasesToolReturnsOnlyPublishedVerifiedEvidence() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        prepareGuardedToolRun();
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertSource(2L, "Pending source", "published", "legacy_unverified", 0L);
        insertCase(11L, 1L, "Eligible case", "verified", 0L);
        insertCase(12L, 2L, "Bad source chain", "verified", 0L);
        insertCase(13L, 1L, "Pending case", "legacy_unverified", 0L);
        AgentToolContext context = new AgentToolContext(31L, 42L, null, 1L, null, null);

        var execution = agentToolRegistry.execute(
                context, 1, "search_cases", new ObjectMapper().readTree("{\"limit\":10}")
        );

        assertEquals(1, execution.result().output().path("items").size());
        assertEquals(11L, execution.result().output().path("items").get(0).path("caseId").asLong());
        assertEquals(Set.of(11L), context.allowedCaseIds());
        assertEquals(Set.of(1L), context.allowedSourceIds());
        assertEquals("completed", jdbc.queryForObject(
                "SELECT status FROM ai_agent_tool_calls WHERE analysis_run_id=31", String.class));
    }

    @Test
    void normalizedRequiredCaseSearchReturnsTwoDistinctEligibleCasesForTheSelectedIndustry() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        prepareGuardedToolRun();
        jdbc.update("DELETE FROM regions");
        jdbc.update("""
                INSERT INTO regions (id,name,level,parent_id) VALUES
                    (1,'China','country',NULL),(2,'Hubei','province',1),
                    (3,'Wuhan','city',2),(4,'Beijing','province',1)
                """);
        insertSource(1L, "Wuhan verified source", "published", "verified", 0L);
        insertSource(2L, "Beijing verified source", "published", "verified", 0L);
        insertCase(11L, 1L, "Wuhan OPC software studio", "verified", 0L);
        insertCase(12L, 2L, "Beijing retail shop", "verified", 0L);
        insertCase(13L, 1L, "Wuhan solo studio", "verified", 0L);
        insertCase(14L, 1L, "Wuhan unrelated retail shop", "verified", 0L);
        jdbc.update("UPDATE case_items SET region_id=3,summary='Uses a validated service workflow' WHERE id=11");
        jdbc.update("UPDATE case_items SET region_id=4 WHERE id=12");
        jdbc.update("UPDATE case_items SET region_id=3,summary='Validated service workflow',ai_tools='AIGC workflow' WHERE id=13");
        jdbc.update("UPDATE case_items SET region_id=3,summary='Traditional checkout workflow',ai_tools='' WHERE id=14");
        jdbc.update("INSERT INTO tags (id,name,tag_type,is_industry) VALUES (701,'AI application','case',1)");
        jdbc.update("INSERT INTO tag_aliases (tag_id,alias,normalized_alias) VALUES (701,'AIGC','aigc')");
        jdbc.update("INSERT INTO case_tags (case_id,tag_id) VALUES (11,701),(12,701)");

        var execution = agentToolRegistry.execute(
                new AgentToolContext(31L, 42L, null, 2L, 701L, "Artificial Intelligence"),
                1, "search_cases",
                new ObjectMapper().readTree("""
                        {"scope":"selected","limit":5}
                        """)
        );

        var items = execution.result().output().path("items");
        assertEquals(2, items.size());
        Set<Long> caseIds = java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .map(item -> item.path("caseId").asLong()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(11L, 13L), caseIds);
        assertTrue(java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .allMatch(item -> "Wuhan".equals(item.path("region").asText())));
    }

    @Test
    void searchPoliciesToolUsesRegionAncestorsAndPreservesApplicabilityMeaning() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        prepareGuardedToolRun();
        jdbc.update("DELETE FROM regions");
        jdbc.update("""
                INSERT INTO regions (id,name,level,parent_id) VALUES
                    (1,'China','country',NULL),(2,'Hubei','province',1),(3,'Wuhan','city',2),(4,'Beijing','province',1)
                """);
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "National reference", "verified", 0L);
        insertPolicy(22L, 1L, "Hubei general", "verified", 0L);
        insertPolicy(23L, 1L, "Hubei AI-specific", "verified", 0L);
        insertPolicy(24L, 1L, "Beijing general", "verified", 0L);
        jdbc.update("UPDATE policies SET region_id=1,applicability_mode='unclassified' WHERE id=21");
        jdbc.update("UPDATE policies SET region_id=2,applicability_mode='general' WHERE id=22");
        jdbc.update("UPDATE policies SET region_id=2,applicability_mode='specific' WHERE id=23");
        jdbc.update("UPDATE policies SET region_id=4,applicability_mode='general' WHERE id=24");
        jdbc.update("INSERT INTO tags (id,name,tag_type,is_industry) VALUES (701,'AI','policy',1)");
        jdbc.update("INSERT INTO policy_industry_tags (policy_id,industry_tag_id) VALUES (23,701)");
        AgentToolContext context = new AgentToolContext(31L, 42L, null, 3L, 701L, "AI");
        ObjectMapper objectMapper = new ObjectMapper();
        var items = objectMapper.createArrayNode();
        int step = 0;
        for (String scope : List.of("selected", "parent", "national")) {
            var scoped = agentToolRegistry.execute(
                    context, ++step, "search_policies",
                    objectMapper.readTree("{\"scope\":\"" + scope
                            + "\",\"query\":\"artificial intelligence startup policy\",\"limit\":10}")
            ).result().output().path("items");
            scoped.forEach(items::add);
        }

        assertEquals(3, items.size());
        assertTrue(java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .noneMatch(item -> item.path("policyId").asLong() == 24L));
        var unclassified = java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .filter(item -> "unclassified".equals(item.path("applicabilityMode").asText()))
                .findFirst().orElseThrow();
        assertTrue(unclassified.path("matchReason").asText().contains("地区"));
        assertTrue(!unclassified.path("matchReason").asText().contains("行业专项"));
    }

    @Test
    void regionNamesAuthorizeOnlyResolvedDirectoriesAndDeriveHierarchicalCoverage() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        prepareGuardedToolRun();
        jdbc.update("DELETE FROM regions");
        jdbc.update("""
                INSERT INTO regions (id,name,level,parent_id) VALUES
                    (1,'中国','country',NULL),(2,'湖北省','province',1),
                    (3,'武汉市','city',2),(4,'北京市','province',1)
                """);
        insertSource(1L, "National source", "published", "verified", 0L);
        insertSource(2L, "Hubei source", "published", "verified", 0L);
        insertSource(3L, "Wuhan source", "published", "verified", 0L);
        insertSource(4L, "Beijing source", "published", "verified", 0L);
        insertSource(5L, "Draft source", "draft", "verified", 0L);
        insertSource(6L, "Unverified source", "published", "legacy_unverified", 0L);
        insertPolicy(21L, 1L, "National support", "verified", 0L);
        insertPolicy(22L, 2L, "Hubei support", "verified", 0L);
        insertPolicy(23L, 3L, "Wuhan support", "verified", 0L);
        insertPolicy(24L, 5L, "Draft source policy", "verified", 0L);
        insertPolicy(25L, 6L, "Unverified source policy", "verified", 0L);
        jdbc.update("UPDATE policies SET region_id=1,applicability_mode='general' WHERE id=21");
        jdbc.update("UPDATE policies SET region_id=2,applicability_mode='general' WHERE id=22");
        jdbc.update("UPDATE policies SET region_id=3,applicability_mode='general' WHERE id IN (23,24,25)");
        insertCase(11L, 4L, "Beijing AI studio", "verified", 0L);
        insertCase(12L, 3L, "Wuhan traditional retail", "verified", 0L);
        insertCase(13L, 5L, "Beijing draft-source case", "verified", 0L);
        jdbc.update("UPDATE case_items SET region_id=4,summary='AI workflow' WHERE id IN (11,13)");
        jdbc.update("UPDATE case_items SET region_id=3,summary='Traditional retail' WHERE id=12");
        jdbc.update("INSERT INTO tags (id,name,tag_type,is_industry) VALUES (701,'AI','case',1)");
        jdbc.update("INSERT INTO case_tags (case_id,tag_id) VALUES (11,701),(13,701)");
        AgentToolContext context = new AgentToolContext(31L, 42L, null, 3L, 701L, "AI");
        ObjectMapper objectMapper = new ObjectMapper();
        var policies = objectMapper.createArrayNode();
        int step = 0;
        for (String scope : List.of("selected", "parent", "national")) {
            var scoped = agentToolRegistry.execute(
                    context, ++step, "search_policies",
                    objectMapper.readTree("{\"scope\":\"" + scope + "\",\"limit\":10}")
            ).result().output().path("items");
            scoped.forEach(policies::add);
        }
        var cases = agentToolRegistry.execute(
                context, ++step, "search_cases",
                objectMapper.readTree("{\"scope\":\"cross_region_reference\",\"limit\":10}")
        ).result().output().path("items");

        assertEquals(3, policies.size());
        assertEquals(Set.of("exact", "parent", "national"),
                java.util.stream.StreamSupport.stream(policies.spliterator(), false)
                        .map(item -> item.path("geographicScope").asText())
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, cases.size());
        assertEquals(11L, cases.get(0).path("caseId").asLong());
        assertEquals("cross_region", cases.get(0).path("geographicScope").asText());
        assertTrue(java.util.stream.StreamSupport.stream(cases.spliterator(), false)
                .noneMatch(item -> item.path("title").asText().contains("retail")));

        var coverage = context.deriveCoverage("final");
        assertEquals("sufficient", coverage.status());
        assertEquals(1, coverage.caseCount());
        assertEquals(3, coverage.policyCount());
        assertEquals(4, coverage.sourceCount());
        assertEquals(1, coverage.exactRegionCount());
        assertEquals(1, coverage.parentRegionCount());
        assertEquals(1, coverage.nationalCount());
        assertEquals(1, coverage.crossRegionCount());

        AgentToolException forbidden = assertThrows(AgentToolException.class, () -> agentToolRegistry.execute(
                context, 5, "search_cases", new ObjectMapper().readTree("{\"regionId\":999,\"limit\":3}")
        ));
        assertEquals("INVALID_TOOL_ARGUMENTS", forbidden.getDiagnosticCode());
    }

    @Test
    void getSourceToolCannotReadSourceOutsideCurrentRunResults() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        prepareGuardedToolRun();
        insertSource(1L, "Allowed source", "published", "verified", 0L);
        insertSource(2L, "Other source", "published", "verified", 0L);
        insertCase(11L, 1L, "Eligible case", "verified", 0L);
        AgentToolContext context = new AgentToolContext(31L, 42L, null, 1L, null, null);
        agentToolRegistry.execute(
                context, 1, "search_cases", new ObjectMapper().readTree("{\"limit\":10}")
        );

        AgentToolException forbidden = assertThrows(AgentToolException.class, () -> agentToolRegistry.execute(
                context, 2, "get_source", new ObjectMapper().readTree("{\"sourceId\":2}")
        ));
        assertEquals("FORBIDDEN_SOURCE_ID", forbidden.getDiagnosticCode());

        var allowed = agentToolRegistry.execute(
                context, 3, "get_source", new ObjectMapper().readTree("{\"sourceId\":1}")
        );
        assertEquals(1L, allowed.result().output().path("sourceId").asLong());
        assertEquals("Allowed source", allowed.result().output().path("title").asText());
        assertTrue(allowed.result().output().path("url").asText().startsWith("https://"));
    }

    @Test
    void runEvidenceApiRechecksOwnershipAndCurrentPublishedEvidenceState() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        prepareGuardedToolRun();
        jdbc.update("DELETE FROM regions");
        jdbc.update("""
                INSERT INTO regions (id,name,level,parent_id) VALUES
                    (1,'China','country',NULL),(2,'Hubei','province',1),(3,'Wuhan','city',2)
                """);
        insertSource(1L, "Government source", "published", "verified", 0L);
        insertSource(2L, "Case source", "published", "verified", 0L);
        insertCase(11L, 2L, "Wuhan AI studio", "verified", 0L);
        insertPolicy(21L, 1L, "Hubei startup support", "verified", 0L);
        jdbc.update("UPDATE case_items SET region_id=3,original_url='https://example.gov.cn/case/11' WHERE id=11");
        jdbc.update("UPDATE policies SET region_id=2,applicability_mode='general',original_url='https://example.gov.cn/policy/21' WHERE id=21");
        AgentToolContext context = new AgentToolContext(31L, 42L, null, 2L, null, null);
        agentToolRegistry.execute(context, 1, "search_cases",
                new ObjectMapper().readTree("{\"limit\":10}"));
        agentToolRegistry.execute(context, 2, "search_policies",
                new ObjectMapper().readTree("{\"scope\":\"selected\",\"limit\":10}"));
        agentToolRegistry.execute(context, 3, "get_source",
                new ObjectMapper().readTree("{\"sourceId\":2}"));

        var evidence = agentRunEvidenceService.read(user(), 31L);
        assertEquals(3, evidence.items().size());
        assertEquals(1, evidence.groups().get("case"));
        assertEquals(1, evidence.groups().get("policy"));
        assertEquals(1, evidence.groups().get("source"));
        var caseEvidence = evidence.items().stream()
                .filter(item -> "case".equals(item.itemType())).findFirst().orElseThrow();
        assertEquals("Wuhan", caseEvidence.regionName());
        assertEquals("/cases/11", caseEvidence.detailUrl());
        assertEquals("https://example.gov.cn/case/11", caseEvidence.originalUrl());
        assertTrue(caseEvidence.available());

        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> agentRunEvidenceService.read(new AuthenticatedUser(99L, "other", "other@example.com"), 31L));
        assertEquals(ErrorCode.NOT_FOUND, forbidden.getErrorCode());

        jdbc.update("UPDATE case_items SET status='draft',summary='must not be returned' WHERE id=11");
        var changedEvidence = agentRunEvidenceService.read(user(), 31L);
        assertEquals(3, changedEvidence.totalCount());
        assertEquals(2, changedEvidence.availableCount());
        assertEquals(1, changedEvidence.unavailableCount());
        assertEquals(0, changedEvidence.availableGroups().get("case"));
        assertEquals(1, changedEvidence.totalGroups().get("case"));
        var afterStateChange = changedEvidence.items().stream()
                .filter(item -> "case".equals(item.itemType())).findFirst().orElseThrow();
        assertEquals(false, afterStateChange.available());
        assertEquals("unavailable", afterStateChange.evidenceStatus());
        assertEquals("", afterStateChange.brief());
        assertEquals(null, afterStateChange.originalUrl());
    }

    @Test
    void runEvidenceApiSupportsRunsWithOnlyCaseEvidence() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        prepareGuardedToolRun();
        insertSource(1L, "Case source", "published", "verified", 0L);
        insertCase(11L, 1L, "Wuhan AI studio", "verified", 0L);
        AgentToolContext context = new AgentToolContext(31L, 42L, null, 1L, null, null);
        agentToolRegistry.execute(context, 1, "search_cases",
                new ObjectMapper().readTree("{\"limit\":10}"));

        var evidence = agentRunEvidenceService.read(user(), 31L);

        assertEquals(1, evidence.items().size());
        assertEquals(1, evidence.groups().get("case"));
        assertEquals(0, evidence.groups().get("policy"));
        assertEquals(0, evidence.groups().get("source"));
    }

    @Test
    void runEvidenceApiReadsAuthorizedProjectionFromLargeUtf8AuditJson() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        prepareGuardedToolRun();
        insertSource(1L, "Case source", "published", "verified", 0L);
        insertCase(11L, 1L, "Wuhan AI studio", "verified", 0L);
        AgentToolContext context = new AgentToolContext(31L, 42L, null, 1L, null, null);
        agentToolRegistry.execute(context, 1, "search_cases",
                new ObjectMapper().readTree("{\"limit\":10}"));
        String auditJson = jdbc.queryForObject(
                "SELECT result_summary_json FROM ai_agent_tool_calls WHERE analysis_run_id=31 LIMIT 1",
                String.class);
        var expanded = (com.fasterxml.jackson.databind.node.ObjectNode) new ObjectMapper().readTree(auditJson);
        expanded.put("boundedAuditDetail", "已核验证据".repeat(5000));
        assertTrue(expanded.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16_000);
        jdbc.update("UPDATE ai_agent_tool_calls SET result_summary_json=? WHERE analysis_run_id=31",
                expanded.toString());

        var evidence = agentRunEvidenceService.read(user(), 31L);

        assertEquals(1, evidence.availableCount());
        assertEquals(1, evidence.totalCount());
        assertEquals(11L, evidence.items().get(0).itemId());
    }

    @Test
    void compareCasesToolAcceptsOnlyTwoOrThreePreviouslySearchedCases() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        prepareGuardedToolRun();
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertCase(11L, 1L, "Case A", "verified", 0L);
        insertCase(12L, 1L, "Case B", "verified", 0L);
        insertCase(13L, 1L, "Case C", "verified", 0L);
        insertCase(14L, 1L, "Case outside search", "verified", 0L);
        jdbc.update("UPDATE case_items SET region_id=2 WHERE id=14");
        AgentToolContext context = new AgentToolContext(31L, 42L, null, 1L, null, null);
        agentToolRegistry.execute(
                context, 1, "search_cases", new ObjectMapper().readTree("{\"limit\":10}")
        );

        AgentToolException tooMany = assertThrows(AgentToolException.class, () -> agentToolRegistry.execute(
                context, 2, "compare_cases",
                new ObjectMapper().readTree("{\"caseIds\":[11,12,13,14]}")
        ));
        assertEquals("INVALID_TOOL_ARGUMENTS", tooMany.getDiagnosticCode());
        AgentToolException outside = assertThrows(AgentToolException.class, () -> agentToolRegistry.execute(
                context, 3, "compare_cases",
                new ObjectMapper().readTree("{\"caseIds\":[11,14]}")
        ));
        assertEquals("FORBIDDEN_CASE_ID", outside.getDiagnosticCode());

        var compared = agentToolRegistry.execute(
                context, 4, "compare_cases",
                new ObjectMapper().readTree("{\"caseIds\":[11,12],\"dimensions\":[\"businessModel\",\"evidenceStrength\"]}")
        );
        assertEquals(2, compared.result().output().path("cases").size());
        assertEquals(4, compared.result().output().path("conclusions").size());
        assertTrue(java.util.stream.StreamSupport.stream(
                compared.result().output().path("conclusions").spliterator(), false)
                .allMatch(item -> item.path("sourceId").asLong() == 1L));
    }

    @Test
    void concurrentAtomicStartReplayLeavesOneSessionMessagePairAndRun() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        jdbc.update("""
                UPDATE ai_model_settings
                SET enabled=1, agent_enabled=1, agent_rollout_state='explicitly_enabled',
                    agent_rollout_changed_at=NOW(6), agent_rollout_changed_by_admin_id=1
                WHERE id=1
                """);
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        AgentSessionStartDTO request = new AgentSessionStartDTO();
        request.setContent("Please help me research a startup opportunity");
        request.setIdempotencyKey("idem-concurrent-start");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<com.opc.platform.ai.service.AgentResearchStartReceipt>> tasks = List.of(
                    () -> { start.await(); return agentResearchService.start(owner, request); },
                    () -> { start.await(); return agentResearchService.start(owner, request); }
            );
            List<Future<com.opc.platform.ai.service.AgentResearchStartReceipt>> futures =
                    tasks.stream().map(executor::submit).toList();
            start.countDown();
            var first = futures.get(0).get(15, TimeUnit.SECONDS);
            var second = futures.get(1).get(15, TimeUnit.SECONDS);

            assertEquals(first.session().sessionId(), second.session().sessionId());
            assertEquals(first.messageId(), second.messageId());
            assertEquals(first.runId(), second.runId());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_agent_sessions WHERE user_id=42", Integer.class));
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_agent_messages", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_analysis_runs WHERE task_type='agent_research'", Integer.class));
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT assistant_history_revision FROM platform_users WHERE id=42", Long.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void atomicSessionStartIsRequestBoundAndDoesNotCreateReplayOrphans() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        jdbc.update("INSERT IGNORE INTO regions (id,name,level,parent_id) VALUES (1,'Hubei','province',NULL),(2,'Beijing','province',NULL)");
        jdbc.update("""
                UPDATE ai_model_settings
                SET enabled=1, agent_enabled=1, agent_rollout_state='explicitly_enabled',
                    agent_rollout_changed_at=NOW(6), agent_rollout_changed_by_admin_id=1
                WHERE id=1
                """);
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        AgentSessionStartDTO firstRequest = new AgentSessionStartDTO();
        firstRequest.setProfile(new ObjectMapper().readTree("{\"industry\":\"AI\",\"regionId\":1}"));
        firstRequest.setContent("Please research Hubei AI startup opportunities");
        firstRequest.setIdempotencyKey("idem-atomic-start-1");

        var first = agentResearchService.start(owner, firstRequest);
        AgentSessionStartDTO replay = new AgentSessionStartDTO();
        replay.setProfile(new ObjectMapper().readTree("{\"regionId\":1,\"industry\":\"AI\"}"));
        replay.setContent("Please research Hubei AI startup opportunities");
        replay.setIdempotencyKey("idem-atomic-start-1");
        var second = agentResearchService.start(owner, replay);

        assertEquals(first.session().sessionId(), second.session().sessionId());
        assertEquals(first.messageId(), second.messageId());
        assertEquals(first.runId(), second.runId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_agent_sessions WHERE user_id=42", Integer.class));

        replay.setContent("Different question");
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> agentResearchService.start(owner, replay)).getErrorCode());
        replay.setContent(firstRequest.getContent());
        replay.setProfile(new ObjectMapper().readTree("{\"regionId\":2,\"industry\":\"AI\"}"));
        assertEquals(ErrorCode.CONFLICT,
                assertThrows(BusinessException.class, () -> agentResearchService.start(owner, replay)).getErrorCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_agent_sessions WHERE user_id=42", Integer.class));
    }

    @Test
    void initialTaskContextIsPersistedReadBackAndBoundToIdempotency() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,email,status) VALUES (42,'owner','owner@example.com','active')");
        jdbc.update("""
                UPDATE ai_model_settings
                SET enabled=1, agent_enabled=1, agent_rollout_state='explicitly_enabled',
                    agent_rollout_changed_at=NOW(6), agent_rollout_changed_by_admin_id=1
                WHERE id=1
                """);
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        AgentSessionStartDTO request = new AgentSessionStartDTO();
        request.setContent("Research a constrained startup opportunity");
        request.setIdempotencyKey("idem-task-context-1");
        request.setRequestedIntent("general_research");
        request.setTaskContext(new ObjectMapper().readTree("""
                {"version":"phase3-task-v1","taskType":"general_research",
                 "caseIds":[],"comparisonDimensions":[],"outputDepth":"standard",
                 "constraints":"budget limited"}
                """));

        var first = agentResearchService.start(owner, request);
        Map<String, Object> firstSessionRow = jdbc.queryForMap("""
                SELECT task_context_version, CAST(task_context_json AS CHAR) AS task_context_json,
                       task_context_hash
                FROM ai_agent_sessions WHERE id=?
                """, first.session().sessionId());
        assertTrue(String.valueOf(firstSessionRow.get("task_context_hash")).length() >= 32);
        Map<String, Object> sessionRow = jdbc.queryForMap("""
                SELECT task_context_version, CAST(task_context_json AS CHAR) AS task_context_json,
                       task_context_hash
                FROM ai_agent_sessions WHERE id=?
                """, first.session().sessionId());
        assertEquals("phase3-task-v1", sessionRow.get("task_context_version"));
        assertTrue(String.valueOf(sessionRow.get("task_context_json")).contains("general_research"));
        assertEquals("general_research", first.taskType());
        assertEquals(sessionRow.get("task_context_hash"), first.taskContextHash());
        assertEquals("general_research", first.taskContext().path("taskType").asText());
        assertEquals("general_research", agentResearchQueryService.sessionDetail(owner, first.session().sessionId())
                .session().taskContext().path("taskType").asText());
        assertEquals(sessionRow.get("task_context_hash"), agentResearchQueryService.run(owner, first.runId())
                .taskContextHash());

        AgentSessionStartDTO replay = new AgentSessionStartDTO();
        replay.setContent(request.getContent());
        replay.setIdempotencyKey(request.getIdempotencyKey());
        replay.setRequestedIntent("general_research");
        replay.setTaskContext(new ObjectMapper().readTree("""
                {"comparisonDimensions":[],"constraints":"budget limited","caseIds":[],
                 "taskType":"general_research","version":"phase3-task-v1","outputDepth":"standard"}
                """));
        var replayed = agentResearchService.start(owner, replay);
        assertEquals(first.runId(), replayed.runId());

        replay.setTaskContext(new ObjectMapper().readTree("""
                {"version":"phase3-task-v1","taskType":"general_research",
                 "caseIds":[],"comparisonDimensions":[],"outputDepth":"deep"}
                """));
        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> agentResearchService.start(owner, replay)).getErrorCode());
    }

    @Test
    void clarificationRunIsZeroTokenAuditedAndIdempotentWithoutCallingProvider() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        jdbc.update("""
                UPDATE ai_model_settings
                SET enabled=1,
                    agent_enabled=1,
                    agent_rollout_state='explicitly_enabled',
                    agent_rollout_changed_at=NOW(6),
                    agent_rollout_changed_by_admin_id=1
                WHERE id=1
                """);
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Clarification", null);
        AgentMessageCreateDTO request = new AgentMessageCreateDTO();
        request.setContent("请帮我研究创业机会");
        request.setIdempotencyKey("idem-clarify-123");

        var first = agentResearchService.submit(owner, session.getId(), request);
        var second = agentResearchService.submit(owner, session.getId(), request);

        assertEquals(first.runId(), second.runId());
        assertEquals("clarification_needed", first.status());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_agent_messages WHERE session_id=?", Integer.class, session.getId()));
        Map<String, Object> run = jdbc.queryForMap(
                "SELECT status,provider,total_tokens,result_json FROM ai_analysis_runs WHERE id=?", first.runId());
        assertEquals("clarification_needed", run.get("status"));
        assertEquals("not_called", run.get("provider"));
        assertEquals(0, ((Number) run.get("total_tokens")).intValue());
        assertTrue(String.valueOf(run.get("result_json")).contains("finalMessageId"));
    }

    @Test
    void disabledAgentRuntimeRejectsClarificationWithoutPersistingConversation() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Disabled runtime", null);
        AgentMessageCreateDTO request = new AgentMessageCreateDTO();
        request.setContent("请帮我研究创业机会");
        request.setIdempotencyKey("idem-disabled-agent");

        BusinessException rejected = assertThrows(
                BusinessException.class,
                () -> agentResearchService.submit(owner, session.getId(), request)
        );

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, rejected.getErrorCode());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_agent_messages WHERE session_id=?", Integer.class, session.getId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_analysis_runs WHERE session_id=?", Integer.class, session.getId()));
    }

    @Test
    void deterministicProviderCompletesMultiRoundRunWithToolAuditAndCitation() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        insertSource(1L, "Policy source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "Hubei support", "verified", 0L);
        jdbc.update("UPDATE policies SET applicability_mode='general' WHERE id=21");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(
                owner, "Agent integration", "{\"regionId\":1,\"industry\":\"AI\"}");
        var userMessage = agentSessionService.appendMessage(
                owner, session.getId(), "user", "Research Hubei AI support", "completed", null, null);
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse(
                        "{\"action\":\"tool\",\"toolName\":\"search_policies\",\"arguments\":{\"regionId\":1,\"industry\":\"AI\",\"limit\":5}}",
                        10, 5, 15, 20, "req-1", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Hubei has verified general support.\"," +
                                "\"citations\":[{\"sourceId\":1,\"claim\":\"The policy supports the conclusion.\"}],\"confidence\":0.8}",
                        11, 4, 15, 18, "req-2", "stop")
        ));
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "fake", "openai_compatible", "https://api.example.com/v1", "fake-agent", "test-key",
                0.2, 1200, java.time.Duration.ofSeconds(20), 0, true);
        AiClient fakeClient = new AiClient() {
            public AiProviderResponse generate(AiProviderRequest request) { return responses.removeFirst(); }
            public AiProviderDescriptor descriptor() { return new AiProviderDescriptor("fake", "fake-agent", true); }
        };
        AiRuntimeSettingsProvider runtimeProvider = new AiRuntimeSettingsProvider() {
            public AiRuntimeSettings current() { return runtime; }
            public long dailyTokenQuota() { return 100_000L; }
        };
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(runMapper, fakeClient, runtimeProvider);
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, java.time.Duration.ofSeconds(120), "json_plan");
        var lease = lifecycle.begin(owner, session.getId(), userMessage.getId(), "idem-agent-123", config);
        assertEquals(1, jdbc.update(
                "UPDATE ai_agent_messages SET run_id=? WHERE id=?", lease.run().getId(), userMessage.getId()));
        AgentRunFinalizer finalizer = new AgentRunFinalizer(
                runMapper, agentSessionService, lifecycle, new ObjectMapper());
        AgentResearchWorker worker = new AgentResearchWorker(
                agentSessionService, agentOrchestrator, lifecycle, finalizer, sourceMapper, new ObjectMapper());

        worker.execute(lease, owner, session.getProfileJson(), userMessage.getContent());

        Map<String, Object> run = jdbc.queryForMap(
                "SELECT status,prompt_tokens,completion_tokens,total_tokens,step_count,tool_call_count,finish_reason,provider_request_id FROM ai_analysis_runs WHERE id=?",
                lease.run().getId());
        assertEquals("completed", run.get("status"));
        assertEquals(21, ((Number) run.get("prompt_tokens")).intValue());
        assertEquals(9, ((Number) run.get("completion_tokens")).intValue());
        assertEquals(30, ((Number) run.get("total_tokens")).intValue());
        assertEquals(2, ((Number) run.get("step_count")).intValue());
        assertEquals(1, ((Number) run.get("tool_call_count")).intValue());
        assertEquals("stop", run.get("finish_reason"));
        assertEquals("req-2", run.get("provider_request_id"));
        assertEquals("completed", jdbc.queryForObject(
                "SELECT status FROM ai_agent_tool_calls WHERE analysis_run_id=?", String.class, lease.run().getId()));
        assertTrue(jdbc.queryForObject(
                "SELECT citations_json FROM ai_agent_messages WHERE run_id=? AND role='assistant'", String.class,
                lease.run().getId()).contains("Policy source"));
    }

    @Test
    void sameUserCannotStartConcurrentAgentRunsAcrossSessions() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var firstSession = agentSessionService.create(owner, "First run", null);
        var secondSession = agentSessionService.create(owner, "Second run", null);
        var firstMessage = agentSessionService.appendMessage(
                owner, firstSession.getId(), "user", "First question", "completed", null, null);
        var secondMessage = agentSessionService.appendMessage(
                owner, secondSession.getId(), "user", "Second question", "completed", null, null);
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "fake", "openai_compatible", "https://api.example.com/v1", "fake-agent", "test-key",
                0.2, 1200, java.time.Duration.ofSeconds(20), 0, true);
        AiClient fakeClient = new AiClient() {
            public AiProviderResponse generate(AiProviderRequest request) { throw new AssertionError("not called"); }
            public AiProviderDescriptor descriptor() { return new AiProviderDescriptor("fake", "fake-agent", true); }
        };
        AiRuntimeSettingsProvider runtimeProvider = new AiRuntimeSettingsProvider() {
            public AiRuntimeSettings current() { return runtime; }
            public long dailyTokenQuota() { return 100_000L; }
        };
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(runMapper, fakeClient, runtimeProvider);
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, java.time.Duration.ofSeconds(120), "json_plan");
        AtomicInteger successes = new AtomicInteger();

        List<Throwable> failures = runTogether(
                () -> {
                    lifecycle.begin(owner, firstSession.getId(), firstMessage.getId(), "idem-concurrent-a", config);
                    successes.incrementAndGet();
                    return null;
                },
                () -> {
                    lifecycle.begin(owner, secondSession.getId(), secondMessage.getId(), "idem-concurrent-b", config);
                    successes.incrementAndGet();
                    return null;
                }
        );

        assertEquals(1, successes.get());
        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof BusinessException);
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ((BusinessException) failures.get(0)).getErrorCode());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_analysis_runs WHERE user_id=42 AND status='running'", Integer.class));
    }

    @Test
    void evidenceChangeAfterToolSearchPreventsAgentCompletion() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        insertSource(1L, "Policy source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "Hubei support", "verified", 0L);
        jdbc.update("UPDATE policies SET applicability_mode='general' WHERE id=21");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(
                owner, "Changing evidence", "{\"regionId\":1,\"industry\":\"AI\"}");
        var userMessage = agentSessionService.appendMessage(
                owner, session.getId(), "user", "Research Hubei AI support", "completed", null, null);
        AtomicInteger round = new AtomicInteger();
        AiClient fakeClient = new AiClient() {
            public AiProviderResponse generate(AiProviderRequest request) {
                if (round.incrementAndGet() == 1) {
                    return new AiProviderResponse(
                            "{\"action\":\"tool\",\"toolName\":\"search_policies\",\"arguments\":{\"regionId\":1,\"industry\":\"AI\",\"limit\":5}}",
                            10, 5, 15, 20, "req-change-1", "stop");
                }
                jdbc.update("UPDATE policies SET evidence_revision=evidence_revision+1 WHERE id=21");
                return new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Hubei has verified general support.\"," +
                                "\"citations\":[{\"sourceId\":1,\"claim\":\"The policy supports the conclusion.\"}],\"confidence\":0.8}",
                        11, 4, 15, 18, "req-change-2", "stop");
            }
            public AiProviderDescriptor descriptor() { return new AiProviderDescriptor("fake", "fake-agent", true); }
        };
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "fake", "openai_compatible", "https://api.example.com/v1", "fake-agent", "test-key",
                0.2, 1200, java.time.Duration.ofSeconds(20), 0, true);
        AiRuntimeSettingsProvider runtimeProvider = new AiRuntimeSettingsProvider() {
            public AiRuntimeSettings current() { return runtime; }
            public long dailyTokenQuota() { return 100_000L; }
        };
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(runMapper, fakeClient, runtimeProvider);
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, java.time.Duration.ofSeconds(120), "json_plan");
        var lease = lifecycle.begin(owner, session.getId(), userMessage.getId(), "idem-evidence-change", config);
        assertEquals(1, jdbc.update(
                "UPDATE ai_agent_messages SET run_id=? WHERE id=?", lease.run().getId(), userMessage.getId()));
        AgentRunFinalizer finalizer = new AgentRunFinalizer(
                runMapper, agentSessionService, lifecycle, new ObjectMapper());
        AgentResearchWorker worker = new AgentResearchWorker(
                agentSessionService, agentOrchestrator, lifecycle, finalizer, sourceMapper, new ObjectMapper());

        worker.execute(lease, owner, session.getProfileJson(), userMessage.getContent());

        Map<String, Object> failed = jdbc.queryForMap(
                "SELECT status,diagnostic_code,step_count,tool_call_count FROM ai_analysis_runs WHERE id=?",
                lease.run().getId());
        assertEquals("failed", failed.get("status"));
        assertEquals("EVIDENCE_CHANGED", failed.get("diagnostic_code"));
        assertEquals(2, ((Number) failed.get("step_count")).intValue());
        assertEquals(1, ((Number) failed.get("tool_call_count")).intValue());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_agent_messages WHERE run_id=? AND role='assistant'",
                Integer.class, lease.run().getId()));
    }

    @Test
    void concurrentAgentMessagesKeepUniqueStableSequenceNumbers() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Concurrent messages", null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> { start.await(); return agentSessionService.appendMessage(
                            owner, session.getId(), "user", "Question A", "completed", null, null).getSequenceNo(); },
                    () -> { start.await(); return agentSessionService.appendMessage(
                            owner, session.getId(), "user", "Question B", "completed", null, null).getSequenceNo(); }
            );
            List<Future<Integer>> futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            List<Integer> sequences = new ArrayList<>();
            for (Future<Integer> future : futures) sequences.add(future.get(10, TimeUnit.SECONDS));
            sequences.sort(Integer::compareTo);
            assertEquals(List.of(1, 2), sequences);
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT sequence_no) FROM ai_agent_messages WHERE session_id=?",
                    Integer.class, session.getId()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void otherUserCannotCancelOwnedRun() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active'),(43,'other','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        AuthenticatedUser other = new AuthenticatedUser(43L, "other", "other@example.com");
        var session = agentSessionService.create(owner, "Owned run", null);
        var message = agentSessionService.appendMessage(owner, session.getId(), "user", "Question", "completed", null, null);
        Long runId = insertRunningAgentRun(session.getId(), message.getId(), "idem-owned-123", 'a');

        BusinessException denied = assertThrows(BusinessException.class,
                () -> agentResearchQueryService.cancel(other, runId));
        assertEquals(ErrorCode.NOT_FOUND, denied.getErrorCode());
        assertEquals("running", jdbc.queryForObject(
                "SELECT status FROM ai_analysis_runs WHERE id=?", String.class, runId));
        assertEquals("cancelled", agentResearchQueryService.cancel(owner, runId).status());
    }

    @Test
    void cancelledAgentRunRejectsLateCompletionSettlement() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Cancellation race", null);
        var message = agentSessionService.appendMessage(owner, session.getId(), "user", "Question", "completed", null, null);
        Long runId = insertRunningAgentRun(session.getId(), message.getId(), "idem-race-123", 'b');

        agentResearchQueryService.cancel(owner, runId);
        assertEquals(0, runMapper.settleAgentCompleted(
                runId, "completed", 10, 5, 15, 20, "late-request", "stop", 2, 1,
                "{\"citationCount\":1}", null, LocalDateTime.now()));
        assertEquals("cancelled", jdbc.queryForObject(
                "SELECT status FROM ai_analysis_runs WHERE id=?", String.class, runId));
    }

    @Test
    void completedAgentRunPersistsDiagnosticCodeForAdminObservation() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Diagnostic persistence", null);
        var message = agentSessionService.appendMessage(owner, session.getId(), "user", "Question", "completed", null, null);
        Long runId = insertRunningAgentRun(session.getId(), message.getId(), "idem-diagnostic-123", 'e');

        assertEquals(1, runMapper.settleAgentCompleted(
                runId, "completed", 20, 10, 30, 50, "fallback-request", "length", 2, 1,
                "{\"diagnosticCode\":\"FINAL_RESPONSE_TRUNCATED_FALLBACK\"}",
                AgentResearchContract.FINAL_RESPONSE_TRUNCATED_FALLBACK, LocalDateTime.now()));
        assertEquals(AgentResearchContract.FINAL_RESPONSE_TRUNCATED_FALLBACK,
                jdbc.queryForObject("SELECT diagnostic_code FROM ai_analysis_runs WHERE id=?",
                        String.class, runId));
    }

    @Test
    void agentToolCallInsertRollsBackWithItsTransaction() throws Exception {
        createAgentUserTable();
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,status,provider,model_id,prompt_version,evidence_hash)
                VALUES (31,42,'agent_research','completed','fake','fake','agent-v1',REPEAT('c',64))
                """);

        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            AiAgentToolCall call = new AiAgentToolCall();
            call.setAnalysisRunId(31L);
            call.setStepNo(1);
            call.setToolName("search_cases");
            call.setArgumentsJson("{}");
            call.setStatus("pending");
            call.setEvidenceCount(0);
            call.setLatencyMs(0L);
            agentToolCallMapper.insert(call);
            throw new IllegalStateException("force rollback");
        }));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_agent_tool_calls WHERE analysis_run_id=31", Integer.class));
    }

    @Test
    void expiredAgentCleanupUsesExpiredTerminalState() throws Exception {
        createAgentUserTable();
        runAgentRuntimeWorkspaceMigrations();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Expired run", null);
        var message = agentSessionService.appendMessage(owner, session.getId(), "user", "Question", "completed", null, null);
        Long runId = insertRunningAgentRun(session.getId(), message.getId(), "idem-expired-123", 'd');
        jdbc.update("UPDATE ai_analysis_runs SET deadline_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?", runId);

        assertEquals(1, runMapper.failExpiredRunning(LocalDateTime.now()));
        Map<String, Object> expired = jdbc.queryForMap(
                "SELECT status,current_stage,diagnostic_code,reserved_tokens,completed_at FROM ai_analysis_runs WHERE id=?",
                runId);
        assertEquals("expired", expired.get("status"));
        assertEquals("expired", expired.get("current_stage"));
        assertEquals("AGENT_TIMEOUT", expired.get("diagnostic_code"));
        assertEquals(0L, ((Number) expired.get("reserved_tokens")).longValue());
        assertNotNull(expired.get("completed_at"));
    }

    @Test
    void finalizationMigrationIsRepeatableAndBothForeignKeysRestrictDeletes() throws Exception {
        runFinalizationMigration();
        runFinalizationMigration();

        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name IN ('fk_case_items_source', 'fk_policies_source')
                """, Integer.class));

        insertSource(1L, "Official source", "published", "verified", 0L);
        insertCase(11L, 1L, "Case A", "legacy_unverified", 0L);
        insertPolicy(21L, 1L, "Policy A", "legacy_unverified", 0L);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM sources WHERE id = 1"));
    }

    @Test
    void policyApplicabilityMigrationIsRepeatableAndKeepsLegacyPoliciesUnclassified() throws Exception {
        insertSource(1L, "Official source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "Legacy policy", "verified", 0L);

        runPolicyApplicabilityMigration();
        runPolicyApplicabilityMigration();

        assertEquals("unclassified", jdbc.queryForObject(
                "SELECT applicability_mode FROM policies WHERE id=21", String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name='policy_industry_tags'
                """, Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.referential_constraints
                WHERE constraint_schema=DATABASE()
                  AND constraint_name IN ('fk_policy_industry_policy', 'fk_policy_industry_tag')
                """, Integer.class));
    }

    @Test
    void chinaSoftwareReadinessSelectsVerifiedGeneralPolicyWithoutLegacyPolicyTagRelation() {
        seedPolicyApplicabilityTaxonomy();
        insertSource(1L, "National policy source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "General startup support", "verified", 0L);
        jdbc.update("UPDATE policies SET region_id=1, applicability_mode='general' WHERE id=21");

        var readiness = entrepreneurshipEvidenceService.readiness(readinessRequest(1L, 701L, "软件开发"), false);

        assertEquals(1, readiness.getVerifiedPolicyCount());
        assertEquals("partial", readiness.getReadinessStatus());
    }

    @Test
    void hubeiSoftwareReadinessUsesLocalGeneralPolicyAndExcludesOtherProvincePolicy() {
        seedPolicyApplicabilityTaxonomy();
        insertSource(1L, "Hubei policy source", "published", "verified", 0L);
        insertSource(2L, "Beijing policy source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "Hubei general support", "verified", 0L);
        insertPolicy(22L, 2L, "Beijing general support", "verified", 0L);
        jdbc.update("UPDATE policies SET region_id=2, applicability_mode='general' WHERE id=21");
        jdbc.update("UPDATE policies SET region_id=3, applicability_mode='general' WHERE id=22");

        var readiness = entrepreneurshipEvidenceService.readiness(readinessRequest(2L, 701L, "软件开发"), false);

        assertEquals(1, readiness.getVerifiedPolicyCandidateCount());
        assertEquals(1, readiness.getRegionMatchedPolicyCount());
        assertEquals(0, readiness.getDirectIndustryPolicyCount());
        assertEquals(1, readiness.getGeneralPolicyCount());
        assertEquals(1, readiness.getSelectedPolicyCount());
        assertTrue(readiness.getReasons().contains("当前行业暂无直接匹配政策"));
        assertTrue(readiness.getReasons().contains("当前地区有通用创业政策可参考"));
    }

    @Test
    void regionFirstPolicySelectionUsesIndustryAsAuxiliaryAndDoesNotMislabelUnclassifiedPolicy() {
        seedPolicyApplicabilityTaxonomy();
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "General support", "verified", 0L);
        insertPolicy(22L, 1L, "Software-specific support", "verified", 0L);
        insertPolicy(23L, 1L, "Unclassified software wording", "verified", 0L);
        jdbc.update("UPDATE policies SET region_id=2, applicability_mode='general' WHERE id=21");
        jdbc.update("UPDATE policies SET region_id=1, applicability_mode='specific' WHERE id=22");
        jdbc.update("UPDATE policies SET region_id=2, applicability_mode='unclassified', summary='软件开发专项支持' WHERE id=23");
        jdbc.update("INSERT INTO policy_industry_tags(policy_id,industry_tag_id) VALUES (22,701)");

        var assessment = entrepreneurshipEvidenceService.assess(readinessRequest(2L, 701L, "软件开发"), false);
        var readiness = entrepreneurshipEvidenceService.readiness(readinessRequest(2L, 701L, "软件开发"), false);

        assertEquals(List.of(21L, 23L, 22L), assessment.policies().stream().map(item -> item.item().getId()).toList());
        assertTrue(assessment.policies().stream()
                .filter(item -> item.item().getId().equals(23L))
                .allMatch(item -> item.matchReason().contains("行业适用性未分类")));
        assertEquals(1, readiness.getDirectIndustryPolicyCount());
        assertEquals(1, readiness.getGeneralPolicyCount());
        assertEquals(1, readiness.getUnclassifiedPolicyCount());
        assertEquals(3, readiness.getSelectedPolicyCount());
        assertTrue(readiness.getReasons().stream().anyMatch(reason -> reason.contains("尚未完成行业适用性分类")));
    }

    @Test
    void unverifiedPolicySourceIsRejectedAndIndustryUsageCountsExplicitRelationsOnly() {
        seedPolicyApplicabilityTaxonomy();
        insertSource(1L, "Pending source", "published", "legacy_unverified", 0L);
        insertSource(2L, "Verified source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "Rejected specific policy", "verified", 0L);
        insertPolicy(22L, 2L, "Accepted specific policy", "verified", 0L);
        jdbc.update("UPDATE policies SET region_id=2, applicability_mode='specific' WHERE id IN (21,22)");
        jdbc.update("INSERT INTO policy_industry_tags(policy_id,industry_tag_id) VALUES (21,701),(22,701)");
        jdbc.update("INSERT INTO tags (id,name,tag_type,is_industry,sort_order) VALUES (702,'资金补贴','policy',0,2)");
        jdbc.update("INSERT INTO policy_tags(policy_id,tag_id) VALUES (21,702)");

        var readiness = entrepreneurshipEvidenceService.readiness(readinessRequest(2L, 701L, "软件开发"), false);
        var industry = industryTagService.listIndustries().stream()
                .filter(item -> item.tagId().equals(701L))
                .findFirst().orElseThrow();

        assertEquals(2, readiness.getVerifiedPolicyCandidateCount());
        assertEquals(1, readiness.getSourceRejectedPolicyCount());
        assertEquals(1, readiness.getDirectIndustryPolicyCount());
        assertEquals(1, readiness.getSelectedPolicyCount());
        assertEquals(2, industry.policyUsageCount());
    }

    @Test
    void batchIndustryClassificationIsAtomicAndReturnsVerifiedPoliciesToReview() {
        seedPolicyApplicabilityTaxonomy();
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "Policy one", "verified", 0L);
        insertPolicy(22L, 1L, "Policy two", "verified", 0L);
        PolicyApplicabilityBatchDTO batch = new PolicyApplicabilityBatchDTO();
        batch.setApplicabilityMode("specific");
        batch.setIndustryTagIds(List.of(701L));
        batch.setItems(List.of(applicabilityItem(21L), applicabilityItem(22L)));

        policyService.updateApplicabilityBatch(batch, admin());

        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM policies WHERE applicability_mode='specific' AND ai_evidence_status='legacy_unverified'",
                Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM policy_industry_tags WHERE industry_tag_id=701", Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_evidence_reviews WHERE item_type='policy' AND action_type='content_invalidated'",
                Integer.class));
    }

    @Test
    void stalePolicyInApplicabilityBatchLeavesEveryPolicyUnchanged() {
        seedPolicyApplicabilityTaxonomy();
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertPolicy(21L, 1L, "Policy one", "verified", 0L);
        insertPolicy(22L, 1L, "Policy two", "verified", 0L);
        PolicyApplicabilityBatchItemDTO stale = applicabilityItem(22L);
        stale.setExpectedUpdatedAt(SNAPSHOT_TIME.minusSeconds(1));
        PolicyApplicabilityBatchDTO batch = new PolicyApplicabilityBatchDTO();
        batch.setApplicabilityMode("specific");
        batch.setIndustryTagIds(List.of(701L));
        batch.setItems(List.of(applicabilityItem(21L), stale));

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> policyService.updateApplicabilityBatch(batch, admin()));

        assertEquals(ErrorCode.CONFLICT, conflict.getErrorCode());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM policies WHERE applicability_mode='unclassified' AND ai_evidence_status='verified'",
                Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM policy_industry_tags", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_evidence_reviews", Integer.class));
    }

    @Test
    void orphanedRowsAbortTheForeignKeyMigrationWithoutDeletingHistory() {
        jdbc.update("INSERT INTO case_items (id,title,region_id,category,source_id,summary,accessed_at,status,ai_evidence_status,evidence_revision,updated_at) VALUES (11,'Orphan',1,'software',999,'Summary','2026-07-25','published','legacy_unverified',0,?)", SNAPSHOT_TIME);

        assertThrows(Exception.class, this::runFinalizationMigration);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM case_items WHERE id = 11", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name IN ('fk_case_items_source', 'fk_policies_source')
                """, Integer.class));
    }

    @Test
    void sourceDeleteRacingWithCaseOrPolicyCreateNeverLeavesAnOrphan() throws Exception {
        for (String childType : List.of("case", "policy")) {
            resetDatabase();
            runFinalizationMigration();
            insertSource(1L, "Racing source", "published", "legacy_unverified", 0L);
            List<Throwable> failures = runTogether(
                    () -> {
                        sourceService.deleteSource(1L, 0L, SNAPSHOT_TIME);
                        return null;
                    },
                    () -> {
                        if ("case".equals(childType)) caseItemService.createCaseItem(caseCreate(1L));
                        else policyService.createPolicy(policyCreate(1L));
                        return null;
                    }
            );
            assertTrue(failures.size() <= 1, "Only one racing operation may be rejected");
            assertEquals(0, orphanCount(childType));
        }
    }

    @Test
    void editAndApprovalRaceUsesSnapshotsAndOldUpdateAndDeleteAreRejected() throws Exception {
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertCase(11L, 1L, "Concurrent case", "legacy_unverified", 0L);
        CaseItemUpdateDTO edit = caseUpdate(1L, "Edited case", 0L, SNAPSHOT_TIME);
        EvidenceReviewUpdateDTO approval = reviewUpdate("legacy_unverified", 0L, SNAPSHOT_TIME, "verified");

        List<Throwable> failures = runTogether(
                () -> caseItemService.updateCaseItem(11L, edit, admin()),
                () -> evidenceReviewService.review("case", 11L, approval, admin())
        );

        assertEquals(1, failures.size());
        assertTrue(failures.get(0) instanceof BusinessException);
        assertEquals(ErrorCode.CONFLICT, ((BusinessException) failures.get(0)).getErrorCode());
        assertThrows(BusinessException.class,
                () -> caseItemService.updateCaseItem(11L, edit, admin()));
        BusinessException deleteConflict = assertThrows(BusinessException.class,
                () -> caseItemService.deleteCaseItem(11L, 0L, SNAPSHOT_TIME));
        assertEquals(ErrorCode.CONFLICT, deleteConflict.getErrorCode());
    }

    @Test
    void sourceAndChildEditsCompleteWithoutReverseLockDeadlock() throws Exception {
        insertSource(1L, "Source before edit", "published", "legacy_unverified", 0L);
        insertCase(11L, 1L, "Case before edit", "legacy_unverified", 0L);
        SourceUpdateDTO sourceUpdate = sourceUpdate("Source after edit", 0L, SNAPSHOT_TIME);
        CaseItemUpdateDTO caseUpdate = caseUpdate(1L, "Case after edit", 0L, SNAPSHOT_TIME);

        List<Throwable> failures = runTogether(
                () -> sourceService.updateSource(1L, sourceUpdate, admin()),
                () -> caseItemService.updateCaseItem(11L, caseUpdate, admin())
        );

        assertTrue(failures.isEmpty(), "Unified source-first locking should avoid deadlocks: " + failures);
    }

    @Test
    void childCasConflictRollsBackTheWholeSourceTransaction() {
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertCase(11L, 1L, "Verified case", "verified", 1L);

        BusinessException conflict = assertThrows(BusinessException.class, () -> transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE sources SET ai_evidence_status='excluded', evidence_revision=1 WHERE id=1 AND evidence_revision=0");
            int affected = jdbc.update("UPDATE case_items SET ai_evidence_status='legacy_unverified' WHERE id=11 AND evidence_revision=0");
            if (affected != 1) throw new BusinessException(ErrorCode.CONFLICT, "Concurrent child evidence change");
        }));

        assertEquals(ErrorCode.CONFLICT, conflict.getErrorCode());
        assertEquals("verified", jdbc.queryForObject("SELECT ai_evidence_status FROM sources WHERE id=1", String.class));
    }

    @Test
    void batchDowngradeIsInputOrderIndependentForExcludedAndLegacyTargets() {
        for (String targetStatus : List.of("excluded", "legacy_unverified")) {
            for (boolean sourceFirst : List.of(true, false)) {
                resetDatabase();
                insertSource(1L, "Verified source", "published", "verified", 0L);
                insertCase(11L, 1L, "Verified case", "verified", 0L);
                EvidenceReviewBatchUpdateDTO batch = batchDowngrade(targetStatus, sourceFirst);

                var result = evidenceReviewService.reviewBatch(batch, admin());

                assertEquals(2, result.getProcessedCount());
                assertEquals(targetStatus, jdbc.queryForObject("SELECT ai_evidence_status FROM sources WHERE id=1", String.class));
                assertEquals(targetStatus, jdbc.queryForObject("SELECT ai_evidence_status FROM case_items WHERE id=11", String.class));
            }
        }
    }

    @Test
    void queueSortsTheWholeMixedSetBeforeCrossTypePagination() {
        seedMixedQueue();
        Map<String, List<String>> expected = Map.of(
                "title_asc", List.of("Alpha case", "Bravo policy", "Charlie source", "Delta case", "Echo policy", "Zulu source"),
                "title_desc", List.of("Zulu source", "Echo policy", "Delta case", "Charlie source", "Bravo policy", "Alpha case"),
                "updated_asc", List.of("Alpha case", "Bravo policy", "Charlie source", "Delta case", "Echo policy", "Zulu source"),
                "updated_desc", List.of("Zulu source", "Echo policy", "Delta case", "Charlie source", "Bravo policy", "Alpha case")
        );
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            EvidenceReviewQueryDTO query = new EvidenceReviewQueryDTO();
            query.setSort(entry.getKey());
            query.setSize(2);
            List<String> actual = new ArrayList<>();
            for (int page = 0; page < 3; page++) {
                queueMapper.selectPage(query, 2, page * 2).forEach(row -> actual.add(row.getTitle()));
            }
            assertEquals(entry.getValue(), actual, entry.getKey());
        }
    }

    @Test
    void sourceUrlReviewabilityIsConsistentAcrossQueueDetailPreflightAndSubmission() {
        Map<String, Boolean> corpus = new java.util.LinkedHashMap<>();
        corpus.put("https://example.gov.cn/notice?id=1#detail", true);
        corpus.put(" http://news.example.com/path ", true);
        corpus.put("ftp://example.gov.cn/file", false);
        corpus.put("https://user@example.gov.cn/path", false);
        corpus.put("https://example.gov.cn:8443/path", false);
        corpus.put("https://example.gov.cn/path with space", false);
        corpus.put("https:///missing-host", false);
        corpus.put("https://example..gov.cn/path", false);
        corpus.put("https://-bad.example/path", false);
        corpus.put("https://bad-.example/path", false);
        corpus.put("example.gov.cn/no-scheme", false);
        corpus.put("", false);

        for (Map.Entry<String, Boolean> entry : corpus.entrySet()) {
            resetDatabase();
            jdbc.update("INSERT INTO sources (id,title,source_type,publisher,url,accessed_at,status,ai_evidence_status,evidence_revision,updated_at) VALUES (1,'URL source','web','Official publisher',?,'2026-07-25','published','legacy_unverified',0,?)",
                    entry.getKey(), SNAPSHOT_TIME);

            EvidenceReviewQueryDTO query = new EvidenceReviewQueryDTO();
            query.setItemType("source");
            query.setSize(20);
            boolean queueReviewable = queueMapper.selectPage(query, 20, 0).get(0).isReviewable();
            var detail = evidenceReviewService.detail("source", 1L);
            EvidenceReviewBatchUpdateDTO batch = new EvidenceReviewBatchUpdateDTO();
            batch.setEvidenceStatus("verified");
            batch.setItems(List.of(batchItem("source", 1L, "legacy_unverified")));
            var preflight = evidenceReviewService.preflight(batch);

            assertEquals(entry.getValue(), queueReviewable, "queue: " + entry.getKey());
            assertEquals(entry.getValue(), detail.isReviewable(), "detail: " + entry.getKey());
            assertEquals(entry.getValue(), preflight.getItems().get(0).isAllowed(), "preflight: " + entry.getKey());
            if (entry.getValue()) {
                assertEquals("verified", evidenceReviewService.review(
                        "source", 1L,
                        reviewUpdate("legacy_unverified", 0L, SNAPSHOT_TIME, "verified"), admin()
                ).getEvidenceStatus(), "submission: " + entry.getKey());
            } else {
                BusinessException blocked = assertThrows(BusinessException.class, () -> evidenceReviewService.review(
                        "source", 1L,
                        reviewUpdate("legacy_unverified", 0L, SNAPSHOT_TIME, "verified"), admin()
                ), "submission: " + entry.getKey());
                assertEquals(ErrorCode.BAD_REQUEST, blocked.getErrorCode(), "submission: " + entry.getKey());
            }
        }
    }

    @Test
    void aiSettleHonorsDeadlineAndCleanupWinsAgainstLateResponses() throws Exception {
        insertRun(1L, SNAPSHOT_TIME.plusMinutes(1));
        assertEquals(1, runMapper.settle(1L, "completed", null, null, 3, 2, 5, 10,
                "req-1", "stop", "hash-1", null, SNAPSHOT_TIME));
        assertEquals("completed", jdbc.queryForObject("SELECT status FROM ai_analysis_runs WHERE id=1", String.class));
        assertEquals("stop", jdbc.queryForObject("SELECT finish_reason FROM ai_analysis_runs WHERE id=1", String.class));
        assertEquals("hash-1", jdbc.queryForObject("SELECT response_hash FROM ai_analysis_runs WHERE id=1", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_runs WHERE id=1 AND result_json IS NOT NULL", Integer.class));

        insertRun(2L, SNAPSHOT_TIME.minusSeconds(1));
        assertEquals(0, runMapper.settle(2L, "completed", null, null, 3, 2, 5, 10,
                "req-2", "stop", "hash-2", null, SNAPSHOT_TIME));
        assertEquals(1, runMapper.failExpiredRun(2L, SNAPSHOT_TIME));
        assertTimeoutState(2L);

        insertRun(3L, SNAPSHOT_TIME.minusSeconds(1));
        List<Throwable> failures = runTogether(
                () -> {
                    runMapper.settle(3L, "completed", null, null, 3, 2, 5, 10,
                            "req-3", "stop", "hash-3", null, SNAPSHOT_TIME);
                    return null;
                },
                () -> {
                    runMapper.failExpiredRunning(SNAPSHOT_TIME);
                    return null;
                }
        );
        assertTrue(failures.isEmpty());
        assertTimeoutState(3L);
    }

    @Test
    void aiResponseDiagnosticsMigrationIsRepeatable() throws Exception {
        runAiResponseDiagnosticsMigration();
        runAiResponseDiagnosticsMigration();

        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'ai_analysis_runs'
                  AND column_name IN ('finish_reason', 'response_hash', 'diagnostic_code')
                """, Integer.class));
    }

    @Test
    void agentMultiroundMigrationAddsRequestedIntentRepeatably() throws Exception {
        jdbc.execute("ALTER TABLE ai_analysis_runs "
                + "ADD COLUMN submission_kind VARCHAR(20) NOT NULL DEFAULT 'message' AFTER task_type");
        jdbc.execute("ALTER TABLE ai_analysis_runs DROP COLUMN requested_intent");
        runAgentMultiroundBudgetMigration();
        runAgentMultiroundBudgetMigration();

        Map<String, Object> definition = jdbc.queryForMap("""
                SELECT character_maximum_length AS max_length,
                       is_nullable AS nullable_value,
                       column_default AS default_value
                FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='ai_analysis_runs'
                  AND column_name='requested_intent'
                """);
        assertEquals(40L, ((Number) definition.get("max_length")).longValue());
        assertEquals("NO", definition.get("nullable_value"));
        assertEquals("auto", definition.get("default_value"));
        assertEquals(28000, jdbc.queryForObject(
                "SELECT agent_max_tokens FROM ai_model_settings WHERE id=1", Integer.class));
        assertEquals(5, jdbc.queryForObject(
                "SELECT agent_max_model_rounds FROM ai_model_settings WHERE id=1", Integer.class));
    }

    @Test
    void generationRejectsCasePolicyAndSourceChangesMadeWhileTheModelRuns() {
        for (String changedType : List.of("case", "policy", "source")) {
            resetDatabase();
            insertSource(1L, "Verified source", "published", "verified", 0L);
            insertCase(11L, 1L, "Verified case", "verified", 0L);
            insertPolicy(21L, 1L, "Verified policy", "verified", 0L);
            Runnable invalidator = switch (changedType) {
                case "case" -> () -> jdbc.update("UPDATE case_items SET evidence_revision=1 WHERE id=11");
                case "policy" -> () -> jdbc.update("DELETE FROM policies WHERE id=21");
                default -> () -> jdbc.update("UPDATE sources SET evidence_revision=1 WHERE id=1");
            };
            CaseAnalysisService service = new CaseAnalysisService(
                    caseItemMapper, sourceMapper, policyMapper, runMapper, new ObjectMapper(), fakeExecutionService(invalidator));
            CaseAnalysisRequestDTO request = new CaseAnalysisRequestDTO();
            request.setCaseId(11L);

            BusinessException conflict = assertThrows(BusinessException.class,
                    () -> service.analyze(user(), request));
            assertEquals(ErrorCode.CONFLICT, conflict.getErrorCode(), changedType);
        }
    }

    @Test
    void concurrentEvidenceInsufficientRequestsPersistOnlySafeZeroTokenAudits() throws Exception {
        insertSource(1L, "Pending source", "published", "legacy_unverified", 0L);
        insertCase(11L, 1L, "Pending case", "legacy_unverified", 0L);
        CaseAnalysisService service = new CaseAnalysisService(
                caseItemMapper, sourceMapper, policyMapper, runMapper, new ObjectMapper(), fakeExecutionService(() -> {
                    throw new AssertionError("Provider execution must not run for insufficient evidence");
                }));
        CaseAnalysisRequestDTO request = new CaseAnalysisRequestDTO();
        request.setCaseId(11L);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                futures.add(pool.submit(() -> service.analyze(user(), request)));
            }
            for (Future<?> future : futures) assertNotNull(future.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        int auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_runs", Integer.class);
        assertTrue(auditCount >= 1 && auditCount <= 20);
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_analysis_runs
                WHERE status <> 'evidence_insufficient'
                   OR provider <> 'not_called'
                   OR model_id <> 'not_called'
                   OR total_tokens <> 0
                   OR JSON_UNQUOTE(JSON_EXTRACT(result_json, '$.evidenceStatus')) <> 'insufficient'
                """, Integer.class));
    }

    private void createAgentUserTable() {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
    }

    private Long insertRunningAgentRun(
            Long sessionId,
            Long messageId,
            String idempotencyKey,
            char evidenceHashCharacter
    ) {
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (user_id,task_type,session_id,user_message_id,idempotency_key,status,provider,model_id,
                     prompt_version,evidence_hash,reserved_tokens,deadline_at)
                VALUES (42,'agent_research',?,?,?,'running','fake','fake','agent-v1',
                        REPEAT(?,64),1000,DATE_ADD(NOW(),INTERVAL 2 MINUTE))
                """, sessionId, messageId, idempotencyKey, String.valueOf(evidenceHashCharacter));
        return jdbc.queryForObject("SELECT MAX(id) FROM ai_analysis_runs", Long.class);
    }

    private void prepareGuardedToolRun() {
        jdbc.update("INSERT IGNORE INTO platform_users (id,username,status) VALUES (42,'tool-owner','active')");
        jdbc.update("INSERT INTO ai_agent_sessions (id,user_id,title,title_mode,status,content_generation) VALUES (10,42,'Tool run','manual','active',0)");
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,session_id,status,provider,model_id,prompt_version,evidence_hash,
                     session_content_generation)
                VALUES (31,42,'agent_research',10,'running','fake','fake','agent-v1',REPEAT('a',64),0)
                """);
    }

    private void createBaseSchema() {
        jdbc.execute("CREATE TABLE sources (id BIGINT PRIMARY KEY AUTO_INCREMENT,title VARCHAR(255) NOT NULL,source_type VARCHAR(50) NOT NULL DEFAULT 'web',publisher VARCHAR(255),url VARCHAR(1000),local_file VARCHAR(255),accessed_at DATE NOT NULL,notes TEXT,status VARCHAR(20) NOT NULL,ai_evidence_status VARCHAR(30) NOT NULL DEFAULT 'legacy_unverified',evidence_revision BIGINT NOT NULL DEFAULT 0,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE regions (id BIGINT PRIMARY KEY,name VARCHAR(100) NOT NULL,level VARCHAR(30),parent_id BIGINT,sort_order INT DEFAULT 0,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE case_items (id BIGINT PRIMARY KEY AUTO_INCREMENT,title VARCHAR(255) NOT NULL,region_id BIGINT NOT NULL,category VARCHAR(50) NOT NULL,actor_name VARCHAR(255),source_id BIGINT NOT NULL,summary TEXT NOT NULL,business_model TEXT,ai_tools TEXT,outcome TEXT,tags VARCHAR(500),original_url VARCHAR(1000),local_file VARCHAR(255),accessed_at DATE NOT NULL,status VARCHAR(20) NOT NULL,reviewer VARCHAR(100),ai_evidence_status VARCHAR(30) NOT NULL DEFAULT 'legacy_unverified',evidence_revision BIGINT NOT NULL DEFAULT 0,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE policies (id BIGINT PRIMARY KEY AUTO_INCREMENT,title VARCHAR(255) NOT NULL,region_id BIGINT NOT NULL,issuing_body VARCHAR(255) NOT NULL,document_no VARCHAR(100),publish_date DATE,effective_date DATE,valid_period VARCHAR(100),source_id BIGINT NOT NULL,policy_level VARCHAR(30) NOT NULL,policy_type VARCHAR(50) NOT NULL,applicability_mode VARCHAR(20) NOT NULL DEFAULT 'unclassified',summary TEXT NOT NULL,key_points TEXT,support_measures TEXT,tags VARCHAR(500),original_url VARCHAR(1000),evidence_url VARCHAR(1000),local_file VARCHAR(255),accessed_at DATE NOT NULL,status VARCHAR(20) NOT NULL,reviewer VARCHAR(100),ai_evidence_status VARCHAR(30) NOT NULL DEFAULT 'legacy_unverified',evidence_revision BIGINT NOT NULL DEFAULT 0,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE tags (id BIGINT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(100) NOT NULL,tag_type VARCHAR(20) NOT NULL,is_industry TINYINT(1) NOT NULL DEFAULT 0,sort_order INT NOT NULL DEFAULT 0,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uk_tags_name_type(name,tag_type))");
        jdbc.execute("CREATE TABLE case_tags (id BIGINT PRIMARY KEY AUTO_INCREMENT,case_id BIGINT NOT NULL,tag_id BIGINT NOT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uk_case_tags_case_tag(case_id,tag_id))");
        jdbc.execute("CREATE TABLE policy_tags (id BIGINT PRIMARY KEY AUTO_INCREMENT,policy_id BIGINT NOT NULL,tag_id BIGINT NOT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uk_policy_tags_policy_tag(policy_id,tag_id))");
        jdbc.execute("CREATE TABLE policy_industry_tags (id BIGINT PRIMARY KEY AUTO_INCREMENT,policy_id BIGINT NOT NULL,industry_tag_id BIGINT NOT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uk_policy_industry_policy_tag(policy_id,industry_tag_id))");
        jdbc.execute("CREATE TABLE tag_aliases (id BIGINT PRIMARY KEY AUTO_INCREMENT,tag_id BIGINT NOT NULL,alias VARCHAR(100) NOT NULL,normalized_alias VARCHAR(100) NOT NULL,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uk_tag_aliases_normalized(normalized_alias))");
        jdbc.execute("CREATE TABLE ai_evidence_reviews (id BIGINT PRIMARY KEY AUTO_INCREMENT,item_type VARCHAR(20) NOT NULL,item_id BIGINT NOT NULL,previous_status VARCHAR(30) NOT NULL,new_status VARCHAR(30) NOT NULL,admin_id BIGINT NOT NULL,admin_username VARCHAR(100) NOT NULL,notes VARCHAR(500),action_type VARCHAR(50),reason VARCHAR(500),operation_id VARCHAR(64),created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))");
        jdbc.execute("CREATE TABLE ai_analysis_runs (id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,task_type VARCHAR(40) NOT NULL,case_id BIGINT,session_id BIGINT,user_message_id BIGINT,idempotency_key VARCHAR(64),status VARCHAR(30) NOT NULL,active_guard BIGINT GENERATED ALWAYS AS (CASE WHEN status='running' THEN user_id ELSE NULL END) STORED,session_active_guard BIGINT GENERATED ALWAYS AS (CASE WHEN status='running' THEN session_id ELSE NULL END) STORED,result_json JSON,provider VARCHAR(40) NOT NULL,model_id VARCHAR(191) NOT NULL,prompt_version VARCHAR(60) NOT NULL,evidence_hash CHAR(64) NOT NULL,prompt_tokens INT NOT NULL DEFAULT 0,completion_tokens INT NOT NULL DEFAULT 0,total_tokens INT NOT NULL DEFAULT 0,reserved_tokens BIGINT NOT NULL DEFAULT 0,started_at DATETIME(6),deadline_at DATETIME(6),heartbeat_at DATETIME(6),latency_ms BIGINT NOT NULL DEFAULT 0,provider_request_id VARCHAR(191),finish_reason VARCHAR(40),response_hash CHAR(64),error_type VARCHAR(80),diagnostic_code VARCHAR(80),step_count INT NOT NULL DEFAULT 0,tool_call_count INT NOT NULL DEFAULT 0,current_stage VARCHAR(40),visible_progress VARCHAR(120),cancelled_at DATETIME(6),completed_at DATETIME(6),task_context_version VARCHAR(40) NULL,task_context_json JSON NULL,task_context_hash CHAR(64) NULL,created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),UNIQUE KEY uk_running(active_guard),UNIQUE KEY uk_session_running(session_active_guard),UNIQUE KEY uk_idempotency(user_id,task_type,idempotency_key)) ENGINE=InnoDB");
        jdbc.execute("ALTER TABLE ai_analysis_runs ADD COLUMN requested_intent VARCHAR(40) NOT NULL DEFAULT 'auto' AFTER task_type");
        jdbc.execute("CREATE TABLE ai_model_settings (id BIGINT PRIMARY KEY,provider VARCHAR(40) NOT NULL,api_format VARCHAR(40) NOT NULL,api_base_url VARCHAR(500),model_id VARCHAR(191),model_catalog_json JSON,api_key_ciphertext TEXT,api_key_provider VARCHAR(40),api_key_origin VARCHAR(500),temperature DECIMAL(4,3) NOT NULL,max_output_tokens INT NOT NULL,timeout_seconds INT NOT NULL,retry_count INT NOT NULL,daily_token_quota BIGINT NOT NULL,enabled TINYINT(1) NOT NULL,agent_enabled TINYINT(1) NOT NULL DEFAULT 0,agent_rollout_state VARCHAR(30) NOT NULL DEFAULT 'explicitly_disabled',agent_rollout_changed_at DATETIME(6),agent_rollout_changed_by_admin_id BIGINT,agent_max_model_rounds INT NOT NULL DEFAULT 4,agent_max_tool_calls INT NOT NULL DEFAULT 6,agent_max_tokens INT NOT NULL DEFAULT 8000,agent_history_window INT NOT NULL DEFAULT 12,agent_timeout_seconds INT NOT NULL DEFAULT 120,agent_tool_mode VARCHAR(20) NOT NULL DEFAULT 'json_plan',last_test_status VARCHAR(30) NOT NULL,last_tested_at DATETIME,last_test_message VARCHAR(240),updated_by_admin_id BIGINT,updated_by_admin_username VARCHAR(100),created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ai_model_settings (id,provider,api_format,temperature,max_output_tokens,timeout_seconds,retry_count,daily_token_quota,enabled,agent_enabled,agent_max_model_rounds,agent_max_tool_calls,agent_max_tokens,agent_history_window,agent_timeout_seconds,agent_tool_mode,last_test_status) VALUES (1,'deepseek','openai_compatible',0.2,1200,30,1,100000,0,0,4,6,8000,12,120,'json_plan','not_tested')");
        jdbc.update("INSERT INTO regions (id,name,level,parent_id) VALUES (1,'Hubei','province',NULL)");
    }

    private void runFinalizationMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of("..", "deploy", "sql", "20260725_phase_one_finalization.sql")));
        }
    }

    private void runPolicyApplicabilityMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260725_policy_applicability.sql")));
        }
    }

    private void runAiResponseDiagnosticsMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260725_ai_response_diagnostics.sql")));
        }
    }

    private void runAgentMultiroundBudgetMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260727_agent_multiround_budget.sql")));
        }
    }

    private void runAgentRuntimeMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260725_agent_runtime.sql")));
        }
    }

    private void runAgentRuntimeStabilizationMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260725_agent_runtime_stabilization.sql")));
        }
    }

    private void runAssistantWorkspaceMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260725_assistant_workspace.sql")));
        }
    }

    private void runAgentRuntimeWorkspaceMigrations() throws SQLException {
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        runAssistantWorkspaceMigration();
        runAssistantWorkspaceStabilizationMigration();
        runAssistantHistoryRevisionMigration();
        runAgentMultiroundBudgetMigration();
        runPhaseThreeTaskContextMigration();
        runPhaseThreeAnalyticsSnapshotMigration();
        runPhaseThreeReportsMigration();
        runPhaseThreeFeedbackMigration();
        runPhaseThreePreferencesMigration();
    }

    private void runPhaseThreeTaskContextMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260801_phase_three_task_context.sql")));
        }
    }

    private void runPhaseThreeAnalyticsSnapshotMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260801_phase_three_analytics_snapshots.sql")));
        }
    }

    private void runPhaseThreeReportsMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260801_phase_three_reports.sql")));
        }
    }

    private void runPhaseThreeFeedbackMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260801_phase_three_feedback.sql")));
        }
    }

    private void runPhaseThreePreferencesMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260801_phase_three_preferences.sql")));
        }
    }

    private void runAssistantHistoryRevisionMigration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260726_assistant_history_revision.sql")));
        }
    }

    private void runAssistantWorkspaceStabilizationMigration() throws SQLException {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS app_settings (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  setting_key VARCHAR(120) NOT NULL,
                  setting_value TEXT NULL,
                  `sensitive` TINYINT(1) NOT NULL DEFAULT 0,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_app_settings_key (setting_key)
                ) ENGINE=InnoDB
                """);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    Path.of("..", "deploy", "sql", "20260725_assistant_workspace_stabilization.sql")));
        }
    }

    private void insertSource(Long id, String title, String status, String evidenceStatus, Long revision) {
        jdbc.update("INSERT INTO sources (id,title,source_type,publisher,url,accessed_at,status,ai_evidence_status,evidence_revision,updated_at) VALUES (?,?,'web','Official publisher','https://example.gov.cn/source','2026-07-25',?,?,?,?)",
                id, title, status, evidenceStatus, revision, SNAPSHOT_TIME);
    }

    private void insertCase(Long id, Long sourceId, String title, String evidenceStatus, Long revision) {
        jdbc.update("INSERT INTO case_items (id,title,region_id,category,source_id,summary,accessed_at,status,ai_evidence_status,evidence_revision,updated_at) VALUES (?,?,1,'software',?,'Summary','2026-07-25','published',?,?,?)",
                id, title, sourceId, evidenceStatus, revision, SNAPSHOT_TIME);
    }

    private void insertPolicy(Long id, Long sourceId, String title, String evidenceStatus, Long revision) {
        jdbc.update("INSERT INTO policies (id,title,region_id,issuing_body,source_id,policy_level,policy_type,summary,accessed_at,status,ai_evidence_status,evidence_revision,updated_at) VALUES (?,?,1,'Authority',?,'provincial','comprehensive','Summary','2026-07-25','published',?,?,?)",
                id, title, sourceId, evidenceStatus, revision, SNAPSHOT_TIME);
    }

    private int orphanCount(String type) {
        String table = "case".equals(type) ? "case_items" : "policies";
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " child LEFT JOIN sources s ON s.id=child.source_id WHERE s.id IS NULL", Integer.class);
    }

    private CaseItemCreateDTO caseCreate(Long sourceId) {
        CaseItemCreateDTO dto = new CaseItemCreateDTO();
        dto.setTitle("Created case"); dto.setRegionId(1L); dto.setCategory("software"); dto.setSourceId(sourceId);
        dto.setSummary("Summary"); dto.setAccessedAt(LocalDate.of(2026, 7, 25)); dto.setStatus("published");
        return dto;
    }

    private PolicyCreateDTO policyCreate(Long sourceId) {
        PolicyCreateDTO dto = new PolicyCreateDTO();
        dto.setTitle("Created policy"); dto.setRegionId(1L); dto.setIssuingBody("Authority"); dto.setSourceId(sourceId);
        dto.setPolicyLevel("provincial"); dto.setPolicyType("comprehensive"); dto.setSummary("Summary");
        dto.setAccessedAt(LocalDate.of(2026, 7, 25)); dto.setStatus("published");
        return dto;
    }

    private CaseItemUpdateDTO caseUpdate(Long sourceId, String title, Long revision, LocalDateTime updatedAt) {
        CaseItemUpdateDTO dto = new CaseItemUpdateDTO();
        dto.setTitle(title); dto.setRegionId(1L); dto.setCategory("software"); dto.setSourceId(sourceId);
        dto.setSummary("Summary"); dto.setAccessedAt(LocalDate.of(2026, 7, 25)); dto.setStatus("published");
        dto.setExpectedEvidenceRevision(revision); dto.setExpectedUpdatedAt(updatedAt);
        return dto;
    }

    private SourceUpdateDTO sourceUpdate(String title, Long revision, LocalDateTime updatedAt) {
        SourceUpdateDTO dto = new SourceUpdateDTO();
        dto.setTitle(title); dto.setSourceType("web"); dto.setPublisher("Official publisher");
        dto.setUrl("https://example.gov.cn/source"); dto.setAccessedAt(LocalDate.of(2026, 7, 25)); dto.setStatus("published");
        dto.setExpectedEvidenceRevision(revision); dto.setExpectedUpdatedAt(updatedAt);
        return dto;
    }

    private EvidenceReviewUpdateDTO reviewUpdate(String expected, Long version, LocalDateTime updatedAt, String target) {
        EvidenceReviewUpdateDTO dto = new EvidenceReviewUpdateDTO();
        dto.setEvidenceStatus(target); dto.setExpectedEvidenceStatus(expected); dto.setExpectedVersion(version);
        dto.setExpectedUpdatedAt(updatedAt); return dto;
    }

    private EvidenceReviewBatchUpdateDTO batchDowngrade(String status, boolean sourceFirst) {
        EvidenceReviewBatchItemDTO source = batchItem("source", 1L);
        EvidenceReviewBatchItemDTO item = batchItem("case", 11L);
        EvidenceReviewBatchUpdateDTO dto = new EvidenceReviewBatchUpdateDTO();
        dto.setEvidenceStatus(status); dto.setReason("Integration downgrade"); dto.setCascade(true);
        dto.setItems(sourceFirst ? List.of(source, item) : List.of(item, source));
        return dto;
    }

    private EvidenceReviewBatchItemDTO batchItem(String type, Long id) {
        return batchItem(type, id, "verified");
    }

    private EvidenceReviewBatchItemDTO batchItem(String type, Long id, String expectedStatus) {
        EvidenceReviewBatchItemDTO item = new EvidenceReviewBatchItemDTO();
        item.setItemType(type); item.setItemId(id); item.setExpectedEvidenceStatus(expectedStatus);
        item.setExpectedVersion(0L); item.setExpectedUpdatedAt(SNAPSHOT_TIME); return item;
    }

    private PolicyApplicabilityBatchItemDTO applicabilityItem(Long policyId) {
        PolicyApplicabilityBatchItemDTO item = new PolicyApplicabilityBatchItemDTO();
        item.setPolicyId(policyId);
        item.setExpectedEvidenceRevision(0L);
        item.setExpectedUpdatedAt(SNAPSHOT_TIME);
        return item;
    }

    private void seedMixedQueue() {
        insertSource(1L, "Charlie source", "published", "verified", 0L);
        insertSource(2L, "Zulu source", "published", "verified", 0L);
        insertCase(11L, 1L, "Alpha case", "legacy_unverified", 0L);
        insertCase(12L, 1L, "Delta case", "legacy_unverified", 0L);
        insertPolicy(21L, 1L, "Bravo policy", "legacy_unverified", 0L);
        insertPolicy(22L, 1L, "Echo policy", "legacy_unverified", 0L);
        jdbc.update("UPDATE sources SET updated_at=? WHERE id=1", SNAPSHOT_TIME.plusSeconds(3));
        jdbc.update("UPDATE sources SET updated_at=? WHERE id=2", SNAPSHOT_TIME.plusSeconds(6));
        jdbc.update("UPDATE case_items SET updated_at=? WHERE id=11", SNAPSHOT_TIME.plusSeconds(1));
        jdbc.update("UPDATE case_items SET updated_at=? WHERE id=12", SNAPSHOT_TIME.plusSeconds(4));
        jdbc.update("UPDATE policies SET updated_at=? WHERE id=21", SNAPSHOT_TIME.plusSeconds(2));
        jdbc.update("UPDATE policies SET updated_at=? WHERE id=22", SNAPSHOT_TIME.plusSeconds(5));
    }

    private void insertRun(Long id, LocalDateTime deadline) {
        jdbc.update("INSERT INTO ai_analysis_runs (id,user_id,task_type,case_id,status,provider,model_id,prompt_version,evidence_hash,reserved_tokens,started_at,deadline_at,heartbeat_at) VALUES (?,42,'case_analysis',11,'running','fake','fake','v1',REPEAT('a',64),100,?,?,?)",
                id, SNAPSHOT_TIME.minusMinutes(1), deadline, SNAPSHOT_TIME.minusMinutes(1));
    }

    private void seedPolicyApplicabilityTaxonomy() {
        jdbc.update("DELETE FROM regions");
        jdbc.update("INSERT INTO regions (id,name,level,parent_id) VALUES (1,'中国','country',NULL),(2,'湖北省','province',1),(3,'北京市','province',1)");
        jdbc.update("INSERT INTO tags (id,name,tag_type,is_industry,sort_order) VALUES (701,'软件开发','case',1,1)");
    }

    private com.opc.platform.ai.dto.EntrepreneurshipReadinessRequestDTO readinessRequest(
            Long regionId,
            Long industryTagId,
            String industry
    ) {
        var request = new com.opc.platform.ai.dto.EntrepreneurshipReadinessRequestDTO();
        request.setRegionId(regionId);
        request.setIndustryTagId(industryTagId);
        request.setIndustry(industry);
        return request;
    }

    private void assertTimeoutState(Long id) {
        Map<String, Object> row = jdbc.queryForMap("SELECT status,error_type,reserved_tokens FROM ai_analysis_runs WHERE id=?", id);
        assertEquals("failed", row.get("status"));
        assertEquals("TASK_TIMEOUT", row.get("error_type"));
        assertEquals(0L, ((Number) row.get("reserved_tokens")).longValue());
    }

    private AiTaskExecutionService fakeExecutionService(Runnable invalidator) {
        return new AiTaskExecutionService(null, null, null) {
            @Override
            public <T> T execute(Task task, AiProviderRequest request, Function<Execution, T> resultHandler) {
                invalidator.run();
                AiAnalysisRun run = new AiAnalysisRun(); run.setId(99L);
                AiProviderResponse response = new AiProviderResponse(
                        "{\"summary\":\"Summary\",\"businessModel\":\"Model\",\"technicalAssessment\":\"Assessment\",\"opportunities\":[],\"risks\":[],\"recommendedActions\":[],\"citations\":[{\"sourceId\":1,\"claim\":\"Claim\"}],\"confidence\":0.7}",
                        5, 5, 10, 1, "integration-request");
                return resultHandler.apply(new Execution(run, new AiProviderDescriptor("fake", "fake", true), response));
            }
        };
    }

    private List<Throwable> runTogether(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        List<Callable<Throwable>> wrapped = List.of(first, second).stream().map(task -> (Callable<Throwable>) () -> {
            ready.countDown(); start.await(10, TimeUnit.SECONDS);
            try { task.call(); completed.incrementAndGet(); return null; }
            catch (Throwable error) { return error; }
        }).toList();
        try {
            Future<Throwable> left = pool.submit(wrapped.get(0));
            Future<Throwable> right = pool.submit(wrapped.get(1));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<Throwable> failures = new ArrayList<>();
            Throwable leftFailure = left.get(15, TimeUnit.SECONDS); if (leftFailure != null) failures.add(leftFailure);
            Throwable rightFailure = right.get(15, TimeUnit.SECONDS); if (rightFailure != null) failures.add(rightFailure);
            assertEquals(2, completed.get() + failures.size());
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private AuthenticatedAdmin admin() { return new AuthenticatedAdmin(7L, "integration-admin"); }
    private AuthenticatedUser user() { return new AuthenticatedUser(42L, "integration-user", "user@example.com"); }
}
