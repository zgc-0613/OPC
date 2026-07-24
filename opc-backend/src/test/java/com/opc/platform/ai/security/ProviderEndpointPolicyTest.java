package com.opc.platform.ai.security;

import com.opc.platform.ai.provider.AiHttpRequest;
import com.opc.platform.ai.provider.AiHttpResponse;
import com.opc.platform.ai.provider.AiHttpTransport;
import com.opc.platform.ai.provider.ValidatingAiHttpTransport;
import com.opc.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderEndpointPolicyTest {

    @Test
    void acceptsOnlyHttpsTrustedPublicProviderOrigin() throws Exception {
        ProviderEndpointPolicy policy = policy("8.8.8.8");

        var endpoint = policy.validate("deepseek", "https://api.deepseek.com/v1");

        assertEquals("https://api.deepseek.com", endpoint.origin());
        assertEquals("/v1", endpoint.baseUri().getPath());
    }

    @Test
    void rejectsHttpUserInfoUntrustedAndLocalTargets() throws Exception {
        ProviderEndpointPolicy publicPolicy = policy("8.8.8.8");
        ProviderEndpointPolicy privatePolicy = policy("10.0.0.7");

        assertThrows(BusinessException.class, () -> publicPolicy.validate("deepseek", "http://api.deepseek.com/v1"));
        assertThrows(BusinessException.class, () -> publicPolicy.validate("deepseek", "https://user:pass@api.deepseek.com/v1"));
        assertThrows(BusinessException.class, () -> publicPolicy.validate("deepseek", "https://example.com/v1"));
        assertThrows(BusinessException.class, () -> privatePolicy.validate("deepseek", "https://api.deepseek.com/v1"));
    }

    @Test
    void rejectsCloudMetadataAndIpv6LocalAddresses() throws Exception {
        ProviderEndpointPolicy metadata = policy("169.254.169.254");
        ProviderEndpointPolicy ipv6Local = policy("fd00::1");

        assertThrows(BusinessException.class, () -> metadata.validate("deepseek", "https://api.deepseek.com/v1"));
        assertThrows(BusinessException.class, () -> ipv6Local.validate("deepseek", "https://api.deepseek.com/v1"));
    }

    @Test
    void validatingTransportResolvesAndChecksEveryRequestBeforeSendingCredentials() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        ProviderEndpointPolicy policy = new ProviderEndpointPolicy(
                Set.of("https://api.deepseek.com"),
                host -> {
                    resolutions.incrementAndGet();
                    return List.of(InetAddress.getByName("8.8.8.8"));
                }
        );
        AiHttpTransport delegate = request -> {
            calls.incrementAndGet();
            return new AiHttpResponse(200, "{}", HttpHeaders.of(Map.of(), (name, value) -> true));
        };
        ValidatingAiHttpTransport transport = new ValidatingAiHttpTransport(delegate, policy);
        AiHttpRequest request = new AiHttpRequest(
                "GET",
                URI.create("https://api.deepseek.com/v1/models"),
                HttpHeaders.of(Map.of("Authorization", List.of("Bearer secret")), (name, value) -> true),
                "",
                Duration.ofSeconds(5)
        );

        transport.execute(request);
        transport.execute(request);

        assertEquals(2, resolutions.get());
        assertEquals(2, calls.get());
    }

    private ProviderEndpointPolicy policy(String address) throws Exception {
        InetAddress resolved = InetAddress.getByName(address);
        return new ProviderEndpointPolicy(
                Set.of("https://api.deepseek.com"),
                host -> List.of(resolved)
        );
    }
}
