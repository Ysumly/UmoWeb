package com.ysumly.umowebbackend.service.admin;

import com.ysumly.umowebbackend.model.dto.AiTransformRequest;
import com.ysumly.umowebbackend.model.vo.AiTransformResultVO;

public interface AiTransformService {

    AiTransformResultVO transform(AiTransformRequest request);
}
