package com.opc.platform.ai.service;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class AgentOrchestratorException extends BusinessException {

    private final String diagnosticCode;

    public AgentOrchestratorException(ErrorCode errorCode, String diagnosticCode, String message) {
        super(errorCode, message);
        this.diagnosticCode = diagnosticCode;
    }
}
