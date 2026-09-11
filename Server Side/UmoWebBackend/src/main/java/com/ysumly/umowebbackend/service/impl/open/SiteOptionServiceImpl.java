package com.ysumly.umowebbackend.service.impl.open;

import com.ysumly.umowebbackend.mapper.SiteOptionMapper;
import com.ysumly.umowebbackend.model.entity.SiteOption;
import com.ysumly.umowebbackend.model.vo.SiteInfoVO;
import com.ysumly.umowebbackend.service.open.SiteOptionService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SiteOptionServiceImpl implements SiteOptionService {

    private final SiteOptionMapper siteOptionMapper;

    public SiteOptionServiceImpl(SiteOptionMapper siteOptionMapper) {
        this.siteOptionMapper = siteOptionMapper;
    }

    @Override
    public SiteInfoVO getSiteInfo() {
        SiteInfoVO vo = new SiteInfoVO();
        for (SiteOption opt : siteOptionMapper.findAll()) {
            switch (opt.getOptionKey()) {
                case "site_title" -> vo.setSiteTitle(opt.getOptionValue());
                case "site_subtitle" -> vo.setSiteSubtitle(opt.getOptionValue());
                case "about_page" -> vo.setAboutHtml(opt.getOptionValue());
                case "project_page" -> vo.setProjectHtml(opt.getOptionValue());
            }
        }
        return vo;
    }

    @Override
    public String getPage(String optionKey) {
        SiteOption opt = siteOptionMapper.findByKey(optionKey);
        return opt != null ? opt.getOptionValue() : "";
    }

    @Override
    public Map<String, String> listAll() {
        Map<String, String> result = new LinkedHashMap<>();
        for (SiteOption opt : siteOptionMapper.findAll()) {
            result.put(opt.getOptionKey(), opt.getOptionValue());
        }
        return result;
    }

    @Override
    public void updateOption(String key, String value) {
        siteOptionMapper.upsert(key, value);
    }
}
