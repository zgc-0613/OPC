package com.opc.platform.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Resource not found"),
    TOO_MANY_REQUESTS(429, "Too many requests"),
    UPSTREAM_ERROR(502, "Upstream service error"),
    SERVICE_UNAVAILABLE(503, "Service unavailable"),
    INTERNAL_ERROR(500, "Internal server error");

    private final Integer code;

    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
