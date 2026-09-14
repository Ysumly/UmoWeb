package com.ysumly.umowebbackend.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchExcerptServiceTest {

    private final SearchExcerptServiceImpl service = new SearchExcerptServiceImpl();

    @Test
    void extractsPlainTextAroundBodyMatch() {
        String excerpt = service.build(
                "# 标题\n\n这是 **重点** 上下文，正文中的唯一检索词出现在这里，后面还有更多内容。",
                "唯一检索词",
                "摘要");

        assertThat(excerpt)
                .isEqualTo("标题 这是 重点 上下文，正文中的唯一检索词出现在这里，后面还有更多内容。");
    }

    @Test
    void fallsBackToSummaryWhenBodyDoesNotMatch() {
        String excerpt = service.build("正文没有目标词", "检索词", "摘要中包含检索词");

        assertThat(excerpt).isEqualTo("摘要中包含检索词");
    }

    @Test
    void fallsBackToBodyBeginningWhenBodyAndSummaryDoNotMatch() {
        String excerpt = service.build("## 正文开头\n\n这里是可理解的正文内容。", "未命中", "");

        assertThat(excerpt).isEqualTo("正文开头 这里是可理解的正文内容。");
    }
}
