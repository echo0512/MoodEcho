package com.example.moodecho.service;

import java.util.List;

/**
 * 文本向量生成服务接口。
 */
public interface EmbeddingService {

    List<Double> embedText(String text);

    default List<List<Double>> embedTexts(List<String> texts) {
        return texts.stream().map(this::embedText).toList();
    }

    /** Whether background jobs can currently call the configured provider. */
    boolean isConfigured();

    String getModelName();
}
