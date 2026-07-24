package com.opc.platform.ai.config;

import com.opc.platform.ai.provider.AiHttpTransport;
import com.opc.platform.ai.provider.JavaNetAiHttpTransport;
import com.opc.platform.ai.provider.ValidatingAiHttpTransport;
import com.opc.platform.ai.security.AesGcmSecretCipher;
import com.opc.platform.ai.security.ProviderEndpointPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashSet;

@Configuration(proxyBeanMethods = false)
public class AiSettingsConfiguration {

    @Bean
    AesGcmSecretCipher aiSettingsCipher(
            @Value("${opc.ai.settings-master-key:}") String masterKey
    ) {
        return new AesGcmSecretCipher(masterKey);
    }

    @Bean
    ProviderEndpointPolicy providerEndpointPolicy(
            @Value("${opc.ai.trusted-provider-origins:https://api.deepseek.com}") String origins
    ) {
        LinkedHashSet<String> trusted = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new ProviderEndpointPolicy(trusted, ProviderEndpointPolicy.HostResolver.system());
    }

    @Bean
    AiHttpTransport aiHttpTransport(ProviderEndpointPolicy endpointPolicy) {
        return new ValidatingAiHttpTransport(new JavaNetAiHttpTransport(), endpointPolicy);
    }
}
