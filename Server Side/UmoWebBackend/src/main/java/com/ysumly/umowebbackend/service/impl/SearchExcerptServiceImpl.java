package com.ysumly.umowebbackend.service.impl;

import com.ysumly.umowebbackend.service.SearchExcerptService;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SearchExcerptServiceImpl implements SearchExcerptService {

    private static final int MAX_EXCERPT_LENGTH = 160;
    private static final int CONTEXT_BEFORE = 56;
    private static final int CONTEXT_AFTER = 80;

    @Override
    public String build(String bodyText, String query, String summary) {
        String body = plainText(bodyText);
        String needle = query == null ? "" : query.trim();
        int matchIndex = indexOfIgnoreCase(body, needle);
        if (matchIndex >= 0) {
            return aroundMatch(body, matchIndex, needle.length());
        }

        String plainSummary = plainText(summary);
        if (!plainSummary.isBlank()) {
            return truncate(plainSummary);
        }
        return truncate(body);
    }

    private String aroundMatch(String text, int matchIndex, int matchLength) {
        int start = Math.max(0, matchIndex - CONTEXT_BEFORE);
        int end = Math.min(text.length(), matchIndex + matchLength + CONTEXT_AFTER);
        String excerpt = text.substring(start, end).trim();
        if (start > 0) {
            excerpt = "…" + excerpt;
        }
        if (end < text.length()) {
            excerpt += "…";
        }
        return excerpt;
    }

    private String plainText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        text = text.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        text = text.replaceAll("(?m)^\\s{0,3}(?:>|[-+*]|\\d+\\.)\\s+", "");
        text = text.replace("**", "").replace("__", "").replace("`", "");
        text = text.replaceAll("<[^>]+>", " ");
        return text.replaceAll("\\s+", " ").trim();
    }

    private int indexOfIgnoreCase(String text, String needle) {
        if (needle.isEmpty()) {
            return -1;
        }
        return text.toLowerCase(Locale.ROOT)
                .indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private String truncate(String text) {
        if (text.length() <= MAX_EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_EXCERPT_LENGTH).trim() + "…";
    }
}
