package com.opc.platform.ai.provider;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.time.Duration;

public record AiHttpRequest(
        String method,
        URI uri,
        HttpHeaders headers,
        String body,
        Duration timeout
) {
    public AiHttpRequest(URI uri, HttpHeaders headers, String body, Duration timeout) {
        this("POST", uri, headers, body, timeout);
    }
}
