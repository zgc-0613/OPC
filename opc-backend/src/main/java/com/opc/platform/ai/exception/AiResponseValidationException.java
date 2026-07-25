package com.opc.platform.ai.exception;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

@Getter
public class AiResponseValidationException extends BusinessException {

    private static final Set<String> ALLOWED_CODES = Set.of(
            "INVALID_JSON",
            "MISSING_FIELD",
            "INVALID_CONFIDENCE",
            "MISSING_CITATIONS",
            "UNKNOWN_SOURCE_ID",
            "BLANK_CLAIM",
            "TRUNCATED_RESPONSE",
            "CONTENT_FILTERED",
            "ABNORMAL_FINISH_REASON"
    );

    private static final Map<String, String> MESSAGES = Map.of(
            "TRUNCATED_RESPONSE", "模型输出被截断，请重试或联系管理员提高最大输出词元",
            "MISSING_CITATIONS", "模型未返回可核验引用，本次结果已拒绝展示",
            "UNKNOWN_SOURCE_ID", "模型引用了未提供的来源，本次结果已拒绝展示",
            "BLANK_CLAIM", "模型引用缺少所支撑的结论，本次结果已拒绝展示",
            "CONTENT_FILTERED", "模型响应被内容安全策略拦截，请调整问题后重试",
            "ABNORMAL_FINISH_REASON", "模型响应异常结束，请稍后重试",
            "INVALID_JSON", "模型返回的 JSON 格式错误，请稍后重试",
            "MISSING_FIELD", "模型返回内容缺少必要字段，请稍后重试",
            "INVALID_CONFIDENCE", "模型返回的置信度格式错误，请稍后重试"
    );

    private final String diagnosticCode;

    public AiResponseValidationException(String diagnosticCode) {
        super(ErrorCode.UPSTREAM_ERROR, messageFor(diagnosticCode));
        this.diagnosticCode = normalize(diagnosticCode);
    }

    private static String messageFor(String code) {
        return MESSAGES.get(normalize(code));
    }

    private static String normalize(String code) {
        return ALLOWED_CODES.contains(code) ? code : "INVALID_JSON";
    }
}
