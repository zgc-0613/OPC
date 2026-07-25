package com.opc.platform.ai.vo;

import com.opc.platform.ai.dto.AiModelOptionDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiModelSettingsVO {
    private String provider;
    private String apiFormat;
    private String apiBaseUrl;
    private String modelId;
    private List<AiModelOptionDTO> models;
    private Boolean apiKeyConfigured;
    private Boolean encryptionReady;
    private Double temperature;
    private Integer maxOutputTokens;
    private Integer timeoutSeconds;
    private Integer retryCount;
    private Long dailyTokenQuota;
    private Boolean enabled;
    private Boolean agentEnabled;
    private String agentRolloutState;
    private LocalDateTime agentRolloutChangedAt;
    private Long agentRolloutChangedByAdminId;
    private Integer agentMaxModelRounds;
    private Integer agentMaxToolCalls;
    private Integer agentMaxTokens;
    private Integer agentHistoryWindow;
    private Integer agentTimeoutSeconds;
    private String agentToolMode;
    private String lastTestStatus;
    private LocalDateTime lastTestedAt;
    private String lastTestMessage;
    private String updatedByAdminUsername;
    private LocalDateTime updatedAt;
}
