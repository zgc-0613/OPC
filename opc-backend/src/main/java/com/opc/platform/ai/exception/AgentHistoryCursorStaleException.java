package com.opc.platform.ai.exception;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class AgentHistoryCursorStaleException extends BusinessException {

    private final String diagnosticCode = "HISTORY_CURSOR_STALE";

    public AgentHistoryCursorStaleException() {
        super(ErrorCode.CONFLICT, "历史记录已更新，请重新加载");
    }
}
