package com.opc.platform.ai.config;

import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.DisabledAiClient;
import com.opc.platform.ai.provider.FakeAiClient;
import com.opc.platform.ai.provider.AiProviderFactory;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.provider.ManagedAiClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class AiProviderConfiguration {

    @Bean
    AiClient aiClient(
            Environment environment,
            ObjectProvider<AiRuntimeSettingsProvider> settingsProvider,
            ObjectProvider<AiProviderFactory> providerFactory
    ) {
        String provider = environment.getProperty("opc.ai.provider", "disabled");
        String model = environment.getProperty("opc.ai.model", "unconfigured");
        boolean fakeAllowed = environment.getProperty("opc.ai.allow-fake", Boolean.class, false);
        if ("fake".equalsIgnoreCase(provider) && fakeAllowed) {
            return new FakeAiClient(model);
        }
        AiRuntimeSettingsProvider runtimeSettings = settingsProvider.getIfAvailable();
        AiProviderFactory factory = providerFactory.getIfAvailable();
        if (runtimeSettings != null && factory != null) {
            return new ManagedAiClient(runtimeSettings, factory);
        }
        return new DisabledAiClient(model);
    }
}
