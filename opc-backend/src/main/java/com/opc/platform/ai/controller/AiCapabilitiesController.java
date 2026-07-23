package com.opc.platform.ai.controller;

import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.vo.AiCapabilitiesVO;
import com.opc.platform.ai.vo.AiCapabilityVO;
import com.opc.platform.common.result.Result;
import lombok.RequiredArgsConstructor;
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

    private final AiClient aiClient;

    @GetMapping("/capabilities")
    public Result<AiCapabilitiesVO> capabilities() {
        AiProviderDescriptor provider = aiClient.descriptor();
        AiCapabilityVO caseAnalysis = new AiCapabilityVO(
                CASE_ANALYSIS_ID,
                CASE_ANALYSIS_VERSION,
                provider.available()
        );
        return Result.success(new AiCapabilitiesVO(provider, List.of(caseAnalysis)));
    }
}
