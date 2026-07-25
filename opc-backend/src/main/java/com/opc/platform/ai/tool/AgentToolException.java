package com.opc.platform.ai.tool;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class AgentToolException extends BusinessException {

    private final String diagnosticCode;

    public AgentToolException(String diagnosticCode, String message) {
        this(ErrorCode.BAD_REQUEST, diagnosticCode, message);
    }

    public AgentToolException(ErrorCode errorCode, String diagnosticCode, String message) {
        super(errorCode, message);
        this.diagnosticCode = diagnosticCode;
    }
}
