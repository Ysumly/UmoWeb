package com.ysumly.umowebbackend.controller.open;

import com.ysumly.umowebbackend.model.vo.SiteInfoVO;
import com.ysumly.umowebbackend.service.open.SiteOptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class SiteController {

    private final SiteOptionService siteOptionService;

    public SiteController(SiteOptionService siteOptionService) {
        this.siteOptionService = siteOptionService;
    }

    @GetMapping("/site-info")
    public ResponseEntity<SiteInfoVO> getSiteInfo() {
        return ResponseEntity.ok(siteOptionService.getSiteInfo());
    }

    @GetMapping("/pages/about")
    public ResponseEntity<Map<String, String>> getAbout() {
        return ResponseEntity.ok(Map.of("content", siteOptionService.getPage("about_page")));
    }

    @GetMapping("/pages/project")
    public ResponseEntity<Map<String, String>> getProject() {
        return ResponseEntity.ok(Map.of("content", siteOptionService.getPage("project_page")));
    }
}
