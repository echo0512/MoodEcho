package com.example.moodecho.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Preserve paragraph boundaries before cleaning rich text. Never merge unrelated paragraphs. */
public final class DiaryChunkUtils {
    public static final int VERSION = 1;
    private static final int MAX_LENGTH = 350;
    private static final int OVERLAP = 50;
    private static final Pattern BOUNDARY = Pattern.compile("(?is)<br\\s*/?>|</(?:p|div|li|blockquote|h[1-6])\\s*>");

    private DiaryChunkUtils() {}

    public static List<String> chunks(String content) {
        String structured = BOUNDARY.matcher(content == null ? "" : content).replaceAll("\n");
        List<String> result = new ArrayList<>();
        for (String paragraph : structured.split("\\R+")) {
            String text = TextCleanUtils.cleanDiaryContent(paragraph);
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + MAX_LENGTH, text.length());
                if (end < text.length()) {
                    // Prefer a sentence boundary in the latter half of this window.
                    for (int i = end - 1; i >= start + MAX_LENGTH / 2; i--) {
                        if ("。！？!?；;".indexOf(text.charAt(i)) >= 0) {
                            end = i + 1;
                            break;
                        }
                    }
                    if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
                }
                String chunk = text.substring(start, end).trim();
                if (!chunk.isBlank()) result.add(chunk);
                if (end == text.length()) break;
                start = end - OVERLAP;
                if (Character.isLowSurrogate(text.charAt(start))) start++;
            }
        }
        return List.copyOf(result);
    }

    public static String fingerprint(String title, List<String> chunks) {
        // Length prefixes distinguish paragraph boundaries and avoid ambiguous concatenation.
        StringBuilder value = new StringBuilder("chunks-v" + VERSION + ":");
        String cleanTitle = TextCleanUtils.cleanDiaryContent(title);
        value.append(cleanTitle.length()).append(':').append(cleanTitle);
        for (String chunk : chunks) value.append('|').append(chunk.length()).append(':').append(chunk);
        return HashUtils.sha256(value.toString());
    }
}
