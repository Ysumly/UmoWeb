package com.ysumly.umowebbackend.common.exception;

import com.ysumly.umowebbackend.model.vo.BulkContentFailureVO;

import java.util.List;

public class BulkOperationException extends BusinessException {

    private final List<BulkContentFailureVO> failures;

    public BulkOperationException(int code, String message, List<BulkContentFailureVO> failures) {
        super(code, message);
        this.failures = List.copyOf(failures);
    }

    public List<BulkContentFailureVO> getFailures() {
        return failures;
    }
}
