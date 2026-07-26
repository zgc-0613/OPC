package com.opc.platform.common.exception;

import com.opc.platform.ai.exception.AiResponseValidationException;
import com.opc.platform.ai.exception.AgentHistoryCursorStaleException;
import com.opc.platform.ai.vo.AiFailureDetailVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AiResponseValidationException.class)
    public Result<AiFailureDetailVO> handleAiResponseValidationException(
            AiResponseValidationException exception
    ) {
        return new Result<>(
                exception.getErrorCode().getCode(),
                exception.getMessage(),
                new AiFailureDetailVO(exception.getDiagnosticCode())
        );
    }

    @ExceptionHandler(AgentHistoryCursorStaleException.class)
    public Result<AiFailureDetailVO> handleHistoryCursorStaleException(
            AgentHistoryCursorStaleException exception
    ) {
        return new Result<>(
                exception.getErrorCode().getCode(),
                exception.getMessage(),
                new AiFailureDetailVO(exception.getDiagnosticCode())
        );
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        return Result.fail(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        return Result.fail(ErrorCode.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public Result<Void> handleLockConflict(PessimisticLockingFailureException exception) {
        return Result.fail(ErrorCode.CONFLICT, "数据正在被其他操作修改，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
