package com.opc.platform.ai.provider;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class AiProviderException extends BusinessException {

    private final String diagnosticCode;

    public AiProviderException(String diagnosticCode, String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
        this.diagnosticCode = diagnosticCode;
    }
}
