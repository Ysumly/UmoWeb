package com.ysumly.umowebbackend.controller.admin;

import com.ysumly.umowebbackend.model.dto.OptionSaveRequest;
import com.ysumly.umowebbackend.service.open.SiteOptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class OptionController {

    private final SiteOptionService siteOptionService;

    public OptionController(SiteOptionService siteOptionService) {
        this.siteOptionService = siteOptionService;
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, String>> listAll() {
        return ResponseEntity.ok(siteOptionService.listAll());
    }

    @PutMapping("/options/{key}")
    public ResponseEntity<Void> update(@PathVariable String key,
                                        @Valid @RequestBody OptionSaveRequest request) {
        siteOptionService.updateOption(key, request.getValue());
        return ResponseEntity.noContent().build();
    }
}
