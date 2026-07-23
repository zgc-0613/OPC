package com.opc.platform.ai.provider;

public record AiProviderDescriptor(
        String provider,
        String model,
        boolean available
) {
}
