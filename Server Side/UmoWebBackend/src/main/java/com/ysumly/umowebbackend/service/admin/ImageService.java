package com.ysumly.umowebbackend.service.admin;

import com.ysumly.umowebbackend.model.dto.ImageQuery;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.vo.ImageManageVO;
import com.ysumly.umowebbackend.model.vo.ImageVO;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService {
    ImageVO upload(MultipartFile file);
    PageResult<ImageManageVO> list(ImageQuery query);
    void delete(Long id);
}
