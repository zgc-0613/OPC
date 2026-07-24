package com.opc.platform.ai.security;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ProviderEndpointPolicy {

    private final Set<String> trustedOrigins;
    private final HostResolver resolver;

    public ProviderEndpointPolicy(Set<String> trustedOrigins, HostResolver resolver) {
        this.trustedOrigins = trustedOrigins.stream()
                .map(this::normalizeOrigin)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        this.resolver = resolver;
    }

    public ValidatedEndpoint validate(String provider, String baseUrl) {
        if (!StringUtils.hasText(provider)) {
            throw invalid();
        }
        URI uri = parse(baseUrl);
        validateUri(uri);
        return new ValidatedEndpoint(uri, origin(uri));
    }

    public void validateRequestUri(URI uri) {
        validateUri(uri);
    }

    private void validateUri(URI uri) {
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw invalid();
        }
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            throw invalid();
        }
        String origin = origin(uri);
        if (!trustedOrigins.contains(origin)) {
            throw invalid();
        }
        List<InetAddress> addresses;
        try {
            addresses = resolver.resolve(uri.getHost());
        } catch (Exception exception) {
            throw invalid();
        }
        if (addresses == null || addresses.isEmpty() || addresses.stream().anyMatch(this::blocked)) {
            throw invalid();
        }
    }

    private URI parse(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!uri.isAbsolute()) {
                throw invalid();
            }
            return uri.normalize();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private boolean blocked(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 198 && (second == 18 || second == 19))
                    || first >= 224;
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return true;
    }

    private String origin(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        return "https://" + host + (port == -1 || port == 443 ? "" : ":" + port);
    }

    private String normalizeOrigin(String value) {
        URI uri = URI.create(value.trim());
        return origin(uri);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "AI provider 地址不受信任");
    }

    public record ValidatedEndpoint(URI baseUri, String origin) {
    }

    @FunctionalInterface
    public interface HostResolver {
        List<InetAddress> resolve(String host) throws Exception;

        static HostResolver system() {
            return host -> Arrays.asList(InetAddress.getAllByName(host));
        }
    }
}
