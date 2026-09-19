package com.ysumly.umowebbackend.service.impl.ai;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.service.ai.AiResultValidator;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiResultValidatorImpl implements AiResultValidator {

    private static final Pattern INLINE_LINK =
            Pattern.compile("\\[[^\\]]*\\]\\([^)]+\\)");
    private static final Pattern FENCE_DELIMITER =
            Pattern.compile("(?m)^ {0,3}(`{3,}|~{3,})");
    private static final Pattern FENCE_LINE =
            Pattern.compile("^ {0,3}(`{3,}|~{3,}).*$");

    @Override
    public void validate(AiValidationProfile profile,
                         String source,
                         String result,
                         int maxOutputChars) {
        validateBasicResult(result, maxOutputChars);

        switch (profile) {
            case EXACT_CONTENT -> validateExactContent(source, result);
            case TRANSLATION -> validateTranslation(source, result);
            case LIGHT_EXPANSION -> validateLightExpansion(source, result);
            case NONE -> {
                // Basic validation is sufficient.
            }
        }
    }

    private void validateBasicResult(String result, int maxOutputChars) {
        if (result == null || result.isBlank()) {
            throw invalid("AI 结果不能为空");
        }
        if (codePointLength(result) > maxOutputChars) {
            throw invalid("AI 结果超过最大长度");
        }
    }

    private void validateExactContent(String source, String result) {
        if (!canonicalNonHeadingText(source).equals(canonicalNonHeadingText(result))) {
            throw invalid("结构整理改变了正文");
        }
    }

    private void validateTranslation(String source, String result) {
        int sourceBlocks = countCodeBlocks(source);
        int resultBlocks = countCodeBlocks(result);
        if (sourceBlocks != resultBlocks) {
            throw invalid("翻译结果代码块数量与原文不一致");
        }
        int sourceLinks = countLinks(source);
        int resultLinks = countLinks(result);
        if (sourceLinks != resultLinks) {
            throw invalid("翻译结果链接数量与原文不一致");
        }
    }

    private void validateLightExpansion(String source, String result) {
        int sourceBlocks = countCodeBlocks(source);
        int resultBlocks = countCodeBlocks(result);
        if (resultBlocks > sourceBlocks) {
            throw invalid("轻度小说化不得增加代码块数量");
        }
        int sourceLinks = countLinks(source);
        int resultLinks = countLinks(result);
        if (resultLinks > sourceLinks) {
            throw invalid("轻度小说化不得增加链接数量");
        }

        int sourceLength = Math.max(1, codePointLength(source));
        int resultLength = codePointLength(result);
        if (resultLength < sourceLength || resultLength * 2 > sourceLength * 3) {
            throw invalid("轻度小说化结果长度必须为原文的 1-1.5 倍");
        }
    }

    private String canonicalNonHeadingText(String markdown) {
        if (markdown == null) {
            return "";
        }
        StringBuilder canonical = new StringBuilder();
        boolean inFence = false;
        char fenceCharacter = 0;
        for (String line : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            Matcher fenceMatcher = FENCE_LINE.matcher(line);
            if (fenceMatcher.matches()) {
                char currentFenceCharacter = firstFenceCharacter(line);
                if (!inFence) {
                    inFence = true;
                    fenceCharacter = currentFenceCharacter;
                } else if (fenceCharacter == currentFenceCharacter) {
                    inFence = false;
                    fenceCharacter = 0;
                }
                appendWithoutWhitespace(canonical, line);
                continue;
            }
            if (!inFence && isAtxHeading(line)) {
                continue;
            }
            appendWithoutWhitespace(canonical, line);
        }
        return canonical.toString();
    }

    private void appendWithoutWhitespace(StringBuilder target, String line) {
        line.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .forEach(target::appendCodePoint);
    }

    private char firstFenceCharacter(String line) {
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (value == '`' || value == '~') {
                return value;
            }
        }
        return 0;
    }

    private boolean isAtxHeading(String line) {
        int index = 0;
        while (index < line.length() && index < 3 && line.charAt(index) == ' ') {
            index++;
        }
        int hashes = 0;
        while (index < line.length() && line.charAt(index) == '#' && hashes < 6) {
            hashes++;
            index++;
        }
        return hashes > 0
                && (index == line.length() || Character.isWhitespace(line.charAt(index)));
    }

    private int countCodeBlocks(String markdown) {
        Matcher matcher = FENCE_DELIMITER.matcher(markdown == null ? "" : markdown);
        boolean open = false;
        int blocks = 0;
        while (matcher.find()) {
            if (!open) {
                blocks++;
            }
            open = !open;
        }
        return blocks;
    }

    private int countLinks(String markdown) {
        Matcher matcher = INLINE_LINK.matcher(markdown == null ? "" : markdown);
        int links = 0;
        while (matcher.find()) {
            links++;
        }
        return links;
    }

    private int codePointLength(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private BusinessException invalid(String message) {
        return new BusinessException(502, message);
    }
}
