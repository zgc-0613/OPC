package com.opc.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.CaseAnalysisRequestDTO;
import com.opc.platform.ai.dto.EvidenceReviewBatchItemDTO;
import com.opc.platform.ai.dto.EvidenceReviewBatchUpdateDTO;
import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import com.opc.platform.ai.dto.EvidenceReviewUpdateDTO;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.mapper.EvidenceReviewQueueMapper;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.service.AiTaskExecutionService;
import com.opc.platform.ai.service.CaseAnalysisService;
import com.opc.platform.ai.service.EvidenceReviewService;
import com.opc.platform.ai.service.EntrepreneurshipEvidenceService;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.lazy-initialization=true")
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

    @BeforeEach
    void resetDatabase() {
        for (String table : List.of("policy_industry_tags", "case_tags", "policy_tags", "tag_aliases",
                "ai_evidence_reviews", "ai_analysis_runs", "ai_model_settings",
                "case_items", "policies", "tags", "regions", "sources")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        createBaseSchema();
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
                    caseItemMapper, sourceMapper, policyMapper, new ObjectMapper(), fakeExecutionService(invalidator));
            CaseAnalysisRequestDTO request = new CaseAnalysisRequestDTO();
            request.setCaseId(11L);

            BusinessException conflict = assertThrows(BusinessException.class,
                    () -> service.analyze(user(), request));
            assertEquals(ErrorCode.CONFLICT, conflict.getErrorCode(), changedType);
        }
    }

    @Test
    void concurrentEvidenceInsufficientRequestsDoNotPersistZeroTokenRuns() throws Exception {
        insertSource(1L, "Pending source", "published", "legacy_unverified", 0L);
        insertCase(11L, 1L, "Pending case", "legacy_unverified", 0L);
        CaseAnalysisService service = new CaseAnalysisService(
                caseItemMapper, sourceMapper, policyMapper, new ObjectMapper(), fakeExecutionService(() -> {
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
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis_runs", Integer.class));
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
        jdbc.execute("CREATE TABLE ai_analysis_runs (id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,task_type VARCHAR(40) NOT NULL,case_id BIGINT,status VARCHAR(30) NOT NULL,active_guard BIGINT GENERATED ALWAYS AS (CASE WHEN status='running' THEN user_id ELSE NULL END) STORED,result_json JSON,provider VARCHAR(40) NOT NULL,model_id VARCHAR(191) NOT NULL,prompt_version VARCHAR(60) NOT NULL,evidence_hash CHAR(64) NOT NULL,prompt_tokens INT NOT NULL DEFAULT 0,completion_tokens INT NOT NULL DEFAULT 0,total_tokens INT NOT NULL DEFAULT 0,reserved_tokens BIGINT NOT NULL DEFAULT 0,started_at DATETIME(6),deadline_at DATETIME(6),heartbeat_at DATETIME(6),latency_ms BIGINT NOT NULL DEFAULT 0,provider_request_id VARCHAR(191),finish_reason VARCHAR(40),response_hash CHAR(64),error_type VARCHAR(80),diagnostic_code VARCHAR(80),created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),UNIQUE KEY uk_running(active_guard)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE ai_model_settings (id BIGINT PRIMARY KEY,provider VARCHAR(40) NOT NULL,api_format VARCHAR(40) NOT NULL,api_base_url VARCHAR(500),model_id VARCHAR(191),model_catalog_json JSON,api_key_ciphertext TEXT,api_key_provider VARCHAR(40),api_key_origin VARCHAR(500),temperature DECIMAL(4,3) NOT NULL,max_output_tokens INT NOT NULL,timeout_seconds INT NOT NULL,retry_count INT NOT NULL,daily_token_quota BIGINT NOT NULL,enabled TINYINT(1) NOT NULL,last_test_status VARCHAR(30) NOT NULL,last_tested_at DATETIME,last_test_message VARCHAR(240),updated_by_admin_id BIGINT,updated_by_admin_username VARCHAR(100),created_at DATETIME DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ai_model_settings (id,provider,api_format,temperature,max_output_tokens,timeout_seconds,retry_count,daily_token_quota,enabled,last_test_status) VALUES (1,'deepseek','openai_compatible',0.2,1200,30,1,100000,0,'not_tested')");
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
