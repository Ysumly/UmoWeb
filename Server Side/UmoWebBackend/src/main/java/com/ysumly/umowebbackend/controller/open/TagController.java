package com.ysumly.umowebbackend.controller.open;

import com.ysumly.umowebbackend.model.vo.TagVO;
import com.ysumly.umowebbackend.service.open.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TagVO>> list() {
        return ResponseEntity.ok(tagService.getAll());
    }
}
