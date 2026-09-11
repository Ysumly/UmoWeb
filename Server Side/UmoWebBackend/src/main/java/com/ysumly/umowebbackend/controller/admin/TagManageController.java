package com.ysumly.umowebbackend.controller.admin;

import com.ysumly.umowebbackend.model.dto.TagSaveRequest;
import com.ysumly.umowebbackend.model.vo.TagVO;
import com.ysumly.umowebbackend.service.admin.TagManageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class TagManageController {

    private final TagManageService tagManageService;

    public TagManageController(TagManageService tagManageService) {
        this.tagManageService = tagManageService;
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TagVO>> list() {
        return ResponseEntity.ok(tagManageService.getAll());
    }

    @PostMapping("/tags")
    public ResponseEntity<TagVO> create(@Valid @RequestBody TagSaveRequest request) {
        return ResponseEntity.ok(tagManageService.create(request));
    }

    @PutMapping("/tags/{id}")
    public ResponseEntity<TagVO> update(@PathVariable Long id,
                                        @Valid @RequestBody TagSaveRequest request) {
        return ResponseEntity.ok(tagManageService.update(id, request));
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagManageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
