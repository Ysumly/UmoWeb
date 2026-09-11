package com.ysumly.umowebbackend.service.open;

import com.ysumly.umowebbackend.model.vo.SiteInfoVO;
import java.util.Map;

public interface SiteOptionService {
    SiteInfoVO getSiteInfo();
    String getPage(String optionKey);

    // 管理端用
    Map<String, String> listAll();
    void updateOption(String key, String value);
}
