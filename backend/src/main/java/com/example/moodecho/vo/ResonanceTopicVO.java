package com.example.moodecho.vo;

import lombok.Data;

/**
 * 共鸣话题响应视图对象。
 */
@Data
public class ResonanceTopicVO {

    private Long id;

    private String name;

    private String description;

    private Integer sortOrder;
}
