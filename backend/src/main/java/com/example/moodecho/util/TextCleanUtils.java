package com.example.moodecho.util;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * 富文本日记正文清洗工具。
 */
public final class TextCleanUtils {

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\(\\s*moodecho-image:\\d+\\s*\\)");
    private static final Pattern MOODECHO_IMAGE_PATTERN = Pattern.compile("moodecho-image:\\d+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private TextCleanUtils() {
    }

    public static String cleanDiaryContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String cleaned = content;
        cleaned = IMG_TAG_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = MARKDOWN_IMAGE_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = MOODECHO_IMAGE_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = HTML_TAG_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = HtmlUtils.htmlUnescape(cleaned);
        cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ");

        return cleaned.trim();
    }

    public static String buildSummary(String content) {
        return buildSummary(content, 100);
    }

    public static String buildSummary(String content, int maxLength) {
        String text = cleanDiaryContent(content);
        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}
