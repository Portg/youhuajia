package com.youhua.common.config;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.common.response.ErrorResponse;
import com.youhua.infra.log.filter.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ErrorResponse> handleBizException(BizException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        log.warn("BizException: code={}, message={}, traceId={}", ex.getErrorCode().getCode(), ex.getMessage(), traceId);

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getErrorCode().getCode())
                        .message(ex.getMessage())
                        .status(ex.getErrorCode().getStatus())
                        .build())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        List<Object> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .map(s -> (Object) s)
                .toList();

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_FAILED.getCode())
                        .message("参数校验失败")
                        .status(ErrorCode.VALIDATION_FAILED.getStatus())
                        .details(details)
                        .build())
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        log.warn("Request body parse failed, traceId={}", traceId, ex);

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_FAILED.getCode())
                        .message("请求体格式错误")
                        .status("INVALID_ARGUMENT")
                        .build())
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(HttpStatus.NOT_FOUND.value())
                        .message("接口不存在")
                        .status("NOT_FOUND")
                        .build())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(HttpStatus.METHOD_NOT_ALLOWED.value())
                        .message("请求方法不支持: " + ex.getMethod())
                        .status("INVALID_ARGUMENT")
                        .build())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        log.error("Data integrity violation, traceId={}", traceId, ex);

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ErrorCode.DB_ERROR.getCode())
                        .message(ErrorCode.DB_ERROR.getDefaultMessage())
                        .status(ErrorCode.DB_ERROR.getStatus())
                        .build())
                .traceId(traceId)
                .build();

        return ResponseEntity.internalServerError().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = getTraceId(request);
        log.error("Unexpected error, traceId={}", traceId, ex);

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ErrorCode.SYSTEM_BUSY.getCode())
                        .message(ErrorCode.SYSTEM_BUSY.getDefaultMessage())
                        .status(ErrorCode.SYSTEM_BUSY.getStatus())
                        .build())
                .traceId(traceId)
                .build();

        return ResponseEntity.internalServerError().body(response);
    }

    private String getTraceId(HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        if (traceId != null) {
            return traceId;
        }
        // fallback: 直接从 header 读取（Filter 未生效的场景）
        traceId = request.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        return traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "");
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
