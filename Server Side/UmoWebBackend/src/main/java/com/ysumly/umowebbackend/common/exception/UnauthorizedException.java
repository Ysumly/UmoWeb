package com.ysumly.umowebbackend.common.exception;

/** 401 — JWT 缺失、无效或过期 */
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String message) {
        super(401, message);
    }
}
