package com.opc.platform.ai.tool;

public interface AgentTool<T> {

    String name();

    String description();

    Class<T> argumentType();

    String argumentSchema();

    AgentToolResult execute(AgentToolContext context, T arguments);
}
