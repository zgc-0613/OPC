package com.opc.platform.ai.provider;

public class FakeAiClient implements AiClient {

    private final AiProviderDescriptor descriptor;

    public FakeAiClient(String model) {
        this.descriptor = new AiProviderDescriptor("fake", model, true);
    }

    @Override
    public AiProviderResponse generate(AiProviderRequest request) {
        return new AiProviderResponse("{}");
    }

    @Override
    public AiProviderDescriptor descriptor() {
        return descriptor;
    }
}
