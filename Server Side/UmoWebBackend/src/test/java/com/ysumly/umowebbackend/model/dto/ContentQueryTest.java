package com.ysumly.umowebbackend.model.dto;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentQueryTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

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

    @Test
    void descendantFilterRequiresCategoryId() {
        ContentQuery query = new ContentQuery();
        query.setIncludeDescendants(true);

        assertThat(validator.validate(query))
                .extracting(violation -> violation.getMessage())
                .containsExactly("includeDescendants 需要同时提供 categoryId");
    }

    @Test
    void exactCategoryFilterDoesNotRequireIncludeDescendants() {
        ContentQuery query = new ContentQuery();
        query.setCategoryId(1L);

        assertThat(validator.validate(query)).isEmpty();
    }
}
