package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ImageCleanupTaskMapper;
import com.ysumly.umowebbackend.model.entity.ImageCleanupTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ImageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final ImageCleanupTaskMapper cleanupTaskMapper;
    private final FileUtil fileUtil;

    public ImageCleanupService(ImageCleanupTaskMapper cleanupTaskMapper, FileUtil fileUtil) {
        this.cleanupTaskMapper = cleanupTaskMapper;
        this.fileUtil = fileUtil;
    }

    public void retryPending() {
        for (ImageCleanupTask task : cleanupTaskMapper.findAll()) {
            try {
                fileUtil.deleteStoredFile(task.getPath());
                cleanupTaskMapper.delete(task.getId());
            } catch (Exception e) {
                String error = errorMessage(e);
                cleanupTaskMapper.recordFailure(task.getId(), error);
                log.error("Failed to delete queued image file: {}", task.getPath(), e);
            }
        }
    }

    private String errorMessage(Exception e) {
        String message = e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}
