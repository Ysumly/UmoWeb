package com.ysumly.umowebbackend.controller.admin;

import com.ysumly.umowebbackend.model.dto.ImageQuery;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.vo.ImageManageVO;
import com.ysumly.umowebbackend.model.vo.ImageIntegrityReportVO;
import com.ysumly.umowebbackend.model.vo.ImageVO;
import com.ysumly.umowebbackend.service.admin.ImageIntegrityService;
import com.ysumly.umowebbackend.service.admin.ImageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
public class ImageController {

    private final ImageService imageService;
    private final ImageIntegrityService imageIntegrityService;

    public ImageController(ImageService imageService,
                           ImageIntegrityService imageIntegrityService) {
        this.imageService = imageService;
        this.imageIntegrityService = imageIntegrityService;
    }

    @PostMapping("/images/upload")
    public ResponseEntity<ImageVO> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(imageService.upload(file));
    }

    @GetMapping("/images")
    public ResponseEntity<PageResult<ImageManageVO>> list(@Valid ImageQuery query) {
        return ResponseEntity.ok(imageService.list(query));
    }

    @GetMapping("/images/integrity")
    public ResponseEntity<ImageIntegrityReportVO> integrity() {
        return ResponseEntity.ok(imageIntegrityService.inspect());
    }

    @DeleteMapping("/images/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        imageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
