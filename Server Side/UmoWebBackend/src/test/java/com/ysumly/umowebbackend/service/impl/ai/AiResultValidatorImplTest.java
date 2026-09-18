package com.ysumly.umowebbackend.service.impl.ai;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.service.ai.AiResultValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResultValidatorImplTest {

    private final AiResultValidator validator = new AiResultValidatorImpl();

    @Test
    void exactContentAllowsHeadingAndWhitespaceChanges() {
        assertThatCode(() -> validator.validate(
                AiValidationProfile.EXACT_CONTENT,
                "# 原标题\n\n第一段  文字\n\n- 列表",
                "## 新标题\n\n第一段文字\n- 列表",
                60_000))
                .doesNotThrowAnyException();
    }

    @Test
    void exactContentRejectsBodyChanges() {
        assertThatThrownBy(() -> validator.validate(
                AiValidationProfile.EXACT_CONTENT,
                "# 标题\n正文",
                "## 标题\n改写正文",
                60_000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结构整理改变了正文");
    }

    @Test
    void translationRequiresSameCodeBlockAndLinkCounts() {
        assertThatCode(() -> validator.validate(
                AiValidationProfile.TRANSLATION,
                "Hello [link](https://example.com)\n```java\ncode\n```",
                "你好 [链接](https://example.com)\n```java\ncode\n```",
                60_000))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validate(
                AiValidationProfile.TRANSLATION,
                "Hello [link](https://example.com)",
                "你好",
                60_000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("链接数量");
    }

    @Test
    void lightExpansionAllowsOnlyOneToFifteenTimesLength() {
        assertThatCode(() -> validator.validate(
                AiValidationProfile.LIGHT_EXPANSION,
                "abcd",
                "abcdef",
                60_000))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validate(
                AiValidationProfile.LIGHT_EXPANSION,
                "abcd",
                "abcdefg",
                60_000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1-1.5 倍");

        assertThatThrownBy(() -> validator.validate(
                AiValidationProfile.LIGHT_EXPANSION,
                "abcd",
                "abc",
                60_000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1-1.5 倍");
    }

    @Test
    void lightExpansionRejectsAdditionalLinksOrCodeBlocks() {
        assertThatThrownBy(() -> validator.validate(
                AiValidationProfile.LIGHT_EXPANSION,
                "abcd",
                "abcdef [new](https://example.com)",
                60_000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("链接数量");

        assertThatThrownBy(() -> validator.validate(
                AiValidationProfile.LIGHT_EXPANSION,
                "abcd",
                "abcdef\n```\ncode\n```",
                60_000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("代码块数量");
    }

    @Test
    void noneOnlyRequiresNonBlankOutputWithinLimit() {
        assertThatCode(() -> validator.validate(
                AiValidationProfile.NONE,
                "source",
                "result",
                6))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validate(
                AiValidationProfile.NONE,
                "source",
                " ",
                60_000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结果不能为空");

        assertThatThrownBy(() -> validator.validate(
                AiValidationProfile.NONE,
                "source",
                "1234567",
                6))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过最大长度");
    }
}
