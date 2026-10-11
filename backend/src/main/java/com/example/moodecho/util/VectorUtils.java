package com.example.moodecho.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 向量序列化和相似度计算工具。
 */
public final class VectorUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Double>> DOUBLE_LIST_TYPE = new TypeReference<>() {
    };

    private VectorUtils() {
    }

    public static String toJson(List<Double> vector) {
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("向量序列化失败", e);
        }
    }

    public static List<Double> fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, DOUBLE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("向量解析失败", e);
        }
    }

    public static double cosineSimilarity(List<Double> left, List<Double> right) {
        validate(left);
        validate(right);
        if (left.size() != right.size()) throw new IllegalArgumentException("向量维度不匹配");
        int size = left.size();
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;

        for (int i = 0; i < size; i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);

            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }

        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }

        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public static void validate(List<Double> vector) {
        if (vector == null || vector.isEmpty()) throw new IllegalArgumentException("向量为空");
        double norm = 0;
        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("向量包含无效数值");
            norm += value * value;
        }
        if (!Double.isFinite(norm) || norm == 0) throw new IllegalArgumentException("向量范数无效");
    }

    /** 长文由单位片段向量的均值表示，避免把无界整篇文本提交给模型。 */
    public static List<Double> normalizedMean(List<List<Double>> vectors) {
        if (vectors == null || vectors.isEmpty()) throw new IllegalArgumentException("向量为空");
        validate(vectors.get(0));
        double[] sum = new double[vectors.get(0).size()];
        for (List<Double> vector : vectors) {
            validate(vector);
            if (vector.size() != sum.length) throw new IllegalArgumentException("向量维度不匹配");
            double norm = Math.sqrt(vector.stream().mapToDouble(v -> v * v).sum());
            for (int i = 0; i < sum.length; i++) sum[i] += vector.get(i) / norm;
        }
        double norm = Math.sqrt(java.util.Arrays.stream(sum).map(v -> v * v).sum());
        // 极端相反向量抵消时，使用首个有效片段而不是发布零向量。
        if (norm < 1e-12) return List.copyOf(vectors.get(0));
        return java.util.Arrays.stream(sum).map(v -> v / norm).boxed().toList();
    }

    public static double roundScore(double score) {
        return BigDecimal.valueOf(score)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
