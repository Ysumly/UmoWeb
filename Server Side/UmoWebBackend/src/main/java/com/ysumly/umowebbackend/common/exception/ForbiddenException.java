package com.ysumly.umowebbackend.common.exception;

/** 403 — 越权访问 */
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super(403, message);
    }
}
