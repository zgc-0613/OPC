package com.opc.platform.ai.config;

import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiProviderConfiguration.class);

    @Test
    void providerIsDisabledByDefault() {
        contextRunner.run(context -> {
            AiClient client = context.getBean(AiClient.class);

            assertFalse(client.descriptor().available());
            assertEquals("disabled", client.descriptor().provider());
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> client.generate(request())
            );
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
        });
    }

    @Test
    void fakeProviderMustBeEnabledExplicitly() {
        contextRunner
                .withPropertyValues(
                        "opc.ai.provider=fake",
                        "opc.ai.model=contract-test-model",
                        "opc.ai.allow-fake=true"
                )
                .run(context -> {
                    AiClient client = context.getBean(AiClient.class);

                    assertTrue(client.descriptor().available());
                    assertEquals("fake", client.descriptor().provider());
                    assertEquals("contract-test-model", client.descriptor().model());
                    assertEquals("{}", client.generate(request()).content());
                });
    }

    @Test
    void fakeProviderCannotBeActivatedWithoutTestOnlyGate() {
        contextRunner
                .withPropertyValues("opc.ai.provider=fake", "opc.ai.model=must-not-run")
                .run(context -> assertFalse(context.getBean(AiClient.class).descriptor().available()));
    }

    private AiProviderRequest request() {
        return new AiProviderRequest(
                "case-analysis",
                "case-analysis-v1",
                "Use only supplied evidence.",
                "Analyze case 1.",
                "{\"type\":\"object\"}"
        );
    }
}
