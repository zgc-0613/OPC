package com.opc.platform.ai.provider;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JavaNetAiHttpTransport implements AiHttpTransport {

    private final HttpClient httpClient;

    public JavaNetAiHttpTransport() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    JavaNetAiHttpTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public AiHttpResponse execute(AiHttpRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(request.timeout());
        if ("GET".equalsIgnoreCase(request.method())) {
            builder.GET();
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(request.body()));
        }
        request.headers().map().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new AiHttpResponse(response.statusCode(), response.body(), response.headers());
    }
}
