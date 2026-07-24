package com.opc.platform.ai.provider;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ManagedAiClient implements AiClient {

    private final AiRuntimeSettingsProvider settingsProvider;
    private final AiProviderFactory providerFactory;

    @Override
    public AiProviderResponse generate(AiProviderRequest request) {
        AiRuntimeSettings settings = settingsProvider.current();
        return generate(request, settings);
    }

    @Override
    public AiProviderResponse generate(AiProviderRequest request, AiRuntimeSettings settings) {
        return providerFactory.create(settings).generate(request);
    }

    @Override
    public AiProviderDescriptor descriptor() {
        AiRuntimeSettings settings = settingsProvider.current();
        return descriptor(settings);
    }

    @Override
    public AiProviderDescriptor descriptor(AiRuntimeSettings settings) {
        if (settings == null || !settings.enabled()) {
            return new AiProviderDescriptor(
                    settings == null ? "disabled" : settings.provider(),
                    settings == null ? "unconfigured" : settings.model(),
                    false
            );
        }
        return providerFactory.create(settings).descriptor();
    }
}
