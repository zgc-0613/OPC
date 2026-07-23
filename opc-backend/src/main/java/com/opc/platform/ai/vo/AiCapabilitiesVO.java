package com.opc.platform.ai.vo;

import com.opc.platform.ai.provider.AiProviderDescriptor;

import java.util.List;

public record AiCapabilitiesVO(
        AiProviderDescriptor provider,
        List<AiCapabilityVO> capabilities
) {
}
