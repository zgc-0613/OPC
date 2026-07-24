package com.opc.platform.ai.provider;

public interface AiClient {

    AiProviderResponse generate(AiProviderRequest request);

    default AiProviderResponse generate(AiProviderRequest request, AiRuntimeSettings settings) {
        return generate(request);
    }

    AiProviderDescriptor descriptor();

    default AiProviderDescriptor descriptor(AiRuntimeSettings settings) {
        return descriptor();
    }
}
