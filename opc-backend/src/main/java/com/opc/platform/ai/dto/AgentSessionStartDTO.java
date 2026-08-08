package com.opc.platform.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.service.AgentAnalyticsSnapshotBinding;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentSessionStartDTO {

    private JsonNode profile;

    private JsonNode taskContext;

    /** Only server code can attach this after recreating an owned analytics snapshot. */
    @JsonIgnore
    private AgentAnalyticsSnapshotBinding analyticsSnapshotBinding;

    @NotBlank
    @Size(max = 2000)
    private String content;

    @NotBlank
    @Size(min = 8, max = 64)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String idempotencyKey;

    @Pattern(regexp = AgentResearchContract.REQUESTED_INTENT_PATTERN)
    private String requestedIntent;
}
