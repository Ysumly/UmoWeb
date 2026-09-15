package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.model.entity.Image;
import com.ysumly.umowebbackend.model.entity.ImageCleanupTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION", matches = "true")
@Transactional
class ImageManagementIntegrationTest {

    @Autowired
    private ImageMapper imageMapper;

    @Autowired
    private ContentMapper contentMapper;

    @Autowired
    private ImageCleanupTaskMapper cleanupTaskMapper;

    @Test
    void imageListIsStableAndCleanupQueuePersistsFailures() {
        Image image = image();
        imageMapper.insert(image);

        assertThat(imageMapper.findAll())
                .extracting(Image::getId)
                .contains(image.getId());

        imageMapper.delete(image.getId());
        assertThat(imageMapper.findById(image.getId())).isNull();

        ImageCleanupTask task = new ImageCleanupTask();
        task.setImageId(image.getId());
        task.setPath(image.getPath());
        cleanupTaskMapper.insert(task);
        cleanupTaskMapper.recordFailure(task.getId(), "temporary failure");

        ImageCleanupTask queued = cleanupTaskMapper.findAll().get(0);
        assertThat(queued.getAttempts()).isEqualTo(1);
        assertThat(queued.getLastError()).isEqualTo("temporary failure");

        cleanupTaskMapper.delete(task.getId());
        assertThat(cleanupTaskMapper.findAll()).isEmpty();
    }

    @Test
    void referenceScanQueryReturnsContentSourceFields() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Content content = new Content();
        content.setTitle("Image reference source");
        content.setSlug("image-reference-" + suffix);
        content.setBodyPath("contents/NOTE/image-reference-" + suffix + ".md");
        content.setType("NOTE");
        content.setStatus("DRAFT");
        contentMapper.insert(content);

        assertThat(contentMapper.findAllForReferenceScan())
                .filteredOn(item -> item.getId().equals(content.getId()))
                .extracting(Content::getTitle, Content::getBodyPath)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "Image reference source",
                        content.getBodyPath()));
    }

    private Image image() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Image image = new Image();
        image.setOriginalName("integration.png");
        image.setStoredName(suffix + ".png");
        image.setPath("images/2026/09/" + suffix + ".png");
        image.setSize(128L);
        image.setContentType("image/png");
        return image;
    }
}
