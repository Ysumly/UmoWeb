package com.ysumly.umowebbackend.common.exception;

/** 404 — 资源不存在 */
public class NotFoundException extends BusinessException {
    public NotFoundException(String message) {
        super(404, message);
    }
}
