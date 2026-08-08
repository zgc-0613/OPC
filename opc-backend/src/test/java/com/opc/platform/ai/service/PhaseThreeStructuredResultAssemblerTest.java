package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.tool.PhaseThreeEvidenceBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private PhaseThreeEvidenceBundle.EntityEvidence entity(long id, String hash) {
        return new PhaseThreeEvidenceBundle.EntityEvidence(
                id, 1, "sha256:" + hash.repeat(64), "published_verified");
    }

    private PhaseThreeEvidenceBundle.SourceEvidence source(long id, String hash) {
        return new PhaseThreeEvidenceBundle.SourceEvidence(
                id, "来源 " + id, "测试发布者", "https://example.invalid/source/" + id,
                1, "sha256:" + hash.repeat(64), "published_verified");
    }
}
