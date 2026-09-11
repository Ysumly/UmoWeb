package com.ysumly.umowebbackend.common.exception;

/**
 * 业务异常基类，含 HTTP 状态码和用户可读消息。
 * 由 GlobalExceptionHandler 统一处理。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
