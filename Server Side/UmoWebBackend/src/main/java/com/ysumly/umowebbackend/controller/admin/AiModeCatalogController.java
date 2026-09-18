package com.ysumly.umowebbackend.controller.admin;

import com.ysumly.umowebbackend.model.dto.AiModeCopyRequest;
import com.ysumly.umowebbackend.model.dto.AiModeCreateRequest;
import com.ysumly.umowebbackend.model.dto.AiModeUpdateRequest;
import com.ysumly.umowebbackend.model.vo.AiModeSettingsVO;
import com.ysumly.umowebbackend.model.vo.AiModeVersionVO;
import com.ysumly.umowebbackend.service.admin.AiModeCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
public class AiModeCatalogController {

    private final AiModeCatalogService aiModeCatalogService;

    public AiModeCatalogController(AiModeCatalogService aiModeCatalogService) {
        this.aiModeCatalogService = aiModeCatalogService;
    }

    @GetMapping("/modes")
    public ResponseEntity<List<AiModeSettingsVO>> list() {
        return ResponseEntity.ok(aiModeCatalogService.list());
    }

    @PostMapping("/modes")
    public ResponseEntity<AiModeSettingsVO> create(
            @Valid @RequestBody AiModeCreateRequest request) {
        return ResponseEntity.ok(aiModeCatalogService.create(request));
    }

    @PostMapping("/modes/{id}/copy")
    public ResponseEntity<AiModeSettingsVO> copy(
            @PathVariable Long id,
            @Valid @RequestBody AiModeCopyRequest request) {
        return ResponseEntity.ok(aiModeCatalogService.copy(id, request));
    }

    @PutMapping("/modes/{id}")
    public ResponseEntity<AiModeSettingsVO> update(
            @PathVariable Long id,
            @Valid @RequestBody AiModeUpdateRequest request) {
        return ResponseEntity.ok(aiModeCatalogService.update(id, request));
    }

    @GetMapping("/modes/{id}/versions")
    public ResponseEntity<List<AiModeVersionVO>> listVersions(@PathVariable Long id) {
        return ResponseEntity.ok(aiModeCatalogService.listVersions(id));
    }

    @PostMapping("/modes/{id}/rollback/{versionNo}")
    public ResponseEntity<AiModeSettingsVO> rollback(
            @PathVariable Long id,
            @PathVariable int versionNo,
            @Valid @RequestBody RollbackRequest request) {
        return ResponseEntity.ok(
                aiModeCatalogService.rollback(id, versionNo, request.expectedVersion()));
    }

    public record RollbackRequest(
            @Min(1)
            int expectedVersion
    ) {
    }
}
