package com.example.moodecho.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 查询词覆盖率只用于重排已召回的日记，不单独用部分词重合召回。 */
public final class DiaryLexicalScorer {
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+");
    private final Map<String, Double> weights = new HashMap<>();
    private final double total;

    public DiaryLexicalScorer(String query, List<String> documents) {
        Set<String> terms = terms(query);
        Map<String, Integer> frequencies = new HashMap<>();
        for (String document : documents) {
            for (String term : terms(document)) {
                if (terms.contains(term)) frequencies.merge(term, 1, Integer::sum);
            }
        }
        for (String term : terms) {
            weights.put(term, Math.log(1 + (documents.size() + 1.0) / (frequencies.getOrDefault(term, 0) + 1.0)));
        }
        total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double coverage(String document) {
        if (total == 0) return 0;
        Set<String> present = terms(document);
        return weights.entrySet().stream().filter(e -> present.contains(e.getKey()))
                .mapToDouble(Map.Entry::getValue).sum() / total;
    }

    // 汉字按相邻双字切分，拉丁文字/数字按词切分；不跨标点或空格构造词项。
    private static Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        var matcher = WORDS.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            int[] points = matcher.group().codePoints().toArray();
            for (int start = 0; start < points.length;) {
                boolean han = Character.UnicodeScript.of(points[start]) == Character.UnicodeScript.HAN;
                int end = start + 1;
                while (end < points.length && (Character.UnicodeScript.of(points[end]) == Character.UnicodeScript.HAN) == han) end++;
                if (han && end - start > 1) {
                    for (int i = start; i + 1 < end; i++) result.add(new String(points, i, 2));
                } else {
                    result.add(new String(points, start, end - start));
                }
                start = end;
            }
        }
        return result;
    }
}
