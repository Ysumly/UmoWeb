package com.ysumly.umowebbackend.controller.admin;

import com.ysumly.umowebbackend.config.AiProperties;
import com.ysumly.umowebbackend.model.vo.AiCapabilitiesVO;
import com.ysumly.umowebbackend.model.vo.AiCapabilityModeVO;
import com.ysumly.umowebbackend.model.vo.AiSettingsVO;
import com.ysumly.umowebbackend.service.admin.AiModeCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
public class AiRuntimeController {

    private static final String PROVIDER = "deepseek";

    private final AiProperties properties;
    private final AiModeCatalogService aiModeCatalogService;

    public AiRuntimeController(AiProperties properties,
                               AiModeCatalogService aiModeCatalogService) {
        this.properties = properties;
        this.aiModeCatalogService = aiModeCatalogService;
    }

    @GetMapping("/settings")
    public ResponseEntity<AiSettingsVO> settings() {
        List<?> modes = aiModeCatalogService.list();
        return ResponseEntity.ok(new AiSettingsVO(
                properties.isEnabled(),
                PROVIDER,
                properties.getDeepseek().getModel(),
                properties.getMaxInputChars(),
                properties.getMaxOutputChars(),
                modes.size()));
    }

    @GetMapping("/capabilities")
    public ResponseEntity<AiCapabilitiesVO> capabilities() {
        List<AiCapabilityModeVO> modes = aiModeCatalogService.list().stream()
                .filter(mode -> mode.enabled())
                .map(mode -> new AiCapabilityModeVO(
                        mode.modeKey(),
                        mode.name(),
                        mode.description()))
                .toList();
        return ResponseEntity.ok(new AiCapabilitiesVO(
                properties.isEnabled(),
                properties.getMaxInputChars(),
                modes));
    }
}
