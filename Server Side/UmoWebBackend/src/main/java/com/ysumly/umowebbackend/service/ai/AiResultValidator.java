package com.ysumly.umowebbackend.service.ai;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;

public interface AiResultValidator {

    void validate(
            AiValidationProfile profile,
            String source,
            String result,
            int maxOutputChars
    );
}
