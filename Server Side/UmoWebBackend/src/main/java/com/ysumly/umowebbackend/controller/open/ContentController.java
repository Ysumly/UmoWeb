package com.ysumly.umowebbackend.controller.open;

import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.vo.ContentDetailVO;
import com.ysumly.umowebbackend.model.vo.ContentListVO;
import com.ysumly.umowebbackend.service.open.ContentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/contents")
    public ResponseEntity<PageResult<ContentListVO>> list(@Valid ContentQuery query) {
        return ResponseEntity.ok(contentService.listPublished(query));
    }

    @GetMapping("/contents/{slug}")
    public ResponseEntity<ContentDetailVO> detail(@PathVariable String slug) {
        return ResponseEntity.ok(contentService.getBySlug(slug));
    }

    @GetMapping("/contents/search")
    public ResponseEntity<PageResult<ContentListVO>> search(@Valid ContentQuery query) {
        return ResponseEntity.ok(contentService.search(query));
    }
}
