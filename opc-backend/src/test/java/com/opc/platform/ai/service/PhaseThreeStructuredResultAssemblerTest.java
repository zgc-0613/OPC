package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.tool.PhaseThreeEvidenceBundle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseThreeStructuredResultAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PhaseThreeStructuredResultAssembler assembler =
            new PhaseThreeStructuredResultAssembler(objectMapper);

    @Test
    void emitsCaseAnalysisWithSelectedCaseAndCaseSpecificEvidence() throws Exception {
        JsonNode result = assembler.assemble(
                legacy("来源 1 的案例事实", 101L, "caseInsights", "案例商业模式", "inference"),
                "final",
                context("case_analysis", "\"caseIds\":[1]"),
                fullBundle(),
                "sha256:" + "f".repeat(64));

        assertEquals("case_analysis", result.path("taskResult").path("type").asText());
        assertEquals(1, result.path("taskSelectedEvidence").path("caseIds").size());
        assertEquals(1L, result.path("taskSelectedEvidence").path("caseIds").get(0).asLong());
        assertEquals(1, result.path("taskResult").path("caseId").asLong());
        assertEquals("known", result.path("taskResult").path("sections")
                .path("businessModel").path("status").asText());
        assertEquals("high", result.path("confidence").asText());
    }

    @Test
    void keepsComparisonCasesDimensionsAndBaselinesAligned() throws Exception {
        JsonNode result = assembler.assemble(
                legacy("比较结论", 101L, "comparison", "统一比较事实", "fact", 102L),
                "final",
                context("case_comparison", "\"caseIds\":[1,2],\"comparisonDimensions\":[\"businessModel\",\"outcome\"]"),
                fullBundle(),
                "sha256:" + "e".repeat(64));

        JsonNode taskResult = result.path("taskResult");
        assertEquals("case_comparison", taskResult.path("type").asText());
        assertEquals(1L, taskResult.path("caseIds").get(0).asLong());
        assertEquals(2L, taskResult.path("caseIds").get(1).asLong());
        assertEquals("businessModel", taskResult.path("dimensions").get(0).asText());
        assertEquals("outcome", taskResult.path("dimensions").get(1).asText());
        assertEquals(2, taskResult.path("baselines").size());
        assertEquals(2, taskResult.path("comparisons").size());
        assertEquals("businessModel", taskResult.path("comparisons").get(0).path("dimension").asText());
        assertEquals("outcome", taskResult.path("comparisons").get(1).path("dimension").asText());
        assertEquals(2, result.path("taskSelectedEvidence").path("caseIds").size());
    }

    @Test
    void technologyAssessmentAlwaysIncludesTheThreeRequiredDimensions() throws Exception {
        JsonNode result = assembler.assemble(
                legacy("技术评估", 101L, "caseInsights", "基础设施事实", "inference"),
                "final",
                context("technology_assessment", "\"technologyText\":\"检索增强生成\""),
                fullBundle(),
                "sha256:" + "d".repeat(64));

        JsonNode taskResult = result.path("taskResult");
        assertEquals("technology_assessment", taskResult.path("type").asText());
        assertEquals(3, taskResult.path("dimensions").size());
        assertEquals("maturity", taskResult.path("dimensions").get(0).path("dimension").asText());
        assertEquals("scenario_fit", taskResult.path("dimensions").get(1).path("dimension").asText());
        assertEquals("implementation_complexity", taskResult.path("dimensions").get(2).path("dimension").asText());
        assertEquals("检索增强生成", taskResult.path("technology").path("text").asText());
        assertEquals(2, taskResult.path("supportingCases").size());
        assertEquals(1L, taskResult.path("supportingCases").get(0).asLong());
        assertEquals(2L, taskResult.path("supportingCases").get(1).asLong());
        assertEquals(1, taskResult.path("relatedPolicies").size());
    }

    @Test
    void technologyAssessmentKeepsTheImmutableEvaluationConditions() throws Exception {
        JsonNode result = assembler.assemble(
                legacy("技术评估", 101L, "caseInsights", "基础设施事实", "inference"),
                "final",
                context("technology_assessment", """
                        "technologyTagId":91,
                        "technologyText":"私有知识库检索增强生成",
                        "applicationScenario":"为小微企业客服提供可追溯回答",
                        "teamCapabilities":"两名全栈工程师",
                        "timeline":"3_6_months",
                        "existingResources":"已有脱敏 FAQ",
                        "constraints":"数据不得离开私有网络"
                        """),
                fullBundle(),
                "sha256:" + "d".repeat(64));

        JsonNode assessmentContext = result.path("taskResult").path("assessmentContext");
        assertEquals(91L, assessmentContext.path("technologyTagId").asLong());
        assertEquals("私有知识库检索增强生成", assessmentContext.path("technologyText").asText());
        assertEquals("为小微企业客服提供可追溯回答", assessmentContext.path("applicationScenario").asText());
        assertEquals("两名全栈工程师", assessmentContext.path("teamCapabilities").asText());
        assertEquals("3_6_months", assessmentContext.path("timeline").asText());
        assertEquals("已有脱敏 FAQ", assessmentContext.path("existingResources").asText());
        assertEquals("数据不得离开私有网络", assessmentContext.path("constraints").asText());
    }

    @Test
    void policyLookupFactsRemainLinkedToTheSelectedPolicySource() throws Exception {
        JsonNode result = assembler.assemble(
                legacy("政策结论", 103L, "policyInsights", "政策支持事实", "fact"),
                "final",
                context("policy_lookup", ""),
                fullBundle(),
                "sha256:" + "c".repeat(64));

        JsonNode taskResult = result.path("taskResult");
        assertEquals("policy_lookup", taskResult.path("type").asText());
        assertEquals(1, taskResult.path("policyIds").size());
        assertEquals(3L, taskResult.path("policyIds").get(0).asLong());
        JsonNode support = taskResult.path("supportMeasures");
        assertEquals("known", support.path("status").asText());
        assertEquals(103L, support.path("items").get(0).path("sourceIds").get(0).asLong());
        assertEquals("https://example.invalid/source/103", result.path("citations").get(0).path("url").asText());
    }

    @Test
    void selectedSourceVerificationUsesSelectedSourceMode() throws Exception {
        JsonNode result = assembler.assemble(
                legacy("来源核验", 101L, "keyFindings", "来源支持该事实", "fact"),
                "final",
                context("source_verification", "\"sourceId\":101"),
                sourceOnlyBundle(),
                "sha256:" + "b".repeat(64));

        JsonNode taskResult = result.path("taskResult");
        assertEquals("source_verification", taskResult.path("type").asText());
        assertEquals("selected_source", taskResult.path("mode").asText());
        assertEquals(101L, taskResult.path("sourceId").asLong());
        assertEquals(101L, result.path("taskSelectedEvidence").path("sourceIds").get(0).asLong());
    }

    @Test
    void sourceVerificationVerdictIsDerivedFromAuthorizedClaimSupport() throws Exception {
        JsonNode supports = assembleVerification(
                verificationLegacy(new VerificationClaim("core", "该来源支持待核验主张", "supports", 101L)),
                "final", sourceBundle(101L));
        assertEquals("supports", supports.path("taskResult").path("verdict").asText());
        assertEquals("known", supports.path("taskResult").path("supportedClaims").path("status").asText());
        assertTrue(supports.path("taskResult").path("verdictExplanation").asText().contains("支持"));

        JsonNode partial = assembleVerification(
                verificationLegacy(
                        new VerificationClaim("supported", "已确认的部分", "supports", 101L),
                        new VerificationClaim("unresolved", "仍待确认的部分", "unresolved")),
                "final", sourceBundle(101L));
        assertEquals("partially_supports", partial.path("taskResult").path("verdict").asText());
        assertEquals("known", partial.path("taskResult").path("supportedClaims").path("status").asText());
        assertEquals("unknown", partial.path("taskResult").path("unsupportedClaims").path("status").asText());
        assertEquals("known", partial.path("taskResult").path("invalidityReasons").path("status").asText());

        JsonNode doesNotSupport = assembleVerification(
                verificationLegacy(new VerificationClaim("core", "现有来源不支持待核验主张", "contradicts", 101L)),
                "final", sourceBundle(101L));
        assertEquals("does_not_support", doesNotSupport.path("taskResult").path("verdict").asText());
        assertTrue(doesNotSupport.path("taskResult").path("verdictExplanation").asText().contains("不能支持"));

        JsonNode conflicting = assembleVerification(
                verificationLegacy(
                        new VerificationClaim("core", "同一关键主张", "supports", 101L),
                        new VerificationClaim("core", "同一关键主张", "contradicts", 102L)),
                "final", sourceBundle(101L, 102L));
        assertEquals("conflicting", conflicting.path("taskResult").path("verdict").asText());
        assertEquals("known", conflicting.path("taskResult").path("conflicts").path("status").asText());
        assertEquals(2, conflicting.path("taskResult").path("conflicts").path("items")
                .get(0).path("sourceIds").size());

        JsonNode insufficient = assembleVerification(
                verificationLegacy(), "evidence_insufficient", PhaseThreeEvidenceBundle.empty());
        assertEquals("insufficient", insufficient.path("taskResult").path("verdict").asText());
        assertTrue(insufficient.path("taskResult").path("verdictExplanation").asText().contains("没有合法授权证据"));
    }

    @Test
    void sourceVerificationCannotUseUnknownSourcesOrReturnSupportsWithoutCitations() throws Exception {
        ObjectNode unknownSource = verificationLegacy(
                new VerificationClaim("core", "越权来源不得参与核验", "supports", 999L));
        assertThrows(RuntimeException.class, () -> assembleVerification(
                unknownSource, "final", sourceBundle(101L)));

        ObjectNode uncited = verificationLegacy(
                new VerificationClaim("core", "空引用不得得到支持结论", "supports", 101L));
        uncited.putArray("citations");
        assertThrows(RuntimeException.class, () -> assembleVerification(
                uncited, "final", sourceBundle(101L)));
    }

    @Test
    void finalWithoutVerificationClaimsStaysInsufficientDespiteLegacyCitationsAndCoverage() throws Exception {
        ObjectNode legacy = unsafeVerificationLegacy("Legacy fact must not survive insufficient verdict.");

        JsonNode result = assembleVerification(legacy, "final", sourceBundle(101L));

        assertEquals("insufficient", result.path("taskResult").path("verdict").asText());
        assertEquals("insufficient", result.path("taskResult").path("evidenceStatus").asText());
        assertEquals("insufficient", result.path("evidenceCoverage").path("status").asText());
        assertEquals("当前没有足够的授权证据完成来源核验结论。", result.path("directAnswer").asText());
        assertTrue(!result.path("directAnswer").asText().contains("Legacy fact"));
        assertTrue(result.path("keyFindings").isEmpty());
        assertTrue(result.path("recommendations").isEmpty());
        assertTrue(result.path("risks").isEmpty());
        assertTrue(result.path("assumptions").isEmpty());
        assertTrue(result.path("uncertainties").isEmpty());
        assertTrue(result.path("nextQuestions").isEmpty());
        assertTrue(result.path("citations").isEmpty());
        assertEquals(0, result.path("evidenceCoverage").path("factClaimCount").asInt());
        assertEquals(0, result.path("evidenceCoverage").path("citedFactClaimCount").asInt());
        assertEquals(0, result.path("evidenceCoverage").path("missingEvidenceFactCount").asInt());
        assertTrue(result.path("evidenceCoverage").path("ratio").isNull());
        assertEquals("unknown", result.path("taskResult").path("publisherAssessment").path("status").asText());
    }

    @Test
    void finalWithOnlyUnresolvedClaimsStaysInsufficientWithoutUnsupportedFacts() throws Exception {
        ObjectNode legacy = unsafeVerificationLegacy("Unresolved legacy fact must not survive.");
        legacy.putArray("verificationClaims").addObject()
                .put("claimId", "core")
                .put("text", "Unresolved claim")
                .put("relation", "unresolved")
                .putArray("sourceIds");

        JsonNode result = assembleVerification(legacy, "final", sourceBundle(101L));

        assertEquals("insufficient", result.path("taskResult").path("verdict").asText());
        assertEquals("insufficient", result.path("taskResult").path("evidenceStatus").asText());
        assertEquals("当前没有足够的授权证据完成来源核验结论。", result.path("directAnswer").asText());
        assertTrue(result.path("keyFindings").isEmpty());
        assertTrue(result.path("recommendations").isEmpty());
        assertTrue(result.path("risks").isEmpty());
        assertTrue(result.path("assumptions").isEmpty());
        assertTrue(result.path("uncertainties").isEmpty());
        assertTrue(result.path("nextQuestions").isEmpty());
        assertTrue(result.path("citations").isEmpty());
        assertEquals(0, result.path("taskResult").path("unsupportedClaims").path("items").size());
        assertEquals(1, result.path("taskResult").path("invalidityReasons").path("items").size());
        assertEquals("methodology", result.path("taskResult").path("invalidityReasons")
                .path("items").get(0).path("kind").asText());
        assertTrue(result.path("taskResult").path("invalidityReasons")
                .path("items").get(0).path("sourceIds").isEmpty());
        assertEquals(0, result.path("evidenceCoverage").path("factClaimCount").asInt());
        assertTrue(result.path("evidenceCoverage").path("ratio").isNull());
    }

    @Test
    void sameAuthorizedSourceSupportingAndContradictingOneClaimIsConflicting() throws Exception {
        JsonNode result = assembleVerification(
                verificationLegacy(
                        new VerificationClaim("core", "One claim", "supports", 101L),
                        new VerificationClaim("core", "One claim", "contradicts", 101L)),
                "final", sourceBundle(101L));

        assertEquals("conflicting", result.path("taskResult").path("verdict").asText());
        assertEquals("conflicting", result.path("taskResult").path("evidenceStatus").asText());
        assertEquals(List.of(101L), ids(result.path("taskResult").path("conflicts")
                .path("items").get(0).path("sourceIds")));
    }

    @Test
    void differentAuthorizedSourcesSupportingAndContradictingOneClaimRemainConflicting() throws Exception {
        JsonNode result = assembleVerification(
                verificationLegacy(
                        new VerificationClaim("core", "One claim", "supports", 101L),
                        new VerificationClaim("core", "One claim", "contradicts", 102L)),
                "final", sourceBundle(101L, 102L));

        assertEquals("conflicting", result.path("taskResult").path("verdict").asText());
        assertEquals("conflicting", result.path("taskResult").path("evidenceStatus").asText());
    }

    @Test
    void evidenceInsufficientDoesNotEmitPublisherFactsOrCitations() throws Exception {
        JsonNode result = assembleVerification(
                verificationLegacy(), "evidence_insufficient", sourceBundle(101L));

        JsonNode publisher = result.path("taskResult").path("publisherAssessment");
        assertEquals("insufficient", result.path("taskResult").path("verdict").asText());
        assertEquals("insufficient", result.path("taskResult").path("evidenceStatus").asText());
        assertEquals("unknown", publisher.path("status").asText());
        assertEquals(0, publisher.path("items").size());
        assertEquals(0, result.path("citations").size());
        assertEquals(0, result.path("keyFindings").size());
    }

    @Test
    void publisherFactsAreServerDerivedAndCoveredByAuthorizedCitations() throws Exception {
        PhaseThreeEvidenceBundle evidence = sourceBundle(101L, 102L);
        ObjectNode legacy = verificationLegacy(
                new VerificationClaim("core", "Supported claim", "supports", 101L));
        legacy.putObject("publisherAssessment").put("status", "known");

        JsonNode result = assembleVerification(legacy, "final", evidence);
        JsonNode publisher = result.path("taskResult").path("publisherAssessment");

        assertEquals("known", publisher.path("status").asText());
        assertEquals(2, publisher.path("items").size());
        assertEquals("publisher_101", publisher.path("items").get(0).path("id").asText());
        assertEquals(evidence.source(101L).publisher(), publisher.path("items").get(0).path("text").asText());
        assertEquals(List.of(101L), ids(publisher.path("items").get(0).path("sourceIds")));
        assertEquals(List.of(102L), ids(publisher.path("items").get(1).path("sourceIds")));
        assertEquals(List.of(101L, 102L), ids(result.path("citations"), "sourceId"));
        assertEquals(3, result.path("evidenceCoverage").path("factClaimCount").asInt());
        assertEquals(3, result.path("evidenceCoverage").path("citedFactClaimCount").asInt());
    }

    @Test
    void finalWithoutPublisherKeepsPublisherAssessmentUnknown() throws Exception {
        JsonNode result = assembleVerification(
                verificationLegacy(new VerificationClaim("core", "Supported claim", "supports", 101L)),
                "final", sourceBundleWithoutPublisher(101L));

        JsonNode publisher = result.path("taskResult").path("publisherAssessment");
        assertEquals("unknown", publisher.path("status").asText());
        assertEquals(0, publisher.path("items").size());
        assertTrue(publisher.path("caveat").asText().contains("发布者"));
    }

    @Test
    void evidenceInsufficientActionRemainsInsufficientEvenWhenEvidenceBundleIsNonEmpty() throws Exception {
        JsonNode result = assembleVerification(
                verificationLegacy(), "evidence_insufficient", sourceBundle(101L));

        assertEquals("insufficient", result.path("taskResult").path("verdict").asText());
        assertEquals("insufficient", result.path("taskResult").path("evidenceStatus").asText());
        assertEquals("insufficient", result.path("evidenceCoverage").path("status").asText());
    }

    @Test
    void unresolvedClaimsWithoutSourcesRemainInsufficientRatherThanDoesNotSupport() throws Exception {
        JsonNode result = assembleVerification(
                verificationLegacy(new VerificationClaim("unknown", "尚未核验的主张", "unresolved")),
                "final", sourceBundle(101L));

        assertEquals("insufficient", result.path("taskResult").path("verdict").asText());
        assertEquals("insufficient", result.path("taskResult").path("evidenceStatus").asText());
        assertEquals(0, result.path("taskResult").path("unsupportedClaims").path("items").size());
    }

    @Test
    void publisherAssessmentIsServerDerivedAndUsesOnlyAuthorizedSourceMetadata() throws Exception {
        ObjectNode legacy = verificationLegacy(
                new VerificationClaim("core", "来源支持待核验主张", "supports", 101L));
        ObjectNode modelPublisherAssessment = legacy.putObject("publisherAssessment");
        modelPublisherAssessment.put("status", "known");
        modelPublisherAssessment.putArray("items").addObject()
                .put("id", "publisher_model")
                .put("kind", "fact")
                .put("text", "模型编造发布者")
                .putArray("sourceIds").add(999L);

        JsonNode result = assembleVerification(legacy, "final", sourceBundle(101L));
        JsonNode publisher = result.path("taskResult").path("publisherAssessment");

        assertEquals("known", publisher.path("status").asText());
        assertEquals(1, publisher.path("items").size());
        JsonNode item = publisher.path("items").get(0);
        assertEquals("publisher_101", item.path("id").asText());
        assertEquals("fact", item.path("kind").asText());
        assertEquals("测试发布者", item.path("text").asText());
        assertEquals(List.of(101L), ids(item.path("sourceIds")));
        assertTrue(publisher.toString().contains("测试发布者"));
        assertTrue(!publisher.toString().contains("模型编造发布者"));
    }

    @Test
    void publisherAssessmentReportsUnknownWhenAuthorizedSourcesHaveNoPublisher() throws Exception {
        JsonNode result = assembleVerification(
                verificationLegacy(new VerificationClaim("core", "来源支持待核验主张", "supports", 101L)),
                "final", sourceBundleWithoutPublisher(101L));

        JsonNode publisher = result.path("taskResult").path("publisherAssessment");
        assertEquals("unknown", publisher.path("status").asText());
        assertEquals(0, publisher.path("items").size());
        assertTrue(publisher.path("caveat").asText().contains("来源记录未提供发布者信息"));
        assertTrue(result.path("citations").get(0).path("publisher").isNull());
    }

    @Test
    void publisherAssessmentIncludesEachAuthorizedPublisherInStableSourceOrder() throws Exception {
        JsonNode result = assembleVerification(
                verificationLegacy(new VerificationClaim("core", "来源支持待核验主张", "supports", 101L)),
                "final", sourceBundle(102L, 101L));

        JsonNode items = result.path("taskResult").path("publisherAssessment").path("items");
        assertEquals(2, items.size());
        assertEquals("publisher_101", items.get(0).path("id").asText());
        assertEquals("publisher_102", items.get(1).path("id").asText());
        assertEquals(List.of(101L), ids(items.get(0).path("sourceIds")));
        assertEquals(List.of(102L), ids(items.get(1).path("sourceIds")));
    }

    @Test
    void generalResearchWithNoFactsReportsNullCoverageRatio() throws Exception {
        JsonNode result = assembler.assemble(
                legacyWithoutFacts(),
                "evidence_insufficient",
                context("general_research", ""),
                PhaseThreeEvidenceBundle.empty(),
                "sha256:" + "a".repeat(64));

        assertEquals("general_research", result.path("taskResult").path("type").asText());
        assertTrue(result.path("taskResult").path("sections").size() >= 1);
        assertEquals(0, result.path("evidenceCoverage").path("factClaimCount").asInt());
        assertEquals(0, result.path("evidenceCoverage").path("citedFactClaimCount").asInt());
        assertEquals(0, result.path("evidenceCoverage").path("missingEvidenceFactCount").asInt());
        assertTrue(result.path("evidenceCoverage").path("ratio").isNull());
        assertNotNull(result.path("dataVersion"));
        assertNull(result.path("dataVersion").asText(null));
    }

    private JsonNode context(String taskType, String fields) throws Exception {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("version", "phase3-task-v1");
        value.put("taskType", taskType);
        value.putArray("caseIds");
        value.putArray("comparisonDimensions");
        value.put("outputDepth", "standard");
        if (fields != null && !fields.isBlank()) {
            JsonNode extras = objectMapper.readTree("{" + fields + "}");
            extras.fields().forEachRemaining(entry -> value.set(entry.getKey(), entry.getValue()));
        }
        return value;
    }

    private ObjectNode legacy(String answer, long sourceId, String section, String text, String evidenceType,
                              long... additionalSourceIds) {
        ObjectNode value = legacyBase(answer);
        ArrayNode sources = objectMapper.createArrayNode().add(sourceId);
        for (long id : additionalSourceIds) sources.add(id);
        value.putArray("keyFindings").addObject()
                .put("text", answer).put("evidenceType", "fact").set("sourceIds", sources.deepCopy());
        value.putArray("caseInsights");
        value.putArray("policyInsights");
        value.putArray("comparison");
        if ("keyFindings".equals(section)) {
            ((ObjectNode) value.withArray("keyFindings").get(0)).put("text", text);
        } else {
            value.withArray(section).addObject()
                    .put("text", text).put("evidenceType", evidenceType).set("sourceIds", sources.deepCopy());
        }
        ObjectNode citations = value.putArray("citations").addObject();
        citations.put("sourceId", sourceId).put("claim", text);
        for (long id : additionalSourceIds) value.withArray("citations").addObject().put("sourceId", id).put("claim", text);
        return value;
    }

    private ObjectNode legacyWithoutFacts() {
        ObjectNode value = legacyBase("暂无足够证据");
        value.putArray("citations");
        return value;
    }

    private ObjectNode unsafeVerificationLegacy(String directAnswer) {
        ObjectNode value = legacyBase(directAnswer);
        value.withArray("keyFindings").addObject()
                .put("text", "Legacy factual finding")
                .put("evidenceType", "fact")
                .putArray("sourceIds").add(101L);
        value.withArray("recommendations").addObject()
                .put("priority", "high")
                .put("reason", "Legacy recommendation premise")
                .put("nextAction", "Legacy next action")
                .putArray("sourceIds").add(101L);
        value.withArray("risks").add("Legacy risk");
        value.withArray("assumptions").add("Legacy assumption");
        value.withArray("uncertainties").add("Legacy uncertainty");
        value.withArray("nextQuestions").add("Legacy next question");
        value.withArray("citations").addObject()
                .put("sourceId", 101L)
                .put("claim", "Legacy citation claim");
        value.putObject("evidenceCoverage")
                .put("status", "sufficient")
                .put("caseCount", 0)
                .put("policyCount", 0)
                .put("sourceCount", 1)
                .putArray("limitations");
        return value;
    }

    private JsonNode assembleVerification(
            ObjectNode legacy,
            String action,
            PhaseThreeEvidenceBundle evidence
    ) throws Exception {
        return assembler.assemble(
                legacy,
                action,
                context("source_verification", ""),
                evidence,
                "sha256:" + "9".repeat(64));
    }

    private ObjectNode verificationLegacy(VerificationClaim... claims) {
        ObjectNode value = legacyBase(claims.length == 0
                ? "当前没有可形成证据链的来源。" : "已按授权来源核验该主张。");
        ArrayNode verificationClaims = value.putArray("verificationClaims");
        LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
        for (VerificationClaim claim : claims) {
            ObjectNode item = verificationClaims.addObject();
            item.put("claimId", claim.claimId());
            item.put("text", claim.text());
            item.put("relation", claim.relation());
            ArrayNode itemSources = item.putArray("sourceIds");
            for (long sourceId : claim.sourceIds()) {
                itemSources.add(sourceId);
                sourceIds.add(sourceId);
            }
        }
        ArrayNode citations = value.putArray("citations");
        sourceIds.forEach(sourceId -> citations.addObject()
                .put("sourceId", sourceId)
                .put("claim", "核验主张的授权来源"));
        return value;
    }

    private ObjectNode legacyBase(String answer) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("directAnswer", answer);
        value.put("confidence", 0.8D);
        value.putArray("keyFindings");
        value.putArray("caseInsights");
        value.putArray("policyInsights");
        value.putArray("comparison");
        value.putArray("recommendations");
        value.putArray("risks");
        value.putArray("assumptions");
        value.putArray("uncertainties");
        value.putArray("nextQuestions");
        value.putArray("citations");
        return value;
    }

    private PhaseThreeEvidenceBundle fullBundle() {
        return new PhaseThreeEvidenceBundle(
                List.of(
                        entity(1, "a"),
                        entity(2, "b")
                ),
                List.of(entity(3, "c")),
                List.of(source(101, "a"), source(102, "b"), source(103, "c")),
                List.of(new PhaseThreeEvidenceBundle.CaseSourceLink(1, 101),
                        new PhaseThreeEvidenceBundle.CaseSourceLink(2, 102)),
                List.of(new PhaseThreeEvidenceBundle.PolicySourceLink(3, 103))
        );
    }

    private PhaseThreeEvidenceBundle sourceOnlyBundle() {
        return new PhaseThreeEvidenceBundle(
                List.of(), List.of(), List.of(source(101, "a")), List.of(), List.of());
    }

    private PhaseThreeEvidenceBundle sourceBundle(long... sourceIds) {
        return new PhaseThreeEvidenceBundle(
                List.of(), List.of(),
                java.util.Arrays.stream(sourceIds)
                        .mapToObj(id -> source(id, Long.toHexString(Math.floorMod(id, 16))))
                        .toList(),
                List.of(), List.of());
    }

    private PhaseThreeEvidenceBundle sourceBundleWithoutPublisher(long... sourceIds) {
        return new PhaseThreeEvidenceBundle(
                List.of(), List.of(),
                java.util.Arrays.stream(sourceIds)
                        .mapToObj(id -> new PhaseThreeEvidenceBundle.SourceEvidence(
                                id, "来源 " + id, null, "https://example.invalid/source/" + id,
                                1, "sha256:" + Long.toHexString(Math.floorMod(id, 16)).repeat(64), "published_verified"))
                        .toList(),
                List.of(), List.of());
    }

    private PhaseThreeEvidenceBundle.EntityEvidence entity(long id, String hash) {
        return new PhaseThreeEvidenceBundle.EntityEvidence(
                id, 1, "sha256:" + hash.repeat(64), "published_verified");
    }

    private PhaseThreeEvidenceBundle.SourceEvidence source(long id, String hash) {
        return new PhaseThreeEvidenceBundle.SourceEvidence(
                id, "来源 " + id, "测试发布者", "https://example.invalid/source/" + id,
                1, "sha256:" + hash.repeat(64), "published_verified");
    }

    private record VerificationClaim(String claimId, String text, String relation, long... sourceIds) { }

    private List<Long> ids(JsonNode values) {
        List<Long> ids = new java.util.ArrayList<>();
        values.forEach(value -> ids.add(value.asLong()));
        return ids;
    }

    private List<Long> ids(JsonNode values, String field) {
        List<Long> ids = new java.util.ArrayList<>();
        values.forEach(value -> ids.add(value.path(field).asLong()));
        return ids;
    }
}
