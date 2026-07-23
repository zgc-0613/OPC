package com.opc.platform.ai.provider;

public interface AiClient {

    AiProviderResponse generate(AiProviderRequest request);

    AiProviderDescriptor descriptor();
}
