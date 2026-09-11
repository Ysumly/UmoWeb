package com.ysumly.umowebbackend.model.dto;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentQueryTest {

    @Test
    void offsetOverflowReturnsBadRequest() {
        ContentQuery query = new ContentQuery();
        query.setPage(Integer.MAX_VALUE);
        query.setSize(100);

        assertThatThrownBy(query::getOffset)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }
}
