package com.opc.platform.ai.contract;

import java.util.List;
import java.util.Set;

public final class AgentResearchContract {

    public static final String PROMPT_VERSION = "agent-research-v2";
    public static final int PLANNING_OUTPUT_TOKENS = 3200;
    public static final int SYNTHESIS_OUTPUT_TOKENS = 3200;
    public static final int MAX_RESEARCH_QUESTIONS = 3;
    public static final int MAX_RESEARCH_QUESTION_LENGTH = 80;
    public static final int MAX_COMPARISON_DIMENSIONS = 3;
    public static final int MAX_COMPARISON_DIMENSION_LENGTH = 32;
    public static final int MAX_PLANNED_TOOLS = 4;
    public static final int MAX_DEPENDENCIES = 4;
    public static final int MAX_DEPENDENCY_LENGTH = 32;
    public static final int MAX_TOOL_AUDIT_JSON_BYTES = 128 * 1024;
    public static final int MAX_TOOL_MODEL_JSON_BYTES = 12 * 1024;
    public static final int MAX_TOOL_MODEL_FIELD_BYTES = 480;
    public static final int MAX_STATEMENTS = 6;
    public static final int MAX_KEY_FINDINGS = 2;
    public static final int MAX_CASE_INSIGHTS = 1;
    public static final int MAX_POLICY_INSIGHTS = 1;
    public static final int MAX_COMPARISON_ITEMS = 2;
    public static final int MAX_DIRECT_ANSWER_LENGTH = 600;
    public static final int MAX_STATEMENT_LENGTH = 320;
    public static final int MAX_RECOMMENDATIONS = 3;
    public static final int MAX_RECOMMENDATION_FIELD_LENGTH = 240;
    public static final int MAX_SUPPLEMENTAL_ITEMS = 6;
    public static final int MAX_RISKS = 2;
    public static final int MAX_ASSUMPTIONS = 1;
    public static final int MAX_UNCERTAINTIES = 1;
    public static final int MAX_NEXT_QUESTIONS = 2;
    public static final int MAX_SUPPLEMENTAL_ITEM_LENGTH = 240;
    public static final int MAX_CITATIONS = 6;
    public static final int MAX_CITATION_CLAIM_LENGTH = 200;
    public static final int MAX_SOURCE_IDS_PER_ITEM = 6;
    public static final int MAX_VERIFICATION_CLAIMS = 6;
    public static final int MAX_COVERAGE_LIMITATIONS = 3;
    public static final int MAX_COVERAGE_LIMITATION_LENGTH = 200;
    public static final String INVALID_JSON = "INVALID_JSON";
    public static final String UNKNOWN_FIELDS = "UNKNOWN_FIELDS";
    public static final String INVALID_TOOL_REQUESTS = "INVALID_TOOL_REQUESTS";
    public static final String INVALID_RESEARCH_QUESTIONS = "INVALID_RESEARCH_QUESTIONS";
    public static final String INVALID_COMPARISON_DIMENSIONS = "INVALID_COMPARISON_DIMENSIONS";
    public static final String INVALID_OUTPUT_SECTIONS = "INVALID_OUTPUT_SECTIONS";
    public static final String INVALID_DEPENDENCIES = "INVALID_DEPENDENCIES";
    public static final String MISSING_FIELD = "MISSING_FIELD";
    public static final String INVALID_CONFIDENCE = "INVALID_CONFIDENCE";
    public static final String REQUIRED_TOOL_CHAIN_UNSATISFIED = "REQUIRED_TOOL_CHAIN_UNSATISFIED";
    public static final String TRUNCATED_RESPONSE = "TRUNCATED_RESPONSE";
    public static final String PLANNING_RESPONSE_TRUNCATED_FALLBACK =
            "PLANNING_RESPONSE_TRUNCATED_FALLBACK";
    public static final String FINAL_RESPONSE_TRUNCATED_FALLBACK = "FINAL_RESPONSE_TRUNCATED_FALLBACK";
    public static final String FINAL_RESPONSE_INVALID_STRUCTURED_FALLBACK =
            "FINAL_RESPONSE_INVALID_STRUCTURED_FALLBACK";
    public static final String FINAL_RESPONSE_INVALID_JSON_FALLBACK =
            "FINAL_RESPONSE_INVALID_JSON_FALLBACK";
    public static final String FINAL_RESPONSE_CONTRACT_FALLBACK = "FINAL_RESPONSE_CONTRACT_FALLBACK";
    public static final String UNCITED_FACT = "UNCITED_FACT";
    public static final String UNCITED_RECOMMENDATION = "UNCITED_RECOMMENDATION";
    public static final String MISSING_CITATIONS = "MISSING_CITATIONS";
    public static final String INVALID_SOURCE_ID = "INVALID_SOURCE_ID";
    public static final String INVALID_EVIDENCE_COVERAGE = "INVALID_EVIDENCE_COVERAGE";
    public static final String INVALID_STRUCTURED_RESULT = "INVALID_STRUCTURED_RESULT";
    public static final String PROVIDER_CONNECTION_FAILED = "PROVIDER_CONNECTION_FAILED";
    public static final String AGENT_DEADLINE_FALLBACK = "AGENT_DEADLINE_FALLBACK";

    public static final String REQUESTED_INTENT_PATTERN =
            "auto|policy_lookup|case_analysis|case_comparison|source_verification|technology_assessment|general_research";
    public static final Set<String> PHASE_THREE_TASK_TYPES = Set.of(
            "policy_lookup", "case_analysis", "case_comparison", "source_verification",
            "technology_assessment", "general_research"
    );
    public static final Set<String> REQUESTED_INTENTS = Set.of(
            "auto", "policy_lookup", "case_analysis", "case_comparison", "source_verification",
            "technology_assessment", "general_research"
    );

    public static final Set<String> INTENTS = Set.of(
            "policy_lookup", "case_analysis", "case_comparison", "technology_assessment",
            "source_verification", "general_research", "mixed_research", "follow_up"
    );
    public static final Set<String> INITIAL_SEARCH_TOOLS = Set.of("search_cases", "search_policies");
    public static final Set<String> DEPENDENT_TOOLS = Set.of("compare_cases", "get_source");
    public static final List<String> COMPARISON_DIMENSIONS = List.of(
            "businessModel", "technicalPath", "targetCustomer", "outcome",
            "regionalContext", "evidenceStrength"
    );
    public static final List<String> OUTPUT_SECTIONS = List.of(
            "directAnswer", "keyFindings", "caseInsights", "policyInsights", "comparison",
            "recommendations", "risks", "assumptions", "uncertainties", "nextQuestions",
            "citations", "confidence", "evidenceCoverage"
    );

    private AgentResearchContract() {
    }

    public static String planningBoundaryPrompt() {
        return "at most " + MAX_RESEARCH_QUESTIONS + " focused research questions, "
                + MAX_PLANNED_TOOLS + " essential tool requests, and "
                + MAX_COMPARISON_DIMENSIONS + " short comparison dimensions";
    }

    public static String synthesisBoundaryPrompt() {
        return "at most " + MAX_STATEMENTS + " statement items total, at most "
                + MAX_RECOMMENDATIONS + " recommendations, " + MAX_SUPPLEMENTAL_ITEMS
                + " supplemental list items total, and " + MAX_CITATIONS + " citations";
    }
}
