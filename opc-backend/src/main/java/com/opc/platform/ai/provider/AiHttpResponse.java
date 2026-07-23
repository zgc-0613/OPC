package com.opc.platform.ai.provider;

import java.net.http.HttpHeaders;

public record AiHttpResponse(
        int statusCode,
        String body,
        HttpHeaders headers
) {
}
