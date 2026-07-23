package com.opc.platform.ai.provider;

import java.io.IOException;

public interface AiHttpTransport {

    AiHttpResponse execute(AiHttpRequest request) throws IOException, InterruptedException;
}
