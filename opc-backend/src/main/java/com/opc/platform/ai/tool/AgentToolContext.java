package com.opc.platform.ai.tool;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AgentToolContext {

    private final Long runId;
    private final Long userId;
    private final Set<Long> allowedSourceIds = new LinkedHashSet<>();
    private final Set<Long> allowedCaseIds = new LinkedHashSet<>();

    public AgentToolContext(Long runId, Long userId) {
        this.runId = runId;
        this.userId = userId;
    }

    public Long runId() {
        return runId;
    }

    public Long userId() {
        return userId;
    }

    public Set<Long> allowedSourceIds() {
        return Set.copyOf(allowedSourceIds);
    }

    public Set<Long> allowedCaseIds() {
        return Set.copyOf(allowedCaseIds);
    }

    void accept(AgentToolResult result) {
        allowedSourceIds.addAll(result.sourceIds());
        allowedCaseIds.addAll(result.caseIds());
    }
}
