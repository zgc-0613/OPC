package com.opc.platform.ai.provider;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;

public class DisabledAiClient implements AiClient {

    private final AiProviderDescriptor descriptor;

    public DisabledAiClient(String model) {
        this.descriptor = new AiProviderDescriptor("disabled", model, false);
    }

    @Override
    public AiProviderResponse generate(AiProviderRequest request) {
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI provider is not configured");
    }

    @Override
    public AiProviderDescriptor descriptor() {
        return descriptor;
    }
}
