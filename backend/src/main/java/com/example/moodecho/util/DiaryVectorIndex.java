package com.example.moodecho.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/** One atomic index snapshot per diary, stored in the existing LONGTEXT column. */
public record DiaryVectorIndex(int version, int dimensions, List<Double> whole,
                               List<Chunk> chunks) {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Chunk(String text, List<Double> vector) {}

    public String toJson() {
        try { return JSON.writeValueAsString(this); }
        catch (Exception e) { throw new IllegalArgumentException("索引序列化失败", e); }
    }

    public static DiaryVectorIndex fromJson(String value) {
        try {
            DiaryVectorIndex index = JSON.readValue(value, DiaryVectorIndex.class);
            if (index.version != DiaryChunkUtils.VERSION || index.chunks == null || index.chunks.isEmpty())
                throw new IllegalArgumentException("索引版本不兼容，请重建索引");
            VectorUtils.validate(index.whole);
            if (index.dimensions != index.whole.size()) throw new IllegalArgumentException("索引维度不匹配");
            for (Chunk chunk : index.chunks) {
                if (chunk.text == null || chunk.text.isBlank()) throw new IllegalArgumentException("索引片段为空");
                VectorUtils.validate(chunk.vector);
                if (chunk.vector.size() != index.dimensions) throw new IllegalArgumentException("索引维度不匹配");
            }
            return index;
        } catch (Exception e) { throw new IllegalArgumentException("索引无效，请重建索引", e); }
    }
}
