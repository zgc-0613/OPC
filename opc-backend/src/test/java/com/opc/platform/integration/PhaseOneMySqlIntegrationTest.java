package com.opc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.CaseAnalysisRequestDTO;
import com.opc.platform.ai.dto.AgentMessageCreateDTO;
import com.opc.platform.ai.dto.EvidenceReviewBatchItemDTO;
import com.opc.platform.ai.dto.EvidenceReviewBatchUpdateDTO;
import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import com.opc.platform.ai.dto.EvidenceReviewUpdateDTO;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.entity.AiAgentToolCall;
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
import com.opc.platform.ai.service.AgentResearchService;
import com.opc.platform.ai.service.AgentResearchQueryService;
import com.opc.platform.ai.service.AgentOrchestrator;
import com.opc.platform.ai.service.AgentResearchWorker;
import com.opc.platform.ai.service.AgentRunQueueService;
import com.opc.platform.ai.service.AgentClarificationPolicy;
import com.opc.platform.ai.service.AgentRunFinalizer;
import com.opc.platform.ai.service.AgentRunLifecycleService;
import com.opc.platform.ai.mapper.AiAgentProviderCallMapper;
import com.opc.platform.ai.service.CaseAnalysisService;
import com.opc.platform.ai.service.EvidenceReviewService;
import com.opc.platform.ai.service.EntrepreneurshipEvidenceService;
import com.opc.platform.ai.tool.AgentToolContext;
import com.opc.platform.ai.tool.AgentToolException;
import com.opc.platform.ai.tool.AgentToolRegistry;
import com.opc.platform.caseitem.dto.CaseItemCreateDTO;
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
        "opc.ai.agent.worker-enabled=false"
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
    @Autowired private AgentToolRegistry agentToolRegistry;
    @Autowired private AgentResearchService agentResearchService;
    @Autowired private AgentResearchQueryService agentResearchQueryService;
    @Autowired private AgentOrchestrator agentOrchestrator;
    @Autowired private AiAgentToolCallMapper agentToolCallMapper;
    @Autowired private AiAgentProviderCallMapper agentProviderCallMapper;
    @Autowired private AgentRunQueueService agentRunQueueService;
    @Autowired private AgentClarificationPolicy agentClarificationPolicy;
    @Autowired private AgentRunLifecycleService agentRunLifecycleService;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            for (String table : List.of("ai_agent_provider_calls", "ai_agent_tool_calls", "ai_agent_messages", "ai_agent_sessions", "platform_users",
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
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
    void agentSessionsEnforceOwnershipArchiveAndStableMessageOrder() throws Exception {
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Durable received run", null);
        var message = agentSessionService.appendMessage(
                owner, session.getId(), "user", "Question", "completed", null, null);
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
        runAgentRuntimeMigration();
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertSource(2L, "Pending source", "published", "legacy_unverified", 0L);
        insertCase(11L, 1L, "Eligible case", "verified", 0L);
        insertCase(12L, 2L, "Bad source chain", "verified", 0L);
        insertCase(13L, 1L, "Pending case", "legacy_unverified", 0L);
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,status,provider,model_id,prompt_version,evidence_hash)
                VALUES (31,42,'agent_research','completed','fake','fake','agent-v1',REPEAT('a',64))
                """);
        AgentToolContext context = new AgentToolContext(31L, 42L);

        var execution = agentToolRegistry.execute(
                context, 1, "search_cases", new ObjectMapper().readTree("{\"regionId\":1,\"limit\":10}")
        );

        assertEquals(1, execution.result().output().path("items").size());
        assertEquals(11L, execution.result().output().path("items").get(0).path("caseId").asLong());
        assertEquals(Set.of(11L), context.allowedCaseIds());
        assertEquals(Set.of(1L), context.allowedSourceIds());
        assertEquals("completed", jdbc.queryForObject(
                "SELECT status FROM ai_agent_tool_calls WHERE analysis_run_id=31", String.class));
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
        runAgentRuntimeMigration();
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
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,status,provider,model_id,prompt_version,evidence_hash)
                VALUES (31,42,'agent_research','completed','fake','fake','agent-v1',REPEAT('a',64))
                """);
        AgentToolContext context = new AgentToolContext(31L, 42L);

        var execution = agentToolRegistry.execute(
                context, 1, "search_policies",
                new ObjectMapper().readTree("{\"regionId\":3,\"industryTagId\":701,\"limit\":10}")
        );

        var items = execution.result().output().path("items");
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
    void getSourceToolCannotReadSourceOutsideCurrentRunResults() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeMigration();
        insertSource(1L, "Allowed source", "published", "verified", 0L);
        insertSource(2L, "Other source", "published", "verified", 0L);
        insertCase(11L, 1L, "Eligible case", "verified", 0L);
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,status,provider,model_id,prompt_version,evidence_hash)
                VALUES (31,42,'agent_research','completed','fake','fake','agent-v1',REPEAT('a',64))
                """);
        AgentToolContext context = new AgentToolContext(31L, 42L);
        agentToolRegistry.execute(
                context, 1, "search_cases", new ObjectMapper().readTree("{\"regionId\":1,\"limit\":10}")
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
    void compareCasesToolAcceptsOnlyTwoOrThreePreviouslySearchedCases() throws Exception {
        jdbc.execute("""
                CREATE TABLE platform_users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(255),
                    status VARCHAR(20) NOT NULL DEFAULT 'active'
                ) ENGINE=InnoDB
                """);
        runAgentRuntimeMigration();
        insertSource(1L, "Verified source", "published", "verified", 0L);
        insertCase(11L, 1L, "Case A", "verified", 0L);
        insertCase(12L, 1L, "Case B", "verified", 0L);
        insertCase(13L, 1L, "Case C", "verified", 0L);
        insertCase(14L, 1L, "Case outside search", "verified", 0L);
        jdbc.update("UPDATE case_items SET region_id=2 WHERE id=14");
        jdbc.update("""
                INSERT INTO ai_analysis_runs
                    (id,user_id,task_type,status,provider,model_id,prompt_version,evidence_hash)
                VALUES (31,42,'agent_research','completed','fake','fake','agent-v1',REPEAT('a',64))
                """);
        AgentToolContext context = new AgentToolContext(31L, 42L);
        agentToolRegistry.execute(
                context, 1, "search_cases", new ObjectMapper().readTree("{\"regionId\":1,\"limit\":10}")
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
    void clarificationRunIsZeroTokenAuditedAndIdempotentWithoutCallingProvider() throws Exception {
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
        jdbc.update("INSERT INTO platform_users (id,username,status) VALUES (42,'owner','active')");
        AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");
        var session = agentSessionService.create(owner, "Cancellation race", null);
        var message = agentSessionService.appendMessage(owner, session.getId(), "user", "Question", "completed", null, null);
        Long runId = insertRunningAgentRun(session.getId(), message.getId(), "idem-race-123", 'b');

        agentResearchQueryService.cancel(owner, runId);
        assertEquals(0, runMapper.settleAgentCompleted(
                runId, "completed", 10, 5, 15, 20, "late-request", "stop", 2, 1,
                "{\"citationCount\":1}", LocalDateTime.now()));
        assertEquals("cancelled", jdbc.queryForObject(
                "SELECT status FROM ai_analysis_runs WHERE id=?", String.class, runId));
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
        runAgentRuntimeMigration();
        runAgentRuntimeStabilizationMigration();
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
        jdbc.execute("CREATE TABLE ai_analysis_runs (id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,task_type VARCHAR(40) NOT NULL,case_id BIGINT,session_id BIGINT,user_message_id BIGINT,idempotency_key VARCHAR(64),status VARCHAR(30) NOT NULL,active_guard BIGINT GENERATED ALWAYS AS (CASE WHEN status='running' THEN user_id ELSE NULL END) STORED,session_active_guard BIGINT GENERATED ALWAYS AS (CASE WHEN status='running' THEN session_id ELSE NULL END) STORED,result_json JSON,provider VARCHAR(40) NOT NULL,model_id VARCHAR(191) NOT NULL,prompt_version VARCHAR(60) NOT NULL,evidence_hash CHAR(64) NOT NULL,prompt_tokens INT NOT NULL DEFAULT 0,completion_tokens INT NOT NULL DEFAULT 0,total_tokens INT NOT NULL DEFAULT 0,reserved_tokens BIGINT NOT NULL DEFAULT 0,started_at DATETIME(6),deadline_at DATETIME(6),heartbeat_at DATETIME(6),latency_ms BIGINT NOT NULL DEFAULT 0,provider_request_id VARCHAR(191),finish_reason VARCHAR(40),response_hash CHAR(64),error_type VARCHAR(80),diagnostic_code VARCHAR(80),step_count INT NOT NULL DEFAULT 0,tool_call_count INT NOT NULL DEFAULT 0,current_stage VARCHAR(40),visible_progress VARCHAR(120),cancelled_at DATETIME(6),completed_at DATETIME(6),created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),UNIQUE KEY uk_running(active_guard),UNIQUE KEY uk_session_running(session_active_guard),UNIQUE KEY uk_idempotency(user_id,task_type,idempotency_key)) ENGINE=InnoDB");
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
