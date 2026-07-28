package com.opc.platform.ai.service;

import org.junit.jupiter.api.Test;

import static com.opc.platform.ai.service.ResearchExecutionRequirements.Operation.CASE_COMPARISON;
import static com.opc.platform.ai.service.ResearchExecutionRequirements.Operation.CASE_SEARCH;
import static com.opc.platform.ai.service.ResearchExecutionRequirements.Operation.POLICY_SEARCH;
import static com.opc.platform.ai.service.ResearchExecutionRequirements.Operation.SOURCE_VERIFICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchExecutionRequirementsTest {

    @Test
    void explicitPolicyLookupRejectsConflictingModelExpansion() {
        ResearchExecutionRequirements requirements = ResearchExecutionRequirements.resolve(
                "policy_lookup", "请研究适用的扶持政策");

        ResearchExecutionRequirements merged = requirements.withModelIntent("case_analysis");

        assertTrue(merged.requires(POLICY_SEARCH));
        assertFalse(merged.requires(CASE_SEARCH));
        assertEquals("policy_lookup", merged.resolvedIntent());
    }

    @Test
    void deterministicPolicySignalRejectsConflictingModelExpansion() {
        ResearchExecutionRequirements requirements = ResearchExecutionRequirements.resolve(
                "auto", "请查找适用的扶持政策和补贴条件");

        ResearchExecutionRequirements merged = requirements.withModelIntent("case_analysis");

        assertTrue(merged.requires(POLICY_SEARCH));
        assertFalse(merged.requires(CASE_SEARCH));
        assertEquals("policy_lookup", merged.resolvedIntent());
    }

    @Test
    void explicitGeneralResearchRemainsStableAgainstModelIntent() {
        ResearchExecutionRequirements requirements = ResearchExecutionRequirements.resolve(
                "general_research", "帮我梳理下一步创业方向");

        ResearchExecutionRequirements merged = requirements.withModelIntent("case_analysis");

        assertTrue(merged.isEmpty());
        assertEquals("general_research", merged.resolvedIntent());
    }

    @Test
    void explicitTechnologyAssessmentRemainsStableAgainstModelIntent() {
        ResearchExecutionRequirements requirements = ResearchExecutionRequirements.resolve(
                "technology_assessment", "评估这条技术路线");

        ResearchExecutionRequirements merged = requirements.withModelIntent("case_analysis");

        assertTrue(merged.isEmpty());
        assertEquals("technology_assessment", merged.resolvedIntent());
    }

    @Test
    void explicitCaseComparisonRejectsConflictingModelExpansion() {
        ResearchExecutionRequirements requirements = ResearchExecutionRequirements.resolve(
                "case_comparison", "分析这两个对象");

        ResearchExecutionRequirements merged = requirements.withModelIntent("policy_lookup");

        assertTrue(merged.requires(CASE_SEARCH));
        assertTrue(merged.requires(CASE_COMPARISON));
        assertFalse(merged.requires(POLICY_SEARCH));
        assertEquals("case_comparison", merged.resolvedIntent());
    }

    @Test
    void unconstrainedAutoRequestAcceptsSupportedModelIntent() {
        ResearchExecutionRequirements requirements = ResearchExecutionRequirements.resolve(
                "auto", "帮我梳理下一步创业方向");

        ResearchExecutionRequirements merged = requirements.withModelIntent("case_analysis");

        assertTrue(merged.requires(CASE_SEARCH));
        assertEquals("case_analysis", merged.resolvedIntent());
    }

    @Test
    void explicitSignalsMergeWithoutAllowingModelIntentToLowerRequirements() {
        ResearchExecutionRequirements requirements = ResearchExecutionRequirements.resolve(
                "auto", "请比较两个创业案例，并核验原始来源和扶持政策");

        ResearchExecutionRequirements merged = requirements.withModelIntent("case_analysis");

        assertTrue(merged.requires(CASE_SEARCH));
        assertTrue(merged.requires(CASE_COMPARISON));
        assertTrue(merged.requires(SOURCE_VERIFICATION));
        assertTrue(merged.requires(POLICY_SEARCH));
        assertEquals("mixed_research", merged.resolvedIntent());
    }

    @Test
    void negatedComparisonDoesNotTriggerTheExpensiveComparisonChain() {
        ResearchExecutionRequirements requirements = ResearchExecutionRequirements.resolve(
                "auto", "不要比较北京案例，只说明当前项目风险");

        assertFalse(requirements.requires(CASE_COMPARISON));
    }

    @Test
    void ordinaryGeneralResearchRemainsUnconstrained() {
        ResearchExecutionRequirements requirements = ResearchExecutionRequirements.resolve(
                "general_research", "帮我梳理下一步创业方向");

        assertTrue(requirements.isEmpty());
        assertEquals("general_research", requirements.resolvedIntent());
    }
}
