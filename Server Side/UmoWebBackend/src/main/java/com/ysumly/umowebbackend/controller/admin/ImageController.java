package com.ysumly.umowebbackend.controller.admin;

import com.ysumly.umowebbackend.model.dto.ImageQuery;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.vo.ImageManageVO;
import com.ysumly.umowebbackend.model.vo.ImageVO;
import com.ysumly.umowebbackend.service.admin.ImageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/images/upload")
    public ResponseEntity<ImageVO> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(imageService.upload(file));
    }

    @GetMapping("/images")
    public ResponseEntity<PageResult<ImageManageVO>> list(@Valid ImageQuery query) {
        return ResponseEntity.ok(imageService.list(query));
    }

    @DeleteMapping("/images/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        imageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
