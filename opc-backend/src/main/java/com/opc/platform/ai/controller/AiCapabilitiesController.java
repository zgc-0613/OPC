package com.opc.platform.ai.controller;

import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AgentRuntimeConfigProvider;
import com.opc.platform.ai.vo.AiCapabilitiesVO;
import com.opc.platform.ai.vo.AiCapabilityVO;
import com.opc.platform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiCapabilitiesController {

    private static final String CASE_ANALYSIS_ID = "case-analysis";
    private static final String CASE_ANALYSIS_VERSION = "case-analysis-v1";
    private static final String ENTREPRENEURSHIP_ADVISOR_ID = "entrepreneurship-advisor";
    private static final String ENTREPRENEURSHIP_ADVISOR_VERSION = "entrepreneurship-advisor-v1";
    private static final String AGENT_RUNTIME_ID = "agent-runtime";
    private static final String AGENT_RUNTIME_VERSION = "agent-research-v1";

    private final AiClient aiClient;
    private final ObjectProvider<AgentRuntimeConfigProvider> agentConfigProvider;

    @GetMapping("/capabilities")
    public Result<AiCapabilitiesVO> capabilities() {
        AiProviderDescriptor provider = aiClient.descriptor();
        AiCapabilityVO caseAnalysis = new AiCapabilityVO(
                CASE_ANALYSIS_ID,
                CASE_ANALYSIS_VERSION,
                provider.available()
        );
        AiCapabilityVO entrepreneurshipAdvisor = new AiCapabilityVO(
                ENTREPRENEURSHIP_ADVISOR_ID,
                ENTREPRENEURSHIP_ADVISOR_VERSION,
                provider.available()
        );
        AgentRuntimeConfig agentConfig = agentConfigProvider.getIfAvailable(() -> () ->
                new AgentRuntimeConfig(false, 4, 6, 8000, 12, java.time.Duration.ofSeconds(120), "json_plan"))
                .agentRuntimeConfig();
        AiCapabilityVO agentRuntime = new AiCapabilityVO(
                AGENT_RUNTIME_ID,
                AGENT_RUNTIME_VERSION,
                provider.available() && agentConfig.enabled()
        );
        return Result.success(new AiCapabilitiesVO(provider, List.of(caseAnalysis, entrepreneurshipAdvisor, agentRuntime)));
    }
}
