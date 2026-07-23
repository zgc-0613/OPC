package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.AiModelDiscoveryRequestDTO;
import com.opc.platform.ai.dto.AiModelOptionDTO;
import com.opc.platform.ai.dto.AiModelSettingsUpdateDTO;
import com.opc.platform.ai.entity.AiModelSettings;
import com.opc.platform.ai.mapper.AiModelSettingsMapper;
import com.opc.platform.ai.mapper.AiSettingsAuditMapper;
import com.opc.platform.ai.provider.AiProviderFactory;
import com.opc.platform.ai.provider.AiHttpRequest;
import com.opc.platform.ai.provider.AiHttpResponse;
import com.opc.platform.ai.provider.AiHttpTransport;
import com.opc.platform.ai.security.AesGcmSecretCipher;
import com.opc.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.KeyGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.net.http.HttpHeaders;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AiSettingsServiceTest {

    @Test
    void apiKeyIsEncryptedAtRestAndOnlyConfigurationFlagIsReturned() throws Exception {
        AiModelSettingsMapper settingsMapper = mock(AiModelSettingsMapper.class);
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
        AiSettingsService service = new AiSettingsService(
                settingsMapper,
                mock(AiSettingsAuditMapper.class),
                cipher,
                mock(AiProviderFactory.class),
                new ObjectMapper(),
                mock(AiHttpTransport.class)
        );
        String apiKey = "sk-sensitive-setting-value";

        var result = service.update(dto(apiKey), new AuthenticatedAdmin(7L, "ACha_"));

        ArgumentCaptor<AiModelSettings> captor = ArgumentCaptor.forClass(AiModelSettings.class);
        verify(settingsMapper).insert(captor.capture());
        String ciphertext = captor.getValue().getApiKeyCiphertext();
        assertFalse(ciphertext.contains(apiKey));
        assertEquals(apiKey, cipher.decrypt(ciphertext));
        assertTrue(result.getApiKeyConfigured());
        assertEquals("ACha_", result.getUpdatedByAdminUsername());
        assertEquals("configured-model", result.getModels().get(0).modelId());
        assertEquals("Configured model", result.getModels().get(0).displayName());
        assertTrue(captor.getValue().getModelCatalogJson().contains("Configured model"));
    }

    @Test
    void transientApiKeyCanDiscoverModelsWithoutBeingPersisted() throws Exception {
        AiModelSettingsMapper settingsMapper = mock(AiModelSettingsMapper.class);
        AtomicReference<AiHttpRequest> capturedRequest = new AtomicReference<>();
        AiHttpTransport transport = request -> {
            capturedRequest.set(request);
            return new AiHttpResponse(
                    200,
                    "{\"data\":[{\"id\":\"model-a\"},{\"id\":\"model-b\"}]}",
                    HttpHeaders.of(Map.of(), (name, value) -> true)
            );
        };
        AiSettingsService service = new AiSettingsService(
                settingsMapper,
                mock(AiSettingsAuditMapper.class),
                new AesGcmSecretCipher(masterKey()),
                mock(AiProviderFactory.class),
                new ObjectMapper(),
                transport
        );
        AiModelDiscoveryRequestDTO request = new AiModelDiscoveryRequestDTO();
        request.setProvider("deepseek");
        request.setApiFormat("openai_compatible");
        request.setApiBaseUrl("https://api.example.com/v1");
        request.setApiKey("sk-transient-only");
        request.setTimeoutSeconds(15);

        List<AiModelOptionDTO> models = service.discoverModels(request, new AuthenticatedAdmin(7L, "ACha_"));

        assertEquals(List.of("model-a", "model-b"), models.stream().map(AiModelOptionDTO::modelId).toList());
        assertEquals("GET", capturedRequest.get().method());
        assertEquals("https://api.example.com/v1/models", capturedRequest.get().uri().toString());
        assertEquals("Bearer sk-transient-only", capturedRequest.get().headers().firstValue("Authorization").orElseThrow());
        verify(settingsMapper, never()).insert(org.mockito.ArgumentMatchers.<AiModelSettings>any());
        verify(settingsMapper, never()).updateById(org.mockito.ArgumentMatchers.<AiModelSettings>any());
    }

    @Test
    void modelDiscoveryRequiresTransientOrEncryptedApiKey() throws Exception {
        AiModelSettingsMapper settingsMapper = mock(AiModelSettingsMapper.class);
        AiHttpTransport transport = mock(AiHttpTransport.class);
        AiSettingsService service = new AiSettingsService(
                settingsMapper,
                mock(AiSettingsAuditMapper.class),
                new AesGcmSecretCipher(masterKey()),
                mock(AiProviderFactory.class),
                new ObjectMapper(),
                transport
        );
        AiModelDiscoveryRequestDTO request = new AiModelDiscoveryRequestDTO();
        request.setProvider("deepseek");
        request.setApiFormat("openai_compatible");
        request.setApiBaseUrl("https://api.deepseek.com/v1");
        request.setTimeoutSeconds(15);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.discoverModels(request, new AuthenticatedAdmin(7L, "ACha_"))
        );

        assertEquals("请先填写 API Key 或保存密钥", exception.getMessage());
        verify(transport, never()).execute(org.mockito.ArgumentMatchers.<AiHttpRequest>any());
    }

    private AiModelSettingsUpdateDTO dto(String apiKey) {
        AiModelSettingsUpdateDTO dto = new AiModelSettingsUpdateDTO();
        dto.setProvider("deepseek");
        dto.setApiFormat("openai_compatible");
        dto.setApiBaseUrl("https://api.example.com/v1");
        dto.setModelId("configured-model");
        dto.setModels(List.of(new AiModelOptionDTO("configured-model", "Configured model")));
        dto.setApiKey(apiKey);
        dto.setTemperature(0.2);
        dto.setMaxOutputTokens(1200);
        dto.setTimeoutSeconds(30);
        dto.setRetryCount(1);
        dto.setDailyTokenQuota(100_000L);
        dto.setEnabled(false);
        return dto;
    }

    private String masterKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
    }
}
