package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
