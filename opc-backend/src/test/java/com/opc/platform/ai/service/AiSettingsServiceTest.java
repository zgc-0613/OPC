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
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiHttpRequest;
import com.opc.platform.ai.provider.AiHttpResponse;
import com.opc.platform.ai.provider.AiHttpTransport;
import com.opc.platform.ai.security.AesGcmSecretCipher;
import com.opc.platform.ai.security.ProviderEndpointPolicy;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.KeyGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.net.http.HttpHeaders;
import java.net.InetAddress;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSettingsServiceTest {

    @Test
    void agentRuntimeSettingsRejectUnsafeLimitsBeforePersistence() throws Exception {
        AiModelSettingsMapper settingsMapper = mock(AiModelSettingsMapper.class);
        AiSettingsService service = new AiSettingsService(
                settingsMapper, mock(AiSettingsAuditMapper.class), new AesGcmSecretCipher(masterKey()),
                mock(AiProviderFactory.class), new ObjectMapper(), mock(AiHttpTransport.class), endpointPolicy()
        );
        AiModelSettingsUpdateDTO dto = dto("sk-test");
        dto.setAgentMaxModelRounds(9);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.update(dto, new AuthenticatedAdmin(7L, "ACha_"))
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(settingsMapper, never()).insert(any(AiModelSettings.class));
    }

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
                mock(AiHttpTransport.class),
                endpointPolicy()
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
                transport,
                endpointPolicy()
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
                transport,
                endpointPolicy()
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

    @Test
    void storedKeyCannotBeSentToDifferentOriginDuringModelDiscovery() throws Exception {
        AiModelSettingsMapper settingsMapper = mock(AiModelSettingsMapper.class);
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
        AiHttpTransport transport = mock(AiHttpTransport.class);
        org.mockito.Mockito.when(settingsMapper.selectById(1L)).thenReturn(stored(cipher, "https://api.deepseek.com/v1"));
        AiSettingsService service = new AiSettingsService(
                settingsMapper,
                mock(AiSettingsAuditMapper.class),
                cipher,
                mock(AiProviderFactory.class),
                new ObjectMapper(),
                transport,
                endpointPolicy()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.discoverModels(discovery("https://api.example.com/v1", null), new AuthenticatedAdmin(7L, "ACha_"))
        );

        assertEquals("更换 Provider 或 API Base URL 后必须重新输入 API Key", exception.getMessage());
        verify(transport, never()).execute(org.mockito.ArgumentMatchers.<AiHttpRequest>any());
    }

    @Test
    void changingBaseUrlWithoutNewKeyIsRejectedBeforePersisting() throws Exception {
        AiModelSettingsMapper settingsMapper = mock(AiModelSettingsMapper.class);
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
        org.mockito.Mockito.when(settingsMapper.selectById(1L)).thenReturn(stored(cipher, "https://api.deepseek.com/v1"));
        AiSettingsService service = new AiSettingsService(
                settingsMapper,
                mock(AiSettingsAuditMapper.class),
                cipher,
                mock(AiProviderFactory.class),
                new ObjectMapper(),
                mock(AiHttpTransport.class),
                endpointPolicy()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.update(dto(null, "https://api.example.com/v1"), new AuthenticatedAdmin(7L, "ACha_"))
        );

        assertEquals("更换 Provider 或 API Base URL 后必须重新输入 API Key", exception.getMessage());
        verify(settingsMapper, never()).updateById(org.mockito.ArgumentMatchers.<AiModelSettings>any());
    }

    @Test
    void discoveryFailureDoesNotExposeTransientApiKey() throws Exception {
        AiHttpTransport transport = request -> {
            throw new IOException("connection failed for Bearer sk-should-not-escape");
        };
        AiSettingsService service = new AiSettingsService(
                mock(AiModelSettingsMapper.class),
                mock(AiSettingsAuditMapper.class),
                new AesGcmSecretCipher(masterKey()),
                mock(AiProviderFactory.class),
                new ObjectMapper(),
                transport,
                endpointPolicy()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.discoverModels(discovery("https://api.example.com/v1", "sk-should-not-escape"), new AuthenticatedAdmin(7L, "ACha_"))
        );

        assertFalse(exception.getMessage().contains("sk-should-not-escape"));
    }

    @Test
    void connectionTestRequiresAndParsesCompleteJsonAcknowledgement() throws Exception {
        AiModelSettingsMapper settingsMapper = mock(AiModelSettingsMapper.class);
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
        AiModelSettings stored = runnableSettings(cipher);
        when(settingsMapper.selectById(1L)).thenReturn(stored);
        AiProviderFactory factory = mock(AiProviderFactory.class);
        AiClient client = mock(AiClient.class);
        when(factory.create(any(AiRuntimeSettings.class))).thenReturn(client);
        when(client.generate(any(AiProviderRequest.class))).thenReturn(new AiProviderResponse("{\"ok\":true}"));
        AiSettingsService service = new AiSettingsService(
                settingsMapper, mock(AiSettingsAuditMapper.class), cipher, factory,
                new ObjectMapper(), mock(AiHttpTransport.class), endpointPolicy()
        );

        var result = service.testConnection(new AuthenticatedAdmin(7L, "ACha_"));

        assertTrue(result.isSuccess());
        ArgumentCaptor<AiRuntimeSettings> runtime = ArgumentCaptor.forClass(AiRuntimeSettings.class);
        verify(factory).create(runtime.capture());
        assertTrue(runtime.getValue().maxOutputTokens() >= 32);
        ArgumentCaptor<AiProviderRequest> request = ArgumentCaptor.forClass(AiProviderRequest.class);
        verify(client).generate(request.capture());
        assertTrue(request.getValue().userPrompt().contains("{\"ok\":true}"));
    }

    @Test
    void connectionTestRejectsEmptyMalformedAndIncorrectJson() throws Exception {
        for (String content : List.of("", "not-json", "{\"ok\":false}", "{\"status\":\"ok\"}")) {
            AiModelSettingsMapper settingsMapper = mock(AiModelSettingsMapper.class);
            AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
            when(settingsMapper.selectById(1L)).thenReturn(runnableSettings(cipher));
            AiProviderFactory factory = mock(AiProviderFactory.class);
            AiClient client = mock(AiClient.class);
            when(factory.create(any(AiRuntimeSettings.class))).thenReturn(client);
            when(client.generate(any(AiProviderRequest.class))).thenReturn(new AiProviderResponse(content));
            AiSettingsService service = new AiSettingsService(
                    settingsMapper, mock(AiSettingsAuditMapper.class), cipher, factory,
                    new ObjectMapper(), mock(AiHttpTransport.class), endpointPolicy()
            );

            assertThrows(
                    BusinessException.class,
                    () -> service.testConnection(new AuthenticatedAdmin(7L, "ACha_")),
                    "Expected invalid acknowledgement to fail: " + content
            );
        }
    }

    @Test
    void connectionTestSanitizesTimeoutAndRedirectErrors() throws Exception {
        for (String upstreamMessage : List.of(
                "timeout using Bearer sk-connection-secret",
                "redirect rejected for sk-connection-secret"
        )) {
            AiModelSettingsMapper settingsMapper = mock(AiModelSettingsMapper.class);
            AesGcmSecretCipher cipher = new AesGcmSecretCipher(masterKey());
            when(settingsMapper.selectById(1L)).thenReturn(runnableSettings(cipher));
            AiProviderFactory factory = mock(AiProviderFactory.class);
            AiClient client = mock(AiClient.class);
            when(factory.create(any(AiRuntimeSettings.class))).thenReturn(client);
            when(client.generate(any(AiProviderRequest.class))).thenThrow(
                    new BusinessException(com.opc.platform.common.enums.ErrorCode.SERVICE_UNAVAILABLE, upstreamMessage)
            );
            AiSettingsService service = new AiSettingsService(
                    settingsMapper, mock(AiSettingsAuditMapper.class), cipher, factory,
                    new ObjectMapper(), mock(AiHttpTransport.class), endpointPolicy()
            );

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.testConnection(new AuthenticatedAdmin(7L, "ACha_"))
            );
            assertFalse(exception.getMessage().contains("sk-connection-secret"));
        }
    }

    private AiModelSettingsUpdateDTO dto(String apiKey) {
        return dto(apiKey, "https://api.example.com/v1");
    }

    private AiModelSettingsUpdateDTO dto(String apiKey, String apiBaseUrl) {
        AiModelSettingsUpdateDTO dto = new AiModelSettingsUpdateDTO();
        dto.setProvider("deepseek");
        dto.setApiFormat("openai_compatible");
        dto.setApiBaseUrl(apiBaseUrl);
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

    private AiModelDiscoveryRequestDTO discovery(String apiBaseUrl, String apiKey) {
        AiModelDiscoveryRequestDTO request = new AiModelDiscoveryRequestDTO();
        request.setProvider("deepseek");
        request.setApiFormat("openai_compatible");
        request.setApiBaseUrl(apiBaseUrl);
        request.setApiKey(apiKey);
        request.setTimeoutSeconds(15);
        return request;
    }

    private AiModelSettings stored(AesGcmSecretCipher cipher, String apiBaseUrl) {
        AiModelSettings settings = new AiModelSettings();
        settings.setId(1L);
        settings.setProvider("deepseek");
        settings.setApiFormat("openai_compatible");
        settings.setApiBaseUrl(apiBaseUrl);
        settings.setApiKeyCiphertext(cipher.encrypt("sk-stored-key"));
        settings.setApiKeyProvider("deepseek");
        settings.setApiKeyOrigin("https://api.deepseek.com");
        return settings;
    }

    private AiModelSettings runnableSettings(AesGcmSecretCipher cipher) {
        AiModelSettings settings = stored(cipher, "https://api.deepseek.com/v1");
        settings.setModelId("configured-model");
        settings.setEnabled(true);
        settings.setTemperature(0.2);
        settings.setMaxOutputTokens(1200);
        settings.setTimeoutSeconds(30);
        settings.setRetryCount(1);
        settings.setDailyTokenQuota(100_000L);
        return settings;
    }

    private ProviderEndpointPolicy endpointPolicy() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("8.8.8.8");
        return new ProviderEndpointPolicy(
                Set.of("https://api.deepseek.com", "https://api.example.com"),
                host -> List.of(publicAddress)
        );
    }

    private String masterKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
    }
}
