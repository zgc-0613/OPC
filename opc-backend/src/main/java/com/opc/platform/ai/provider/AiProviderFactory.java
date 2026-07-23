package com.opc.platform.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiProviderFactory {

    private final ObjectMapper objectMapper;
    private final AiHttpTransport transport;

    public AiClient create(AiRuntimeSettings settings) {
        if (settings == null || !settings.enabled()) {
            return new DisabledAiClient(settings == null ? "unconfigured" : settings.model());
        }
        return new OpenAiCompatibleAiClient(settings, transport, objectMapper);
    }
}
