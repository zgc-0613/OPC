package com.opc.platform.ai.provider;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagedAiClientTest {

    @Test
    void descriptorAndGenerationFollowCurrentPersistedState() {
        AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);
        AiProviderFactory factory = mock(AiProviderFactory.class);
        ManagedAiClient managed = new ManagedAiClient(settingsProvider, factory);

        when(settingsProvider.current()).thenReturn(settings(false));
        assertFalse(managed.descriptor().available());

        AiRuntimeSettings enabled = settings(true);
        AiClient delegate = mock(AiClient.class);
        when(settingsProvider.current()).thenReturn(enabled);
        when(factory.create(enabled)).thenReturn(delegate);
        when(delegate.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(delegate.generate(request())).thenReturn(new AiProviderResponse("{}", 1, 1, 2, 10, "req-1"));

        assertTrue(managed.descriptor().available());
        assertEquals("deepseek", managed.descriptor().provider());
        assertEquals(2, managed.generate(request()).totalTokens());
    }

    private AiRuntimeSettings settings(boolean enabled) {
        return new AiRuntimeSettings(
                enabled ? "deepseek" : "disabled",
                "openai_compatible",
                enabled ? "https://api.example.com/v1" : null,
                enabled ? "configured-model" : "unconfigured",
                enabled ? "key" : null,
                0.2,
                1200,
                Duration.ofSeconds(30),
                1,
                enabled
        );
    }

    private AiProviderRequest request() {
        return new AiProviderRequest("case-analysis", "case-analysis-v1", "system", "user", "{}");
    }
}
