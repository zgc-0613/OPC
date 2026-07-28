package com.opc.platform.ai.service;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ResearchExecutionRequirements {

    public enum Operation {
        POLICY_SEARCH,
        CASE_SEARCH,
        CASE_COMPARISON,
        SOURCE_VERIFICATION
    }

    private static final Pattern NEGATED_COMPARISON = Pattern.compile(
            "(?:不要|无需|不需要|不用|别)(?:再|进行|做)?(?:比较|对比|分析差异)[^，。！？!?；;]{0,16}"
                    + "|(?:do not|don't|no need to)\\s+(?:compare|contrast)[^,.!?;]{0,32}");
    private static final Pattern COMPARISON = Pattern.compile(
            "比较|对比|差异|两个案例|两项案例|案例\\s*[aAＡ][^，。！？!?；;]{0,16}(?:和|与|及)[^，。！？!?；;]{0,16}案例\\s*[bBＢ]"
                    + "|\\bcompare\\b|\\bcontrast\\b");
    private static final Pattern SOURCE_VERIFICATION = Pattern.compile(
            "核验来源|验证出处|核验出处|原始来源|证据链|来源可信度|来源是否可信"
                    + "|verify (?:the )?source|source verification|evidence chain");
    private static final Pattern POLICY_SEARCH = Pattern.compile(
            "查找政策|查询政策|检索政策|扶持政策|补贴|政策适用条件"
                    + "|policy lookup|search (?:for )?polic(?:y|ies)");
    private static final Pattern CASE_SEARCH = Pattern.compile(
            "案例分析|分析[^，。！？!?；;]{0,16}案例|查找案例|查询案例|检索案例|创业案例"
                    + "|case analysis|search (?:for )?cases");

    private final EnumSet<Operation> operations;
    private final String requestedIntent;
    private final String modelIntent;

    private ResearchExecutionRequirements(
            EnumSet<Operation> operations,
            String requestedIntent,
            String modelIntent
    ) {
        this.operations = operations.clone();
        this.requestedIntent = requestedIntent;
        this.modelIntent = modelIntent;
    }

    public static ResearchExecutionRequirements resolve(String requestedIntent, String message) {
        String normalizedIntent = normalizeIntent(requestedIntent);
        EnumSet<Operation> operations = operationsForIntent(normalizedIntent);
        String normalizedMessage = normalizeMessage(message);
        String comparisonEligible = NEGATED_COMPARISON.matcher(normalizedMessage).replaceAll(" ");
        if (COMPARISON.matcher(comparisonEligible).find()) {
            operations.add(Operation.CASE_SEARCH);
            operations.add(Operation.CASE_COMPARISON);
        }
        if (SOURCE_VERIFICATION.matcher(normalizedMessage).find()) {
            operations.add(Operation.SOURCE_VERIFICATION);
        }
        if (POLICY_SEARCH.matcher(normalizedMessage).find()) {
            operations.add(Operation.POLICY_SEARCH);
        }
        if (CASE_SEARCH.matcher(comparisonEligible).find()) {
            operations.add(Operation.CASE_SEARCH);
        }
        return new ResearchExecutionRequirements(operations, normalizedIntent, null);
    }

    public ResearchExecutionRequirements withModelIntent(String intent) {
        if (!"auto".equals(requestedIntent) || !operations.isEmpty()) {
            return new ResearchExecutionRequirements(
                    operations, requestedIntent, normalizeIntent(intent));
        }
        EnumSet<Operation> merged = operations.clone();
        merged.addAll(operationsForIntent(intent));
        return new ResearchExecutionRequirements(merged, requestedIntent, normalizeIntent(intent));
    }

    public boolean requires(Operation operation) {
        return operations.contains(operation);
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }

    public Set<Operation> operations() {
        return Set.copyOf(operations);
    }

    public String resolvedIntent() {
        if (!"auto".equals(requestedIntent)) return requestedIntent;
        if (requires(Operation.CASE_COMPARISON)
                && (requires(Operation.POLICY_SEARCH) || requires(Operation.SOURCE_VERIFICATION))) {
            return "mixed_research";
        }
        if (requires(Operation.SOURCE_VERIFICATION)) return "source_verification";
        if (requires(Operation.CASE_COMPARISON)) return "case_comparison";
        if (requires(Operation.POLICY_SEARCH) && requires(Operation.CASE_SEARCH)) return "mixed_research";
        if (requires(Operation.POLICY_SEARCH)) return "policy_lookup";
        if (requires(Operation.CASE_SEARCH)) return "case_analysis";
        if (modelIntent != null && !"auto".equals(modelIntent) && !"general_research".equals(modelIntent)) {
            return modelIntent;
        }
        return "general_research".equals(requestedIntent) ? "general_research" : "follow_up";
    }

    private static EnumSet<Operation> operationsForIntent(String intent) {
        EnumSet<Operation> operations = EnumSet.noneOf(Operation.class);
        switch (normalizeIntent(intent)) {
            case "policy_lookup" -> operations.add(Operation.POLICY_SEARCH);
            case "case_analysis" -> operations.add(Operation.CASE_SEARCH);
            case "case_comparison" -> {
                operations.add(Operation.CASE_SEARCH);
                operations.add(Operation.CASE_COMPARISON);
            }
            case "source_verification" -> operations.add(Operation.SOURCE_VERIFICATION);
            case "mixed_research" -> {
                operations.add(Operation.POLICY_SEARCH);
                operations.add(Operation.CASE_SEARCH);
            }
            default -> {
            }
        }
        return operations;
    }

    public static String normalizeIntent(String intent) {
        return intent == null || intent.isBlank()
                ? "auto" : intent.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMessage(String message) {
        if (message == null) return "";
        return Normalizer.normalize(message, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
