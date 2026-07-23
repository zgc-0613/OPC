package com.opc.platform.ai.config;

import com.opc.platform.ai.provider.AiHttpTransport;
import com.opc.platform.ai.provider.JavaNetAiHttpTransport;
import com.opc.platform.ai.security.AesGcmSecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiSettingsConfiguration {

    @Bean
    AesGcmSecretCipher aiSettingsCipher(
            @Value("${opc.ai.settings-master-key:}") String masterKey
    ) {
        return new AesGcmSecretCipher(masterKey);
    }

    @Bean
    AiHttpTransport aiHttpTransport() {
        return new JavaNetAiHttpTransport();
    }
}
