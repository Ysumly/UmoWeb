package com.ysumly.umowebbackend.controller.admin;

import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.dto.ContentSaveRequest;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.vo.ContentDetailVO;
import com.ysumly.umowebbackend.model.vo.ContentListVO;
import com.ysumly.umowebbackend.service.admin.ContentManageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class ContentManageController {

    private final ContentManageService contentManageService;

    public ContentManageController(ContentManageService contentManageService) {
        this.contentManageService = contentManageService;
    }

    @GetMapping("/contents")
    public ResponseEntity<PageResult<ContentListVO>> list(@Valid ContentQuery query) {
        return ResponseEntity.ok(contentManageService.list(query));
    }

    @GetMapping("/contents/{id}")
    public ResponseEntity<ContentDetailVO> detail(@PathVariable Long id) {
        return ResponseEntity.ok(contentManageService.getById(id));
    }

    @PostMapping("/contents")
    public ResponseEntity<ContentDetailVO> create(@Valid @RequestBody ContentSaveRequest request) {
        return ResponseEntity.ok(contentManageService.create(request));
    }

    @PutMapping("/contents/{id}")
    public ResponseEntity<ContentDetailVO> update(@PathVariable Long id,
                                                   @Valid @RequestBody ContentSaveRequest request) {
        return ResponseEntity.ok(contentManageService.update(id, request));
    }

    @DeleteMapping("/contents/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contentManageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
