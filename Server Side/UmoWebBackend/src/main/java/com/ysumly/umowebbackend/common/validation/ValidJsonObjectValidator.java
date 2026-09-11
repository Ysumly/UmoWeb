package com.ysumly.umowebbackend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import tools.jackson.databind.ObjectMapper;

public class ValidJsonObjectValidator implements ConstraintValidator<ValidJsonObject, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            return OBJECT_MAPPER.readTree(value).isObject();
        } catch (Exception e) {
            return false;
        }
    }
}
