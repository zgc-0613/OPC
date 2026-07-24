package com.opc.platform.ai.provider;

import com.opc.platform.ai.security.ProviderEndpointPolicy;

import java.io.IOException;

public class ValidatingAiHttpTransport implements AiHttpTransport {

    private final AiHttpTransport delegate;
    private final ProviderEndpointPolicy endpointPolicy;

    public ValidatingAiHttpTransport(AiHttpTransport delegate, ProviderEndpointPolicy endpointPolicy) {
        this.delegate = delegate;
        this.endpointPolicy = endpointPolicy;
    }

    @Override
    public AiHttpResponse execute(AiHttpRequest request) throws IOException, InterruptedException {
        endpointPolicy.validateRequestUri(request.uri());
        return delegate.execute(request);
    }
}
