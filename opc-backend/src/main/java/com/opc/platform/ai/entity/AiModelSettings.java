package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model_settings")
public class AiModelSettings {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String provider;
    private String apiFormat;
    private String apiBaseUrl;
    private String modelId;
    private String modelCatalogJson;
    private String apiKeyCiphertext;
    private String apiKeyProvider;
    private String apiKeyOrigin;
    private Double temperature;
    private Integer maxOutputTokens;
    private Integer timeoutSeconds;
    private Integer retryCount;
    private Long dailyTokenQuota;
    private Boolean enabled;
    private Boolean agentEnabled;
    private Integer agentMaxModelRounds;
    private Integer agentMaxToolCalls;
    private Integer agentMaxTokens;
    private Integer agentHistoryWindow;
    private Integer agentTimeoutSeconds;
    private String agentToolMode;
    private String lastTestStatus;
    private LocalDateTime lastTestedAt;
    private String lastTestMessage;
    private Long updatedByAdminId;
    private String updatedByAdminUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
