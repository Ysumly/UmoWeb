package com.ysumly.umowebbackend.controller.admin;

import com.ysumly.umowebbackend.model.dto.CategorySaveRequest;
import com.ysumly.umowebbackend.model.vo.CategoryTreeVO;
import com.ysumly.umowebbackend.model.vo.CategoryVO;
import com.ysumly.umowebbackend.service.admin.CategoryManageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class CategoryManageController {

    private final CategoryManageService categoryManageService;

    public CategoryManageController(CategoryManageService categoryManageService) {
        this.categoryManageService = categoryManageService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryTreeVO>> tree(@RequestParam(required = false) String type) {
        return ResponseEntity.ok(categoryManageService.getTree(type));
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<CategoryVO> detail(@PathVariable Long id) {
        return ResponseEntity.ok(categoryManageService.getById(id));
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryVO> create(@Valid @RequestBody CategorySaveRequest request) {
        return ResponseEntity.ok(categoryManageService.create(request));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryVO> update(@PathVariable Long id,
                                             @Valid @RequestBody CategorySaveRequest request) {
        return ResponseEntity.ok(categoryManageService.update(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryManageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
