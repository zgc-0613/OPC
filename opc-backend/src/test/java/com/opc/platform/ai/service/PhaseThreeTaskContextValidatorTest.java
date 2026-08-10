package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseThreeTaskContextValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PhaseThreeTaskContextValidator validator = new PhaseThreeTaskContextValidator(objectMapper);

    @Test
    void normalizesAValidComparisonContextAndProducesStableHash() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"case_comparison",
                 "caseIds":[101,102],"comparisonDimensions":["outcome","businessModel"],
                 "outputDepth":"standard","constraints":"预算有限"}
                """);

        PhaseThreeTaskContext first = validator.validateAndNormalize(input, "case_comparison");
        PhaseThreeTaskContext second = validator.validateAndNormalize(
                objectMapper.readTree(first.canonicalJson()), "case_comparison");

        assertEquals("case_comparison", first.taskType());
        assertEquals("[\"outcome\",\"businessModel\"]", first.node().path("comparisonDimensions").toString());
        assertEquals(first.hash(), second.hash());
        assertEquals(first.canonicalJson(), second.canonicalJson());
    }

    @Test
    void rejectsUnknownFieldsAndIntentMismatch() throws Exception {
        JsonNode unknown = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"general_research",
                 "caseIds":[],"comparisonDimensions":[],"prompt":"不要接受"}
                """);
        assertThrows(BusinessException.class,
                () -> validator.validateAndNormalize(unknown, "general_research"));

        JsonNode mismatch = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"policy_lookup",
                 "caseIds":[],"comparisonDimensions":[]}
                """);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validateAndNormalize(mismatch, "case_analysis"));
        assertEquals(400, exception.getErrorCode().getCode());
    }

    @Test
    void rejectsDuplicateComparisonCasesAndDimensions() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"case_comparison",
                 "caseIds":[101,101],"comparisonDimensions":["outcome","outcome"]}
                """);

        assertThrows(BusinessException.class,
                () -> validator.validateAndNormalize(input, "case_comparison"));
    }

    @Test
    void rejectsFractionalAndOutOfRangeIdentifiersInsteadOfTruncatingThem() throws Exception {
        JsonNode fractionalCaseId = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"case_analysis",
                 "caseIds":[101.5],"comparisonDimensions":[]}
                """);
        JsonNode fractionalTechnologyTagId = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"technology_assessment",
                 "caseIds":[],"comparisonDimensions":[],"technologyTagId":7.5}
                """);
        JsonNode fractionalSourceId = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"source_verification",
                 "caseIds":[],"comparisonDimensions":[],"sourceId":42.5}
                """);
        JsonNode outOfRangeCaseId = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"case_analysis",
                 "caseIds":[9223372036854775808],"comparisonDimensions":[]}
                """);

        assertThrows(BusinessException.class,
                () -> validator.validateAndNormalize(fractionalCaseId, "case_analysis"));
        assertThrows(BusinessException.class,
                () -> validator.validateAndNormalize(fractionalTechnologyTagId, "technology_assessment"));
        assertThrows(BusinessException.class,
                () -> validator.validateAndNormalize(fractionalSourceId, "source_verification"));
        assertThrows(BusinessException.class,
                () -> validator.validateAndNormalize(outOfRangeCaseId, "case_analysis"));
    }

    @Test
    void preservesTheCompleteTechnologyAssessmentBoundaryAndLegacyTextCompatibility() throws Exception {
        JsonNode complete = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"technology_assessment",
                 "caseIds":[],"comparisonDimensions":[],"technologyTagId":91,
                 "technologyText":"面向私有知识库的补充说明",
                 "applicationScenario":"为小微企业客服提供可追溯回答",
                 "teamCapabilities":"两名全栈工程师，具备向量检索经验",
                 "timeline":"3_6_months","existingResources":"已有脱敏 FAQ 与试点客户",
                 "constraints":"数据不得离开私有网络","outputDepth":"deep"}
                """);

        PhaseThreeTaskContext normalized = validator.validateAndNormalize(
                complete, "technology_assessment");

        assertEquals(91L, normalized.node().path("technologyTagId").asLong());
        assertEquals("面向私有知识库的补充说明", normalized.node().path("technologyText").asText());
        assertEquals("为小微企业客服提供可追溯回答", normalized.node().path("applicationScenario").asText());
        assertEquals("两名全栈工程师，具备向量检索经验", normalized.node().path("teamCapabilities").asText());
        assertEquals("3_6_months", normalized.node().path("timeline").asText());
        assertEquals("已有脱敏 FAQ 与试点客户", normalized.node().path("existingResources").asText());
        assertEquals("数据不得离开私有网络", normalized.node().path("constraints").asText());
        assertEquals("deep", normalized.node().path("outputDepth").asText());

        JsonNode legacy = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"technology_assessment",
                 "caseIds":[],"comparisonDimensions":[],"technologyText":"旧版技术说明"}
                """);
        PhaseThreeTaskContext legacyNormalized = validator.validateAndNormalize(
                legacy, "technology_assessment");
        assertEquals("旧版技术说明", legacyNormalized.node().path("technologyText").asText());
        assertEquals("standard", legacyNormalized.node().path("outputDepth").asText());
    }

    @Test
    void rejectsTechnologyOnlyFieldsOutsideTechnologyTasksAndUnsafeTextBounds() throws Exception {
        JsonNode leakedTechnologyBoundary = objectMapper.readTree("""
                {"version":"phase3-task-v1","taskType":"general_research",
                 "caseIds":[],"comparisonDimensions":[],
                 "applicationScenario":"不应进入通用研究"}
                """);
        assertThrows(BusinessException.class, () -> validator.validateAndNormalize(
                leakedTechnologyBoundary, "general_research"));

        String overlongScenario = "场".repeat(501);
        JsonNode overlong = objectMapper.createObjectNode()
                .put("version", "phase3-task-v1")
                .put("taskType", "technology_assessment")
                .set("caseIds", objectMapper.createArrayNode());
        ((com.fasterxml.jackson.databind.node.ObjectNode) overlong)
                .set("comparisonDimensions", objectMapper.createArrayNode());
        ((com.fasterxml.jackson.databind.node.ObjectNode) overlong)
                .put("technologyText", "兼容技术说明")
                .put("applicationScenario", overlongScenario);
        BusinessException exception = assertThrows(BusinessException.class, () ->
                validator.validateAndNormalize(overlong, "technology_assessment"));
        assertTrue(exception.getMessage().contains("PHASE3_TASK_CONTEXT_INVALID"));
    }
}
