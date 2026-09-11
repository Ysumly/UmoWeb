package com.ysumly.umowebbackend.service.admin;

import com.ysumly.umowebbackend.model.vo.ImageVO;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService {
    ImageVO upload(MultipartFile file);
}
