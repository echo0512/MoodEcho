package com.example.moodecho.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 语义搜索和相关往日记录的列表项。
 */
@Data
public class SemanticDiaryResultVO {

    private Long id;

    private String title;

    private String summary;

    private LocalDate recordDate;

    /** 余弦相似度；仅文字命中而无有效语义匹配时为null。 */
    private Double score;
}
