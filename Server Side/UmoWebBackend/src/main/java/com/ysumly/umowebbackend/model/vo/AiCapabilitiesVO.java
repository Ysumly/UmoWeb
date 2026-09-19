package com.ysumly.umowebbackend.model.vo;

import java.util.List;

public record AiCapabilitiesVO(
        boolean enabled,
        int maxInputChars,
        List<AiCapabilityModeVO> modes
) {
}
