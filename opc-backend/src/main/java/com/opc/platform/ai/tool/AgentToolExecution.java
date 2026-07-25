package com.opc.platform.ai.tool;

public record AgentToolExecution(
        Long toolCallId,
        String toolName,
        AgentToolResult result
) {
}
