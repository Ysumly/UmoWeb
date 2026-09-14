package com.ysumly.umowebbackend.service.impl;

import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.mapper.ContentSearchMapper;
import com.ysumly.umowebbackend.model.entity.Content;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentSearchIndexServiceTest {

    private final ContentMapper contentMapper = mock(ContentMapper.class);
    private final ContentSearchMapper searchMapper = mock(ContentSearchMapper.class);
    private final FileUtil fileUtil = mock(FileUtil.class);
    private final ContentSearchIndexServiceImpl service =
            new ContentSearchIndexServiceImpl(contentMapper, searchMapper, fileUtil);

    @Test
    void publishedContentUpsertsItsMarkdownBody() {
        Content content = content(7L, "PUBLISHED", "contents/NOTE/search.md");

        service.sync(content, "正文中的唯一检索词");

        verify(searchMapper).upsert(7L, "正文中的唯一检索词");
    }

    @Test
    void draftContentRemovesItsExistingIndex() {
        Content content = content(7L, "DRAFT", "contents/NOTE/search.md");

        service.sync(content, "草稿正文");

        verify(searchMapper).deleteByContentId(7L);
    }

    @Test
    void rebuildIndexesEveryPublishedBodyAndRemovesStaleRows() throws IOException {
        Content first = content(7L, "PUBLISHED", "contents/NOTE/one.md");
        Content second = content(8L, "PUBLISHED", "contents/NOTE/two.md");
        when(contentMapper.findAllPublishedForIndex()).thenReturn(List.of(first, second));
        when(fileUtil.readMarkdown("contents/NOTE/one.md")).thenReturn("第一篇正文");
        when(fileUtil.readMarkdown("contents/NOTE/two.md")).thenReturn("第二篇正文");

        int indexed = service.rebuildPublished();

        assertThat(indexed).isEqualTo(2);
        verify(searchMapper).upsert(7L, "第一篇正文");
        verify(searchMapper).upsert(8L, "第二篇正文");
        verify(searchMapper).deleteNotPublished();
    }

    @Test
    void rebuildKeepsMetadataSearchAvailableWhenMarkdownFileIsMissing() throws IOException {
        Content content = content(7L, "PUBLISHED", "contents/NOTE/missing.md");
        when(contentMapper.findAllPublishedForIndex()).thenReturn(List.of(content));
        when(fileUtil.readMarkdown("contents/NOTE/missing.md"))
                .thenThrow(new IOException("missing"));

        int indexed = service.rebuildPublished();

        assertThat(indexed).isEqualTo(1);
        verify(searchMapper).upsert(7L, "");
        verify(searchMapper).deleteNotPublished();
    }

    private Content content(Long id, String status, String bodyPath) {
        Content content = new Content();
        content.setId(id);
        content.setStatus(status);
        content.setBodyPath(bodyPath);
        return content;
    }
}
